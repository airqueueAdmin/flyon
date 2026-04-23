package com.airplanehome.flight.service;

import com.airplanehome.flight.model.FlightPrice;
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

    public String buildKey(String origin, String destination, LocalDate departureDate) {
        return normalize(origin) + "|" + normalize(destination) + "|" + departureDate;
    }

    public List<FlightPrice> getFresh(String origin, String destination, LocalDate departureDate) {
        CacheEntry entry = cache.get(buildKey(origin, destination, departureDate));
        if (entry == null || entry.isExpired()) {
            return Collections.emptyList();
        }
        return copy(entry.getFlights());
    }

    public List<FlightPrice> getStale(String origin, String destination, LocalDate departureDate) {
        CacheEntry entry = cache.get(buildKey(origin, destination, departureDate));
        if (entry == null) {
            return Collections.emptyList();
        }
        return copy(entry.getFlights());
    }

    public boolean hasEntry(String origin, String destination, LocalDate departureDate) {
        return cache.containsKey(buildKey(origin, destination, departureDate));
    }

    public boolean isExpired(String origin, String destination, LocalDate departureDate) {
        CacheEntry entry = cache.get(buildKey(origin, destination, departureDate));
        return entry == null || entry.isExpired();
    }

    public void put(String origin, String destination, LocalDate departureDate, List<FlightPrice> flights) {
        cache.put(buildKey(origin, destination, departureDate),
                new CacheEntry(copy(flights), Instant.now().plus(FLIGHT_CACHE_DURATION)));
    }

    public void warm(String origin, String destination, LocalDate departureDate, List<FlightPrice> flights, LocalDateTime prefetchedAt) {
        Instant expiresAt = prefetchedAt.plus(FLIGHT_CACHE_DURATION).atZone(java.time.ZoneId.systemDefault()).toInstant();
        if (Instant.now().isAfter(expiresAt)) {
            return;
        }
        cache.put(buildKey(origin, destination, departureDate), new CacheEntry(copy(flights), expiresAt));
    }

    public void evict(String origin, String destination, LocalDate departureDate) {
        cache.remove(buildKey(origin, destination, departureDate));
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
