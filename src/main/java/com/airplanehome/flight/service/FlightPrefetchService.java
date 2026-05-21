package com.airplanehome.flight.service;

import com.airplanehome.flight.client.FlightProvider;
import com.airplanehome.flight.model.FlightPrice;
import com.airplanehome.flight.model.TripType;
import com.airplanehome.flight.repository.FlightPriceRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import javax.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
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
    private final List<Integer> roundTripDurations;
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
                                 @Value("${app.prefetch.round-trip-durations:2,3,5,7}") String roundTripDurations,
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
        this.roundTripDurations = parseOffsets(roundTripDurations);
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
                    resolveTripType(flightPrice.getTripType(), flightPrice.getReturnDate()),
                    flightPrice.getOrigin(),
                    flightPrice.getDestination(),
                    flightPrice.getDepartureDate(),
                    flightPrice.getReturnDate());
            List<FlightPrice> flights = grouped.get(key);
            if (flights == null) {
                flights = new ArrayList<FlightPrice>();
                grouped.put(key, flights);
            }
            flights.add(flightPrice);
        }

        for (List<FlightPrice> flights : grouped.values()) {
            FlightPrice first = flights.get(0);
            TripType tripType = resolveTripType(first.getTripType(), first.getReturnDate());
            sharedFlightCacheService.warm(
                    tripType,
                    first.getOrigin(),
                    first.getDestination(),
                    first.getDepartureDate(),
                    first.getReturnDate(),
                    flights,
                    first.getPrefetchedAt());
        }
        log.info("CACHE_WARMUP_FROM_DB entries={}", grouped.size());
    }

    public List<FlightPrice> getCachedFlights(String origin, String destination, LocalDate departureDate) {
        return getCachedFlights(TripType.ONE_WAY, origin, destination, departureDate, null);
    }

    public List<FlightPrice> getCachedFlights(TripType tripType,
                                              String origin,
                                              String destination,
                                              LocalDate departureDate,
                                              LocalDate returnDate) {
        recordSearchRequest(origin, destination);
        return getCachedFlightsInternal(resolveTripType(tripType, returnDate), origin, destination, departureDate, returnDate);
    }

    public List<FlightPrice> getCachedOrFetchFlights(String origin, String destination, LocalDate departureDate) {
        return getCachedOrFetchFlights(TripType.ONE_WAY, origin, destination, departureDate, null, null);
    }

    public List<FlightPrice> getCachedOrFetchFlights(TripType tripType,
                                                     String origin,
                                                     String destination,
                                                     LocalDate departureDate,
                                                     LocalDate returnDate,
                                                     Integer adults) {
        TripType resolvedTripType = resolveTripType(tripType, returnDate);
        recordSearchRequest(origin, destination);
        List<FlightPrice> cachedFlights = getCachedFlightsInternal(resolvedTripType, origin, destination, departureDate, returnDate);
        if (!cachedFlights.isEmpty()) {
            return cachedFlights;
        }

        String key = sharedFlightCacheService.buildKey(resolvedTripType, origin, destination, departureDate, returnDate);
        log.info("CACHE_MISS_ON_DEMAND key={}", key);

        ReentrantLock lock = getOnDemandLock(key);
        lock.lock();
        try {
            cachedFlights = getCachedFlightsInternal(resolvedTripType, origin, destination, departureDate, returnDate);
            if (!cachedFlights.isEmpty()) {
                return cachedFlights;
            }

            if (!tryAcquireOnDemandApiCallSlot()) {
                log.info("ON_DEMAND_API_CALL key={} skipped=true reason=rate-limit", key);
                return Collections.emptyList();
            }

            log.info("ON_DEMAND_API_CALL key={}", key);
            List<FlightPrice> fetchedFlights = searchWithFallback(
                    resolvedTripType,
                    origin,
                    destination,
                    departureDate,
                    returnDate,
                    adults);
            if (fetchedFlights.isEmpty()) {
                return Collections.emptyList();
            }

            persistExpandedCoverage(resolvedTripType, origin, destination, departureDate, returnDate, fetchedFlights);
            return sharedFlightCacheService.getFresh(resolvedTripType, origin, destination, departureDate, returnDate);
        } finally {
            lock.unlock();
            if (!lock.hasQueuedThreads()) {
                onDemandLocks.remove(key, lock);
            }
        }
    }

    private List<FlightPrice> getCachedFlightsInternal(TripType tripType,
                                                       String origin,
                                                       String destination,
                                                       LocalDate departureDate,
                                                       LocalDate returnDate) {
        List<FlightPrice> fresh = sharedFlightCacheService.getFresh(tripType, origin, destination, departureDate, returnDate);
        if (!fresh.isEmpty()) {
            log.info("CACHE_HIT key={}", sharedFlightCacheService.buildKey(tripType, origin, destination, departureDate, returnDate));
            logApproximateUsageIfNeeded(fresh);
            return fresh;
        }

        if (sharedFlightCacheService.hasEntry(tripType, origin, destination, departureDate, returnDate)) {
            log.info("CACHE_HIT key={} stale=true",
                    sharedFlightCacheService.buildKey(tripType, origin, destination, departureDate, returnDate));
            List<FlightPrice> stale = sharedFlightCacheService.getStale(tripType, origin, destination, departureDate, returnDate);
            logApproximateUsageIfNeeded(stale);
            return stale;
        }

        log.info("CACHE_MISS key={}", sharedFlightCacheService.buildKey(tripType, origin, destination, departureDate, returnDate));
        List<FlightPrice> exactFromDb = findExactFromDb(tripType, origin, destination, departureDate, returnDate);
        if (!exactFromDb.isEmpty()) {
            log.info("DB_FALLBACK_HIT key={} source=exact",
                    sharedFlightCacheService.buildKey(tripType, origin, destination, departureDate, returnDate));
            sharedFlightCacheService.put(tripType, origin, destination, departureDate, returnDate, exactFromDb);
            log.info("CACHE_POPULATED_FROM_DB key={}",
                    sharedFlightCacheService.buildKey(tripType, origin, destination, departureDate, returnDate));
            logApproximateUsageIfNeeded(exactFromDb);
            return exactFromDb;
        }

        List<FlightPrice> nearest = findNearestAvailable(tripType, origin, destination, departureDate, returnDate);
        if (!nearest.isEmpty()) {
            log.info("APPROX_DATA_USED key={}", sharedFlightCacheService.buildKey(tripType, origin, destination, departureDate, returnDate));
            return nearest;
        }
        log.info("DB_FALLBACK_MISS key={}", sharedFlightCacheService.buildKey(tripType, origin, destination, departureDate, returnDate));
        return Collections.emptyList();
    }

    public boolean isSupported(String origin, String destination, LocalDate departureDate) {
        return isSupported(TripType.ONE_WAY, origin, destination, departureDate, null);
    }

    public boolean isSupported(TripType tripType,
                               String origin,
                               String destination,
                               LocalDate departureDate,
                               LocalDate returnDate) {
        if (departureDate == null) {
            return false;
        }

        LocalDate today = LocalDate.now();
        LocalDate maxCoveredDate = today.plusDays(coverageDaysAhead - 1L);
        if (departureDate.isBefore(today) || departureDate.isAfter(maxCoveredDate)) {
            return false;
        }

        TripType resolvedTripType = resolveTripType(tripType, returnDate);
        if (resolvedTripType.isRoundTrip()) {
            if (returnDate == null || returnDate.isBefore(departureDate) || returnDate.isAfter(maxCoveredDate)) {
                return false;
            }
        }

        for (RouteDefinition route : popularRoutes) {
            if (route.matches(origin, destination)) {
                return true;
            }
        }
        return false;
    }

    public void prefetchPopularRoutes() {
        LocalDate today = LocalDate.now();
        LocalDate maxDate = today.plusDays(coverageDaysAhead - 1L);
        List<RouteDefinition> selectedRoutes = selectRoutesForCycle();
        log.info("PREFETCH_ROTATION routes={}", selectedRoutes);
        for (RouteDefinition route : selectedRoutes) {
            Map<SearchKey, List<FlightPrice>> coverageByKey = new HashMap<SearchKey, List<FlightPrice>>();

            // Step 1: 출발(편도) 날짜별 1회씩 조회
            Map<LocalDate, List<FlightPrice>> outboundByDate = new LinkedHashMap<LocalDate, List<FlightPrice>>();
            for (Integer offset : actualFetchOffsets) {
                LocalDate departureDate = today.plusDays(offset.intValue());
                List<FlightPrice> flights = searchWithFallback(
                        TripType.ONE_WAY, route.getOrigin(), route.getDestination(), departureDate, null, null);
                if (flights.isEmpty()) {
                    log.info("API_CALL_SKIPPED key={} reason=no-data-or-provider-skipped",
                            sharedFlightCacheService.buildKey(TripType.ONE_WAY, route.getOrigin(), route.getDestination(), departureDate, null));
                    continue;
                }
                outboundByDate.put(departureDate, flights);
                mergeCoverage(coverageByKey, buildExpandedCoverage(
                        TripType.ONE_WAY, route.getOrigin(), route.getDestination(), departureDate, null, flights));
            }

            // Step 2: 왕복에 필요한 귀국 날짜 수집 (중복 제거)
            Set<LocalDate> inboundDates = new LinkedHashSet<LocalDate>();
            for (LocalDate departureDate : outboundByDate.keySet()) {
                for (Integer duration : roundTripDurations) {
                    LocalDate returnDate = departureDate.plusDays(duration.intValue());
                    if (!returnDate.isAfter(maxDate)) {
                        inboundDates.add(returnDate);
                    }
                }
            }

            // Step 3: 귀국(역방향 편도) 날짜별 1회씩 조회
            Map<LocalDate, List<FlightPrice>> inboundByDate = new LinkedHashMap<LocalDate, List<FlightPrice>>();
            for (LocalDate returnDate : inboundDates) {
                List<FlightPrice> flights = searchWithFallback(
                        TripType.ONE_WAY, route.getDestination(), route.getOrigin(), returnDate, null, null);
                if (flights.isEmpty()) {
                    continue;
                }
                inboundByDate.put(returnDate, flights);
            }

            // Step 4: 왕복 조합 구성 (조회한 편도 결과 재사용)
            for (LocalDate departureDate : outboundByDate.keySet()) {
                List<FlightPrice> outbound = outboundByDate.get(departureDate);
                for (Integer duration : roundTripDurations) {
                    LocalDate returnDate = departureDate.plusDays(duration.intValue());
                    if (returnDate.isAfter(maxDate)) {
                        continue;
                    }
                    List<FlightPrice> inbound = inboundByDate.get(returnDate);
                    if (inbound == null || inbound.isEmpty()) {
                        continue;
                    }
                    List<FlightPrice> roundTrips = buildRoundTripFlights(
                            route.getOrigin(), route.getDestination(), departureDate, returnDate, outbound, inbound);
                    if (roundTrips.isEmpty()) {
                        continue;
                    }
                    mergeCoverage(coverageByKey, buildExpandedCoverage(
                            TripType.ROUND_TRIP, route.getOrigin(), route.getDestination(), departureDate, returnDate, roundTrips));
                }
            }
            persistCoverage(coverageByKey);
        }
    }

    public void refresh(String origin, String destination, LocalDate departureDate) {
        refresh(TripType.ONE_WAY, origin, destination, departureDate, null);
    }

    public void refresh(TripType tripType,
                        String origin,
                        String destination,
                        LocalDate departureDate,
                        LocalDate returnDate) {
        TripType resolvedTripType = resolveTripType(tripType, returnDate);
        List<FlightPrice> flights = searchWithFallback(resolvedTripType, origin, destination, departureDate, returnDate, null);
        if (flights.isEmpty()) {
            log.info("API_CALL_SKIPPED key={} reason=no-data-or-provider-skipped",
                    sharedFlightCacheService.buildKey(resolvedTripType, origin, destination, departureDate, returnDate));
            return;
        }

        persistExpandedCoverage(resolvedTripType, origin, destination, departureDate, returnDate, flights);
    }

    public void evictCache(String origin, String destination, LocalDate departureDate) {
        evictCache(TripType.ONE_WAY, origin, destination, departureDate, null);
    }

    public void evictCache(TripType tripType,
                           String origin,
                           String destination,
                           LocalDate departureDate,
                           LocalDate returnDate) {
        TripType resolvedTripType = resolveTripType(tripType, returnDate);
        sharedFlightCacheService.evict(resolvedTripType, origin, destination, departureDate, returnDate);
        log.info("CACHE_EVICT key={}",
                sharedFlightCacheService.buildKey(resolvedTripType, origin, destination, departureDate, returnDate));
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

    private List<FlightPrice> buildRoundTripFlights(String origin,
                                                    String destination,
                                                    LocalDate departureDate,
                                                    LocalDate returnDate,
                                                    List<FlightPrice> outboundFlights,
                                                    List<FlightPrice> inboundFlights) {
        int outLimit = Math.min(3, outboundFlights.size());
        int inLimit = Math.min(3, inboundFlights.size());
        List<FlightPrice> combinations = new ArrayList<FlightPrice>();
        for (int i = 0; i < outLimit; i++) {
            for (int j = 0; j < inLimit; j++) {
                combinations.add(buildRoundTripFlight(origin, destination, departureDate, returnDate,
                        outboundFlights.get(i), inboundFlights.get(j)));
            }
        }
        combinations.sort(Comparator.comparing(FlightPrice::getPrice));
        return combinations;
    }

    private FlightPrice buildRoundTripFlight(String origin,
                                             String destination,
                                             LocalDate departureDate,
                                             LocalDate returnDate,
                                             FlightPrice outbound,
                                             FlightPrice inbound) {
        BigDecimal outPrice = outbound.getPrice() != null ? outbound.getPrice() : BigDecimal.ZERO;
        BigDecimal inPrice = inbound.getPrice() != null ? inbound.getPrice() : BigDecimal.ZERO;
        FlightPrice combined = new FlightPrice();
        combined.setTripType(TripType.ROUND_TRIP);
        combined.setOrigin(origin);
        combined.setDestination(destination);
        combined.setDepartureDate(departureDate);
        combined.setReturnDate(returnDate);
        combined.setPassengers(Integer.valueOf(1));
        combined.setCurrency(outbound.getCurrency());
        combined.setProvider(outbound.getProvider() != null ? outbound.getProvider() : inbound.getProvider());
        combined.setPrice(outPrice.add(inPrice));
        combined.setTotalPrice(outPrice.add(inPrice));
        combined.setAirline(outbound.getAirline());
        combined.setOutboundAirline(outbound.getOutboundAirline() != null ? outbound.getOutboundAirline() : outbound.getAirline());
        combined.setInboundAirline(inbound.getOutboundAirline() != null ? inbound.getOutboundAirline() : inbound.getAirline());
        combined.setDepartureTime(outbound.getDepartureTime());
        combined.setArrivalTime(outbound.getArrivalTime());
        combined.setReturnDepartureTime(inbound.getDepartureTime());
        combined.setReturnArrivalTime(inbound.getArrivalTime());
        return combined;
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

    private List<FlightPrice> searchWithFallback(TripType tripType,
                                                 String origin,
                                                 String destination,
                                                 LocalDate departureDate,
                                                 LocalDate returnDate,
                                                 Integer adults) {
        String key = sharedFlightCacheService.buildKey(tripType, origin, destination, departureDate, returnDate);
        log.info("API_CALL provider=rapidapi key={}", key);
        List<FlightPrice> primaryFlights = sanitizeFlights(
                primaryFlightProvider.search(tripType, origin, destination, departureDate, returnDate, adults),
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
                fallbackFlightProvider.search(tripType, origin, destination, departureDate, returnDate, adults),
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

    private void persistExpandedCoverage(TripType tripType,
                                        String origin,
                                        String destination,
                                        LocalDate fetchedDate,
                                        LocalDate returnDate,
                                        List<FlightPrice> flights) {
        persistCoverage(buildExpandedCoverage(tripType, origin, destination, fetchedDate, returnDate, flights));
    }

    private Map<SearchKey, List<FlightPrice>> buildExpandedCoverage(TripType tripType,
                                                                    String origin,
                                                                    String destination,
                                                                    LocalDate fetchedDate,
                                                                    LocalDate returnDate,
                                                                    List<FlightPrice> flights) {
        LocalDate today = LocalDate.now();
        Map<SearchKey, List<FlightPrice>> coverageByKey = new HashMap<SearchKey, List<FlightPrice>>();
        for (FlightPrice flight : flights) {
            SearchKey exactKey = new SearchKey(tripType, origin, destination, fetchedDate, returnDate);
            for (FlightPrice bucketed : createBucketVariants(flight, tripType, fetchedDate, returnDate, false, fetchedDate)) {
                addCoverage(coverageByKey, exactKey, bucketed);
            }

            for (int delta = -2; delta <= 2; delta++) {
                if (delta == 0) {
                    continue;
                }
                LocalDate approximatedDepartureDate = fetchedDate.plusDays(delta);
                LocalDate approximatedReturnDate = returnDate == null ? null : returnDate.plusDays(delta);
                if (approximatedDepartureDate.isBefore(today)
                        || approximatedDepartureDate.isAfter(today.plusDays(coverageDaysAhead - 1L))) {
                    continue;
                }
                if (tripType.isRoundTrip() && (approximatedReturnDate == null
                        || approximatedReturnDate.isAfter(today.plusDays(coverageDaysAhead - 1L)))) {
                    continue;
                }
                SearchKey approximateKey = new SearchKey(tripType, origin, destination, approximatedDepartureDate, approximatedReturnDate);
                for (FlightPrice approximate : createBucketVariants(
                        flight,
                        tripType,
                        approximatedDepartureDate,
                        approximatedReturnDate,
                        true,
                        fetchedDate)) {
                    addCoverage(coverageByKey, approximateKey, approximate);
                }
            }
        }
        return coverageByKey;
    }

    private void mergeCoverage(Map<SearchKey, List<FlightPrice>> target, Map<SearchKey, List<FlightPrice>> source) {
        for (Map.Entry<SearchKey, List<FlightPrice>> entry : source.entrySet()) {
            List<FlightPrice> flights = target.get(entry.getKey());
            if (flights == null) {
                flights = new ArrayList<FlightPrice>();
                target.put(entry.getKey(), flights);
            }
            flights.addAll(entry.getValue());
        }
    }

    private void persistCoverage(Map<SearchKey, List<FlightPrice>> coverageByKey) {
        if (coverageByKey.isEmpty()) {
            return;
        }

        List<SearchKey> keys = new ArrayList<SearchKey>(coverageByKey.keySet());
        Collections.sort(keys, new Comparator<SearchKey>() {
            @Override
            public int compare(SearchKey left, SearchKey right) {
                int tripTypeOrder = left.tripType.compareTo(right.tripType);
                if (tripTypeOrder != 0) {
                    return tripTypeOrder;
                }
                int routeOrder = left.origin.compareTo(right.origin);
                if (routeOrder != 0) {
                    return routeOrder;
                }
                routeOrder = left.destination.compareTo(right.destination);
                if (routeOrder != 0) {
                    return routeOrder;
                }
                int departureOrder = left.departureDate.compareTo(right.departureDate);
                if (departureOrder != 0) {
                    return departureOrder;
                }
                if (left.returnDate == null && right.returnDate == null) {
                    return 0;
                }
                if (left.returnDate == null) {
                    return -1;
                }
                if (right.returnDate == null) {
                    return 1;
                }
                return left.returnDate.compareTo(right.returnDate);
            }
        });

        Map<String, List<String>> summariesByRoute = new HashMap<String, List<String>>();
        for (SearchKey key : keys) {
            List<FlightPrice> flights = sanitizeFlights(
                    coverageByKey.get(key),
                    "persistence",
                    sharedFlightCacheService.buildKey(key.tripType, key.origin, key.destination, key.departureDate, key.returnDate));
            deleteCoverage(key);
            if (flights.isEmpty()) {
                sharedFlightCacheService.evict(key.tripType, key.origin, key.destination, key.departureDate, key.returnDate);
                continue;
            }
            flightPriceRepository.saveAll(flights);
            sharedFlightCacheService.put(key.tripType, key.origin, key.destination, key.departureDate, key.returnDate, flights);

            String summaryKey = key.tripType + "|" + normalizeRouteKey(key.origin, key.destination);
            List<String> routeSummary = summariesByRoute.get(summaryKey);
            if (routeSummary == null) {
                routeSummary = new ArrayList<String>();
                summariesByRoute.put(summaryKey, routeSummary);
            }
            routeSummary.add(key.returnDate == null
                    ? key.departureDate + "=" + flights.size()
                    : key.departureDate + "->" + key.returnDate + "=" + flights.size());
        }

        for (Map.Entry<String, List<String>> entry : summariesByRoute.entrySet()) {
            log.info("PREFETCH_UPDATE route={} summary={}", entry.getKey(), entry.getValue());
        }
    }

    private void addCoverage(Map<SearchKey, List<FlightPrice>> coverageByKey, SearchKey key, FlightPrice flightPrice) {
        List<FlightPrice> flights = coverageByKey.get(key);
        if (flights == null) {
            flights = new ArrayList<FlightPrice>();
            coverageByKey.put(key, flights);
        }
        flights.add(flightPrice);
    }

    private List<FlightPrice> createBucketVariants(FlightPrice source,
                                                   TripType tripType,
                                                   LocalDate departureDate,
                                                   LocalDate returnDate,
                                                   boolean approximate,
                                                   LocalDate sourceDepartureDate) {
        List<FlightPrice> variants = new ArrayList<FlightPrice>();
        for (String timeBucket : TIME_BUCKETS) {
            FlightPrice variant = new FlightPrice();
            variant.setTripType(tripType);
            variant.setOrigin(source.getOrigin());
            variant.setDestination(source.getDestination());
            variant.setDepartureDate(departureDate);
            variant.setReturnDate(returnDate);
            variant.setTotalPrice(source.getTotalPrice());
            variant.setPrice(source.getPrice());
            variant.setCurrency(source.getCurrency());
            variant.setProvider(source.getProvider());
            variant.setBookingUrl(source.getBookingUrl());
            variant.setAirline(source.getAirline());
            variant.setOutboundAirline(source.getOutboundAirline());
            variant.setInboundAirline(source.getInboundAirline());
            variant.setPassengers(source.getPassengers());
            variant.setApproximate(Boolean.valueOf(approximate));
            variant.setSourceDepartureDate(sourceDepartureDate);
            variant.setTimeBucket(timeBucket);
            variant.setPrefetchedAt(LocalDateTime.now());
            variant.setDepartureTime(buildBucketTime(departureDate, timeBucket));
            variant.setArrivalTime(buildBucketTime(departureDate, timeBucket).plusHours(2));
            if (tripType.isRoundTrip() && returnDate != null) {
                variant.setReturnDepartureTime(buildBucketTime(returnDate, timeBucket));
                variant.setReturnArrivalTime(buildBucketTime(returnDate, timeBucket).plusHours(2));
            }
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

    private List<FlightPrice> findNearestAvailable(TripType tripType,
                                                   String origin,
                                                   String destination,
                                                   LocalDate departureDate,
                                                   LocalDate returnDate) {
        for (int distance = 1; distance <= 2; distance++) {
            LocalDate previousDeparture = departureDate.minusDays(distance);
            LocalDate nextDeparture = departureDate.plusDays(distance);
            LocalDate previousReturn = returnDate == null ? null : returnDate.minusDays(distance);
            LocalDate nextReturn = returnDate == null ? null : returnDate.plusDays(distance);

            List<FlightPrice> previous = sharedFlightCacheService.getStale(tripType, origin, destination, previousDeparture, previousReturn);
            if (!previous.isEmpty()) {
                return previous;
            }
            List<FlightPrice> next = sharedFlightCacheService.getStale(tripType, origin, destination, nextDeparture, nextReturn);
            if (!next.isEmpty()) {
                return next;
            }
        }

        List<FlightPrice> nearestFromDb = findNearestFromDb(tripType, origin, destination, departureDate, returnDate);
        if (!nearestFromDb.isEmpty()) {
            FlightPrice first = nearestFromDb.get(0);
            TripType candidateTripType = resolveTripType(first.getTripType(), first.getReturnDate());
            log.info("DB_FALLBACK_HIT key={} source=nearest sourceDate={} sourceReturnDate={}",
                    sharedFlightCacheService.buildKey(tripType, origin, destination, departureDate, returnDate),
                    first.getDepartureDate(),
                    first.getReturnDate());
            sharedFlightCacheService.put(candidateTripType,
                    origin,
                    destination,
                    first.getDepartureDate(),
                    first.getReturnDate(),
                    nearestFromDb);
            log.info("CACHE_POPULATED_FROM_DB key={}",
                    sharedFlightCacheService.buildKey(candidateTripType, origin, destination, first.getDepartureDate(), first.getReturnDate()));
            return nearestFromDb;
        }
        return Collections.emptyList();
    }

    private List<FlightPrice> findExactFromDb(TripType tripType,
                                              String origin,
                                              String destination,
                                              LocalDate departureDate,
                                              LocalDate returnDate) {
        return sanitizeFlights(
                returnDate == null
                        ? flightPriceRepository.findByTripTypeAndOriginAndDestinationAndDepartureDateAndReturnDateIsNullOrderByPriceAsc(
                                tripType, origin, destination, departureDate)
                        : flightPriceRepository.findByTripTypeAndOriginAndDestinationAndDepartureDateAndReturnDateOrderByPriceAsc(
                                tripType, origin, destination, departureDate, returnDate),
                "db-exact",
                sharedFlightCacheService.buildKey(tripType, origin, destination, departureDate, returnDate));
    }

    private List<FlightPrice> findNearestFromDb(TripType tripType,
                                                String origin,
                                                String destination,
                                                LocalDate departureDate,
                                                LocalDate returnDate) {
        LocalDate returnDateFrom = returnDate == null ? null : returnDate.minusDays(2);
        LocalDate returnDateTo = returnDate == null ? null : returnDate.plusDays(2);
        List<FlightPrice> candidates = sanitizeFlights(
                returnDate == null
                        ? flightPriceRepository.findByTripTypeAndOriginAndDestinationAndDepartureDateBetweenAndReturnDateIsNullOrderByDepartureDateAscPriceAsc(
                                tripType,
                                origin,
                                destination,
                                departureDate.minusDays(2),
                                departureDate.plusDays(2))
                        : flightPriceRepository.findByTripTypeAndOriginAndDestinationAndDepartureDateBetweenAndReturnDateBetweenOrderByDepartureDateAscPriceAsc(
                                tripType,
                                origin,
                                destination,
                                departureDate.minusDays(2),
                                departureDate.plusDays(2),
                                returnDateFrom,
                                returnDateTo),
                "db-nearest",
                sharedFlightCacheService.buildKey(tripType, origin, destination, departureDate, returnDate));
        if (candidates.isEmpty()) {
            return Collections.emptyList();
        }

        FlightPrice nearestFlight = null;
        long nearestDistance = Long.MAX_VALUE;
        for (FlightPrice candidate : candidates) {
            long distance = Math.abs(ChronoUnit.DAYS.between(departureDate, candidate.getDepartureDate()));
            if (returnDate != null && candidate.getReturnDate() != null) {
                distance += Math.abs(ChronoUnit.DAYS.between(returnDate, candidate.getReturnDate()));
            }
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearestFlight = candidate;
            }
        }

        if (nearestFlight == null) {
            return Collections.emptyList();
        }

        List<FlightPrice> nearestFlights = new ArrayList<FlightPrice>();
        for (FlightPrice candidate : candidates) {
            if (candidate.getDepartureDate().equals(nearestFlight.getDepartureDate())
                    && Objects.equals(candidate.getReturnDate(), nearestFlight.getReturnDate())) {
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
        if (flight == null) {
            return false;
        }
        String airline = flight.getAirline();
        if (!StringUtils.hasText(airline)) {
            airline = flight.getOutboundAirline();
        }
        return StringUtils.hasText(airline)
                && !INVALID_DUFFEL_AIRLINE.equalsIgnoreCase(airline.trim());
    }

    private void logApproximateUsageIfNeeded(List<FlightPrice> flights) {
        if (!flights.isEmpty() && Boolean.TRUE.equals(flights.get(0).getApproximate())) {
            FlightPrice first = flights.get(0);
            log.info("APPROX_DATA_USED key={} sourceDate={} sourceReturnDate={}",
                    sharedFlightCacheService.buildKey(
                            resolveTripType(first.getTripType(), first.getReturnDate()),
                            first.getOrigin(),
                            first.getDestination(),
                            first.getDepartureDate(),
                            first.getReturnDate()),
                    first.getSourceDepartureDate(),
                    first.getReturnDate() == null || first.getDepartureDate() == null || first.getSourceDepartureDate() == null
                            ? null
                            : first.getReturnDate().minusDays(ChronoUnit.DAYS.between(first.getSourceDepartureDate(), first.getDepartureDate())));
        }
    }

    private TripType resolveTripType(TripType tripType, LocalDate returnDate) {
        if (tripType != null) {
            return tripType;
        }
        return returnDate == null ? TripType.ONE_WAY : TripType.ROUND_TRIP;
    }

    private void deleteCoverage(SearchKey key) {
        if (key.returnDate == null) {
            flightPriceRepository.deleteByTripTypeAndOriginAndDestinationAndDepartureDateAndReturnDateIsNull(
                    key.tripType,
                    key.origin,
                    key.destination,
                    key.departureDate);
            return;
        }
        flightPriceRepository.deleteByTripTypeAndOriginAndDestinationAndDepartureDateAndReturnDate(
                key.tripType,
                key.origin,
                key.destination,
                key.departureDate,
                key.returnDate);
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

    private static final class SearchKey {
        private final TripType tripType;
        private final String origin;
        private final String destination;
        private final LocalDate departureDate;
        private final LocalDate returnDate;

        private SearchKey(TripType tripType, String origin, String destination, LocalDate departureDate, LocalDate returnDate) {
            this.tripType = tripType;
            this.origin = origin;
            this.destination = destination;
            this.departureDate = departureDate;
            this.returnDate = returnDate;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof SearchKey)) {
                return false;
            }
            SearchKey that = (SearchKey) other;
            return tripType == that.tripType
                    && Objects.equals(origin, that.origin)
                    && Objects.equals(destination, that.destination)
                    && Objects.equals(departureDate, that.departureDate)
                    && Objects.equals(returnDate, that.returnDate);
        }

        @Override
        public int hashCode() {
            return Objects.hash(tripType, origin, destination, departureDate, returnDate);
        }
    }
}
