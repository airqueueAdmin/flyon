package com.airplanehome.flight.service;

import com.airplanehome.flight.client.FlightProvider;
import com.airplanehome.flight.model.FlightPrice;
import com.airplanehome.flight.repository.FlightPriceRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import javax.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class FlightPrefetchService {
    private static final Logger log = LoggerFactory.getLogger(FlightPrefetchService.class);
    private static final String INVALID_DUFFEL_AIRLINE = "Duffel Airways";
    private static final List<String> TIME_BUCKETS = Arrays.asList("morning", "afternoon", "evening");
    private static final Duration ON_DEMAND_RATE_WINDOW = Duration.ofMinutes(1);

    private final FlightProvider primaryFlightProvider;
    private final FlightProvider fallbackFlightProvider;
    private final SharedFlightCacheService sharedFlightCacheService;
    private final FlightPriceRepository flightPriceRepository;
    private final List<RouteDefinition> popularRoutes;
    private final int coverageDaysAhead;
    private final List<Integer> actualFetchOffsets;
    private final int routesPerCycle;
    private final int alwaysHotRouteCount;
    private final int maxOnDemandCallsPerMinute;
    private final Map<String, Integer> routeRequestCounts = new ConcurrentHashMap<String, Integer>();
    private final Map<String, ReentrantLock> onDemandLocks = new ConcurrentHashMap<String, ReentrantLock>();
    private final Deque<Instant> onDemandApiCallTimes = new ArrayDeque<Instant>();
    private int rotationCursor;

    public FlightPrefetchService(@Qualifier("rapidApiProvider") FlightProvider primaryFlightProvider,
                                 @Qualifier("duffelApiProvider") FlightProvider fallbackFlightProvider,
                                 SharedFlightCacheService sharedFlightCacheService,
                                 FlightPriceRepository flightPriceRepository,
                                 @Value("${app.prefetch.routes:ICN|NRT,ICN|FUK,ICN|BKK,ICN|SFO}") String routesConfig,
                                 @Value("${app.prefetch.coverage-days-ahead:7}") int coverageDaysAhead,
                                 @Value("${app.prefetch.actual-fetch-offsets:0,3}") String actualFetchOffsets,
                                 @Value("${app.prefetch.routes-per-cycle:5}") int routesPerCycle,
                                 @Value("${app.prefetch.always-hot-routes:2}") int alwaysHotRouteCount,
                                 @Value("${app.on-demand.max-calls-per-minute:6}") int maxOnDemandCallsPerMinute) {
        this.primaryFlightProvider = primaryFlightProvider;
        this.fallbackFlightProvider = fallbackFlightProvider;
        this.sharedFlightCacheService = sharedFlightCacheService;
        this.flightPriceRepository = flightPriceRepository;
        this.popularRoutes = parseRoutes(routesConfig);
        this.coverageDaysAhead = coverageDaysAhead;
        this.actualFetchOffsets = parseOffsets(actualFetchOffsets);
        this.routesPerCycle = routesPerCycle;
        this.alwaysHotRouteCount = alwaysHotRouteCount;
        this.maxOnDemandCallsPerMinute = maxOnDemandCallsPerMinute;
    }

    @PostConstruct
    public void warmCacheFromDb() {
        Map<String, List<FlightPrice>> grouped = new HashMap<String, List<FlightPrice>>();
        for (FlightPrice flightPrice : flightPriceRepository.findByPrefetchedAtAfter(LocalDateTime.now().minusMinutes(30))) {
            if (!isValidAirline(flightPrice)) {
                continue;
            }
            String key = sharedFlightCacheService.buildKey(
                    flightPrice.getOrigin(),
                    flightPrice.getDestination(),
                    flightPrice.getDepartureDate());
            List<FlightPrice> flights = grouped.get(key);
            if (flights == null) {
                flights = new ArrayList<FlightPrice>();
                grouped.put(key, flights);
            }
            flights.add(flightPrice);
        }

        for (List<FlightPrice> flights : grouped.values()) {
            FlightPrice first = flights.get(0);
            sharedFlightCacheService.warm(
                    first.getOrigin(),
                    first.getDestination(),
                    first.getDepartureDate(),
                    flights,
                    first.getPrefetchedAt());
        }
        log.info("CACHE_WARMUP_FROM_DB entries={}", grouped.size());
    }

    public List<FlightPrice> getCachedFlights(String origin, String destination, LocalDate departureDate) {
        recordSearchRequest(origin, destination);
        return getCachedFlightsInternal(origin, destination, departureDate);
    }

    @Transactional
    public List<FlightPrice> getCachedOrFetchFlights(String origin, String destination, LocalDate departureDate) {
        recordSearchRequest(origin, destination);
        List<FlightPrice> cachedFlights = getCachedFlightsInternal(origin, destination, departureDate);
        if (!cachedFlights.isEmpty()) {
            return cachedFlights;
        }

        String key = sharedFlightCacheService.buildKey(origin, destination, departureDate);
        log.info("CACHE_MISS_ON_DEMAND key={}", key);

        ReentrantLock lock = getOnDemandLock(key);
        lock.lock();
        try {
            cachedFlights = getCachedFlightsInternal(origin, destination, departureDate);
            if (!cachedFlights.isEmpty()) {
                return cachedFlights;
            }

            if (!tryAcquireOnDemandApiCallSlot()) {
                log.info("ON_DEMAND_API_CALL key={} skipped=true reason=rate-limit", key);
                return Collections.emptyList();
            }

            log.info("ON_DEMAND_API_CALL key={}", key);
            List<FlightPrice> fetchedFlights = searchWithFallback(origin, destination, departureDate);
            if (fetchedFlights.isEmpty()) {
                return Collections.emptyList();
            }

            persistExpandedCoverage(origin, destination, departureDate, fetchedFlights);
            return sharedFlightCacheService.getFresh(origin, destination, departureDate);
        } finally {
            lock.unlock();
            if (!lock.hasQueuedThreads()) {
                onDemandLocks.remove(key, lock);
            }
        }
    }

    private List<FlightPrice> getCachedFlightsInternal(String origin, String destination, LocalDate departureDate) {
        List<FlightPrice> fresh = sharedFlightCacheService.getFresh(origin, destination, departureDate);
        if (!fresh.isEmpty()) {
            log.info("CACHE_HIT key={}", sharedFlightCacheService.buildKey(origin, destination, departureDate));
            logApproximateUsageIfNeeded(fresh);
            return fresh;
        }

        if (sharedFlightCacheService.hasEntry(origin, destination, departureDate)) {
            log.info("CACHE_HIT key={} stale=true", sharedFlightCacheService.buildKey(origin, destination, departureDate));
            List<FlightPrice> stale = sharedFlightCacheService.getStale(origin, destination, departureDate);
            logApproximateUsageIfNeeded(stale);
            return stale;
        }

        log.info("CACHE_MISS key={}", sharedFlightCacheService.buildKey(origin, destination, departureDate));
        List<FlightPrice> exactFromDb = findExactFromDb(origin, destination, departureDate);
        if (!exactFromDb.isEmpty()) {
            log.info("DB_FALLBACK_HIT key={} source=exact",
                    sharedFlightCacheService.buildKey(origin, destination, departureDate));
            sharedFlightCacheService.put(origin, destination, departureDate, exactFromDb);
            log.info("CACHE_POPULATED_FROM_DB key={}",
                    sharedFlightCacheService.buildKey(origin, destination, departureDate));
            logApproximateUsageIfNeeded(exactFromDb);
            return exactFromDb;
        }

        List<FlightPrice> nearest = findNearestAvailable(origin, destination, departureDate);
        if (!nearest.isEmpty()) {
            log.info("APPROX_DATA_USED key={}", sharedFlightCacheService.buildKey(origin, destination, departureDate));
            return nearest;
        }
        log.info("DB_FALLBACK_MISS key={}", sharedFlightCacheService.buildKey(origin, destination, departureDate));
        return Collections.emptyList();
    }

    public boolean isSupported(String origin, String destination, LocalDate departureDate) {
        if (departureDate == null) {
            return false;
        }

        LocalDate today = LocalDate.now();
        if (departureDate.isBefore(today) || departureDate.isAfter(today.plusDays(coverageDaysAhead - 1))) {
            return false;
        }

        for (RouteDefinition route : popularRoutes) {
            if (route.matches(origin, destination)) {
                return true;
            }
        }
        return false;
    }

    @Transactional
    public void prefetchPopularRoutes() {
        LocalDate today = LocalDate.now();
        List<RouteDefinition> selectedRoutes = selectRoutesForCycle();
        log.info("PREFETCH_ROTATION routes={}", selectedRoutes);
        for (RouteDefinition route : selectedRoutes) {
            Map<LocalDate, List<FlightPrice>> coverageByDate = new HashMap<LocalDate, List<FlightPrice>>();
            Map<LocalDate, LocalDate> exactFetchedDates = new HashMap<LocalDate, LocalDate>();
            for (Integer offset : actualFetchOffsets) {
                LocalDate departureDate = today.plusDays(offset.intValue());
                if (exactFetchedDates.containsKey(departureDate)) {
                    continue;
                }
                List<FlightPrice> flights = searchWithFallback(route.getOrigin(), route.getDestination(), departureDate);
                if (flights.isEmpty()) {
                    log.info("API_CALL_SKIPPED key={} reason=no-data-or-provider-skipped",
                            sharedFlightCacheService.buildKey(route.getOrigin(), route.getDestination(), departureDate));
                    continue;
                }
                exactFetchedDates.put(departureDate, departureDate);
                mergeCoverage(coverageByDate,
                        buildExpandedCoverage(route.getOrigin(), route.getDestination(), departureDate, flights));
            }
            persistCoverage(route.getOrigin(), route.getDestination(), coverageByDate);
        }
    }

    @Transactional
    public void refresh(String origin, String destination, LocalDate departureDate) {
        List<FlightPrice> flights = searchWithFallback(origin, destination, departureDate);
        if (flights.isEmpty()) {
            log.info("API_CALL_SKIPPED key={} reason=no-data-or-provider-skipped",
                    sharedFlightCacheService.buildKey(origin, destination, departureDate));
            return;
        }

        persistExpandedCoverage(origin, destination, departureDate, flights);
    }

    public void evictCache(String origin, String destination, LocalDate departureDate) {
        sharedFlightCacheService.evict(origin, destination, departureDate);
        log.info("CACHE_EVICT key={}", sharedFlightCacheService.buildKey(origin, destination, departureDate));
    }

    public void clearCache() {
        sharedFlightCacheService.clear();
        log.info("CACHE_CLEAR scope=all");
    }

    public void recordSearchRequest(String origin, String destination) {
        String routeKey = normalizeRouteKey(origin, destination);
        Integer current = routeRequestCounts.get(routeKey);
        routeRequestCounts.put(routeKey, current == null ? 1 : current + 1);
    }

    private List<RouteDefinition> parseRoutes(String routesConfig) {
        List<RouteDefinition> routes = new ArrayList<RouteDefinition>();
        for (String token : routesConfig.split(",")) {
            String value = token.trim();
            if (!StringUtils.hasText(value) || !value.contains("|")) {
                continue;
            }

            String[] parts = value.split("\\|");
            if (parts.length != 2) {
                continue;
            }
            routes.add(new RouteDefinition(parts[0].trim().toUpperCase(), parts[1].trim().toUpperCase()));
        }
        return routes;
    }

    private List<Integer> parseOffsets(String offsetsConfig) {
        List<Integer> offsets = new ArrayList<Integer>();
        for (String token : offsetsConfig.split(",")) {
            String value = token.trim();
            if (!StringUtils.hasText(value)) {
                continue;
            }
            offsets.add(Integer.valueOf(value));
        }
        if (offsets.isEmpty()) {
            offsets.add(Integer.valueOf(0));
            offsets.add(Integer.valueOf(3));
        }
        return offsets;
    }

    private List<RouteDefinition> selectRoutesForCycle() {
        List<RouteDefinition> selected = new ArrayList<RouteDefinition>();
        List<RouteDefinition> hotRoutes = new ArrayList<RouteDefinition>(popularRoutes);
        Collections.sort(hotRoutes, new Comparator<RouteDefinition>() {
            @Override
            public int compare(RouteDefinition left, RouteDefinition right) {
                return Integer.valueOf(getRequestCount(right)).compareTo(Integer.valueOf(getRequestCount(left)));
            }
        });

        for (RouteDefinition route : hotRoutes) {
            if (selected.size() >= alwaysHotRouteCount || selected.size() >= routesPerCycle) {
                break;
            }
            selected.add(route);
        }

        while (selected.size() < routesPerCycle && !popularRoutes.isEmpty()) {
            RouteDefinition route = popularRoutes.get(rotationCursor % popularRoutes.size());
            rotationCursor++;
            if (!selected.contains(route)) {
                selected.add(route);
            }
        }
        return selected;
    }

    private int getRequestCount(RouteDefinition route) {
        Integer count = routeRequestCounts.get(normalizeRouteKey(route.getOrigin(), route.getDestination()));
        return count == null ? 0 : count.intValue();
    }

    private String normalizeRouteKey(String origin, String destination) {
        return origin.trim().toUpperCase() + "|" + destination.trim().toUpperCase();
    }

    private ReentrantLock getOnDemandLock(String key) {
        ReentrantLock existingLock = onDemandLocks.get(key);
        if (existingLock != null) {
            return existingLock;
        }
        ReentrantLock newLock = new ReentrantLock();
        ReentrantLock priorLock = onDemandLocks.putIfAbsent(key, newLock);
        return priorLock == null ? newLock : priorLock;
    }

    private synchronized boolean tryAcquireOnDemandApiCallSlot() {
        Instant now = Instant.now();
        while (!onDemandApiCallTimes.isEmpty()
                && onDemandApiCallTimes.peekFirst().isBefore(now.minus(ON_DEMAND_RATE_WINDOW))) {
            onDemandApiCallTimes.removeFirst();
        }
        if (onDemandApiCallTimes.size() >= maxOnDemandCallsPerMinute) {
            return false;
        }
        onDemandApiCallTimes.addLast(now);
        return true;
    }

    private List<FlightPrice> searchWithFallback(String origin, String destination, LocalDate departureDate) {
        String key = sharedFlightCacheService.buildKey(origin, destination, departureDate);
        log.info("API_CALL provider=rapidapi key={}", key);
        List<FlightPrice> primaryFlights = sanitizeFlights(
                primaryFlightProvider.search(origin, destination, departureDate.toString()),
                "rapidapi",
                key);
        if (!primaryFlights.isEmpty()) {
            log.info("PRIMARY_API_SUCCESS key={} size={}", key, primaryFlights.size());
            return primaryFlights;
        }

        log.warn("PRIMARY_API_FAILED key={}", key);
        log.info("FALLBACK_TO_DUFFEL key={}", key);
        log.info("API_CALL provider=duffel key={}", key);
        List<FlightPrice> fallbackFlights = sanitizeFlights(
                fallbackFlightProvider.search(origin, destination, departureDate.toString()),
                "duffel",
                key);
        if (!fallbackFlights.isEmpty()) {
            log.info("FALLBACK_USED key={} provider=duffel size={}", key, fallbackFlights.size());
            log.info("FALLBACK_API_USED key={} size={}", key, fallbackFlights.size());
            return fallbackFlights;
        }

        log.warn("FALLBACK_API_FAILED key={}", key);
        return Collections.emptyList();
    }

    private void persistExpandedCoverage(String origin, String destination, LocalDate fetchedDate, List<FlightPrice> flights) {
        persistCoverage(origin, destination, buildExpandedCoverage(origin, destination, fetchedDate, flights));
    }

    private Map<LocalDate, List<FlightPrice>> buildExpandedCoverage(String origin,
                                                                    String destination,
                                                                    LocalDate fetchedDate,
                                                                    List<FlightPrice> flights) {
        LocalDate today = LocalDate.now();
        Map<LocalDate, List<FlightPrice>> coverageByDate = new HashMap<LocalDate, List<FlightPrice>>();
        for (FlightPrice flight : flights) {
            for (FlightPrice bucketed : createBucketVariants(flight, fetchedDate, false, fetchedDate)) {
                addCoverage(coverageByDate, fetchedDate, bucketed);
            }

            for (int delta = -2; delta <= 2; delta++) {
                if (delta == 0) {
                    continue;
                }
                LocalDate approximatedDate = fetchedDate.plusDays(delta);
                if (approximatedDate.isBefore(today) || approximatedDate.isAfter(today.plusDays(coverageDaysAhead - 1))) {
                    continue;
                }
                for (FlightPrice approximate : createBucketVariants(flight, approximatedDate, true, fetchedDate)) {
                    addCoverage(coverageByDate, approximatedDate, approximate);
                }
            }
        }
        return coverageByDate;
    }

    private void mergeCoverage(Map<LocalDate, List<FlightPrice>> target, Map<LocalDate, List<FlightPrice>> source) {
        for (Map.Entry<LocalDate, List<FlightPrice>> entry : source.entrySet()) {
            List<FlightPrice> flights = target.get(entry.getKey());
            if (flights == null) {
                flights = new ArrayList<FlightPrice>();
                target.put(entry.getKey(), flights);
            }
            flights.addAll(entry.getValue());
        }
    }

    private void persistCoverage(String origin, String destination, Map<LocalDate, List<FlightPrice>> coverageByDate) {
        if (coverageByDate.isEmpty()) {
            return;
        }

        List<String> summaries = new ArrayList<String>();
        List<LocalDate> dates = new ArrayList<LocalDate>(coverageByDate.keySet());
        Collections.sort(dates);
        for (LocalDate date : dates) {
            List<FlightPrice> flights = sanitizeFlights(coverageByDate.get(date), "persistence", normalizeRouteKey(origin, destination) + "|" + date);
            flightPriceRepository.deleteByOriginAndDestinationAndDepartureDate(origin, destination, date);
            if (flights.isEmpty()) {
                sharedFlightCacheService.evict(origin, destination, date);
                continue;
            }
            flightPriceRepository.saveAll(flights);
            sharedFlightCacheService.put(origin, destination, date, flights);
            summaries.add(date + "=" + flights.size());
        }
        log.info("PREFETCH_UPDATE route={} summary={}",
                normalizeRouteKey(origin, destination),
                summaries);
    }

    private void addCoverage(Map<LocalDate, List<FlightPrice>> coverageByDate, LocalDate date, FlightPrice flightPrice) {
        List<FlightPrice> flights = coverageByDate.get(date);
        if (flights == null) {
            flights = new ArrayList<FlightPrice>();
            coverageByDate.put(date, flights);
        }
        flights.add(flightPrice);
    }

    private List<FlightPrice> createBucketVariants(FlightPrice source, LocalDate departureDate, boolean approximate, LocalDate sourceDepartureDate) {
        List<FlightPrice> variants = new ArrayList<FlightPrice>();
        for (String timeBucket : TIME_BUCKETS) {
            FlightPrice variant = new FlightPrice();
            variant.setOrigin(source.getOrigin());
            variant.setDestination(source.getDestination());
            variant.setDepartureDate(departureDate);
            variant.setReturnDate(source.getReturnDate());
            variant.setPrice(source.getPrice());
            variant.setCurrency(source.getCurrency());
            variant.setProvider(source.getProvider());
            variant.setAirline(source.getAirline());
            variant.setPassengers(source.getPassengers());
            variant.setApproximate(Boolean.valueOf(approximate));
            variant.setSourceDepartureDate(sourceDepartureDate);
            variant.setTimeBucket(timeBucket);
            variant.setPrefetchedAt(LocalDateTime.now());
            variant.setDepartureTime(buildBucketTime(departureDate, timeBucket));
            variant.setArrivalTime(buildBucketTime(departureDate, timeBucket).plusHours(2));
            variants.add(variant);
        }
        return variants;
    }

    private LocalDateTime buildBucketTime(LocalDate departureDate, String timeBucket) {
        if ("morning".equals(timeBucket)) {
            return departureDate.atTime(9, 0);
        }
        if ("afternoon".equals(timeBucket)) {
            return departureDate.atTime(14, 0);
        }
        return departureDate.atTime(19, 0);
    }

    private List<FlightPrice> findNearestAvailable(String origin, String destination, LocalDate departureDate) {
        for (int distance = 1; distance <= 2; distance++) {
            List<FlightPrice> previous = sharedFlightCacheService.getStale(origin, destination, departureDate.minusDays(distance));
            if (!previous.isEmpty()) {
                return previous;
            }
            List<FlightPrice> next = sharedFlightCacheService.getStale(origin, destination, departureDate.plusDays(distance));
            if (!next.isEmpty()) {
                return next;
            }
        }

        List<FlightPrice> nearestFromDb = findNearestFromDb(origin, destination, departureDate);
        if (!nearestFromDb.isEmpty()) {
            FlightPrice first = nearestFromDb.get(0);
            log.info("DB_FALLBACK_HIT key={} source=nearest sourceDate={}",
                    sharedFlightCacheService.buildKey(origin, destination, departureDate),
                    first.getDepartureDate());
            sharedFlightCacheService.put(origin, destination, first.getDepartureDate(), nearestFromDb);
            log.info("CACHE_POPULATED_FROM_DB key={}",
                    sharedFlightCacheService.buildKey(origin, destination, first.getDepartureDate()));
            return nearestFromDb;
        }
        return Collections.emptyList();
    }

    private List<FlightPrice> findExactFromDb(String origin, String destination, LocalDate departureDate) {
        return sanitizeFlights(
                flightPriceRepository.findByOriginAndDestinationAndDepartureDateOrderByPriceAsc(
                origin,
                destination,
                departureDate),
                "db-exact",
                sharedFlightCacheService.buildKey(origin, destination, departureDate));
    }

    private List<FlightPrice> findNearestFromDb(String origin, String destination, LocalDate departureDate) {
        List<FlightPrice> candidates = sanitizeFlights(
                flightPriceRepository.findByOriginAndDestinationAndDepartureDateBetweenOrderByDepartureDateAscPriceAsc(
                origin,
                destination,
                departureDate.minusDays(2),
                departureDate.plusDays(2)),
                "db-nearest",
                sharedFlightCacheService.buildKey(origin, destination, departureDate));
        if (candidates.isEmpty()) {
            return Collections.emptyList();
        }

        LocalDate nearestDate = null;
        long nearestDistance = Long.MAX_VALUE;
        for (FlightPrice candidate : candidates) {
            long distance = Math.abs(java.time.temporal.ChronoUnit.DAYS.between(departureDate, candidate.getDepartureDate()));
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearestDate = candidate.getDepartureDate();
            }
        }

        if (nearestDate == null) {
            return Collections.emptyList();
        }

        List<FlightPrice> nearestFlights = new ArrayList<FlightPrice>();
        for (FlightPrice candidate : candidates) {
            if (nearestDate.equals(candidate.getDepartureDate())) {
                nearestFlights.add(candidate);
            }
        }
        return nearestFlights;
    }

    private List<FlightPrice> sanitizeFlights(List<FlightPrice> flights, String source, String key) {
        if (flights == null || flights.isEmpty()) {
            return Collections.emptyList();
        }

        List<FlightPrice> sanitized = new ArrayList<FlightPrice>();
        int dropped = 0;
        for (FlightPrice flight : flights) {
            if (!isValidAirline(flight)) {
                dropped++;
                continue;
            }
            sanitized.add(flight);
        }
        if (dropped > 0) {
            log.warn("INVALID_AIRLINE_FILTERED source={} key={} airline={} dropped={}",
                    source,
                    key,
                    INVALID_DUFFEL_AIRLINE,
                    dropped);
        }
        return sanitized;
    }

    private boolean isValidAirline(FlightPrice flight) {
        return flight != null
                && StringUtils.hasText(flight.getAirline())
                && !INVALID_DUFFEL_AIRLINE.equalsIgnoreCase(flight.getAirline().trim());
    }

    private void logApproximateUsageIfNeeded(List<FlightPrice> flights) {
        if (!flights.isEmpty() && Boolean.TRUE.equals(flights.get(0).getApproximate())) {
            log.info("APPROX_DATA_USED key={} sourceDate={}",
                    normalizeRouteKey(flights.get(0).getOrigin(), flights.get(0).getDestination()) + "|" + flights.get(0).getDepartureDate(),
                    flights.get(0).getSourceDepartureDate());
        }
    }

    private static final class RouteDefinition {
        private final String origin;
        private final String destination;

        private RouteDefinition(String origin, String destination) {
            this.origin = origin;
            this.destination = destination;
        }

        private String getOrigin() {
            return origin;
        }

        private String getDestination() {
            return destination;
        }

        private boolean matches(String requestOrigin, String requestDestination) {
            return origin.equalsIgnoreCase(requestOrigin) && destination.equalsIgnoreCase(requestDestination);
        }

        @Override
        public String toString() {
            return origin + "->" + destination;
        }
    }
}
