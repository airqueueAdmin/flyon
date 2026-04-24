package com.airplanehome.flight.service;

import com.airplanehome.flight.model.FlightPrice;
import com.airplanehome.flight.model.TripType;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class SharedFlightCacheService {
    private static final Duration FLIGHT_CACHE_DURATION = Duration.ofMinutes(20);

    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<String, CacheEntry>();

    public String buildKey(TripType tripType, String origin, String destination, LocalDate departureDate, LocalDate returnDate) {
        StringBuilder key = new StringBuilder();
        key.append(tripType == null ? TripType.ONE_WAY.name() : tripType.name())
                .append("|")
                .append(normalize(origin))
                .append("|")
                .append(normalize(destination))
                .append("|")
                .append(departureDate);
        if (returnDate != null) {
            key.append("|").append(returnDate);
        }
        return key.toString();
    }

    public List<FlightPrice> getFresh(TripType tripType, String origin, String destination, LocalDate departureDate, LocalDate returnDate) {
        CacheEntry entry = cache.get(buildKey(tripType, origin, destination, departureDate, returnDate));
        if (entry == null || entry.isExpired()) {
            return Collections.emptyList();
        }
        return copy(entry.getFlights());
    }

    public List<FlightPrice> getStale(TripType tripType, String origin, String destination, LocalDate departureDate, LocalDate returnDate) {
        CacheEntry entry = cache.get(buildKey(tripType, origin, destination, departureDate, returnDate));
        if (entry == null) {
            return Collections.emptyList();
        }
        return copy(entry.getFlights());
    }

    public boolean hasEntry(TripType tripType, String origin, String destination, LocalDate departureDate, LocalDate returnDate) {
        return cache.containsKey(buildKey(tripType, origin, destination, departureDate, returnDate));
    }

    public boolean isExpired(TripType tripType, String origin, String destination, LocalDate departureDate, LocalDate returnDate) {
        CacheEntry entry = cache.get(buildKey(tripType, origin, destination, departureDate, returnDate));
        return entry == null || entry.isExpired();
    }

    public void put(TripType tripType, String origin, String destination, LocalDate departureDate, LocalDate returnDate, List<FlightPrice> flights) {
        cache.put(buildKey(tripType, origin, destination, departureDate, returnDate),
                new CacheEntry(copy(flights), Instant.now().plus(FLIGHT_CACHE_DURATION)));
    }

    public void warm(TripType tripType,
                     String origin,
                     String destination,
                     LocalDate departureDate,
                     LocalDate returnDate,
                     List<FlightPrice> flights,
                     LocalDateTime prefetchedAt) {
        Instant expiresAt = prefetchedAt.plus(FLIGHT_CACHE_DURATION).atZone(java.time.ZoneId.systemDefault()).toInstant();
        if (Instant.now().isAfter(expiresAt)) {
            return;
        }
        cache.put(buildKey(tripType, origin, destination, departureDate, returnDate), new CacheEntry(copy(flights), expiresAt));
    }

    public void evict(TripType tripType, String origin, String destination, LocalDate departureDate, LocalDate returnDate) {
        cache.remove(buildKey(tripType, origin, destination, departureDate, returnDate));
    }

    public void clear() {
        cache.clear();
    }

    private List<FlightPrice> copy(List<FlightPrice> flights) {
        return new ArrayList<FlightPrice>(flights);
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.replaceAll("[^A-Za-z0-9]", "").trim().toUpperCase();
    }

    private static final class CacheEntry {
        private final List<FlightPrice> flights;
        private final Instant expiresAt;

        private CacheEntry(List<FlightPrice> flights, Instant expiresAt) {
            this.flights = flights;
            this.expiresAt = expiresAt;
        }

        private List<FlightPrice> getFlights() {
            return flights;
        }

        private boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }
}
