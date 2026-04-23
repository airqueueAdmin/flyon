package com.airplanehome.flight.scheduler;

import com.airplanehome.flight.service.FlightPrefetchService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class FlightPrefetchScheduler {
    private final FlightPrefetchService flightPrefetchService;

    public FlightPrefetchScheduler(FlightPrefetchService flightPrefetchService) {
        this.flightPrefetchService = flightPrefetchService;
    }

    @Scheduled(fixedDelayString = "${app.prefetch.interval-ms:600000}")
    public void prefetchPopularRoutes() {
        flightPrefetchService.prefetchPopularRoutes();
    }
}
