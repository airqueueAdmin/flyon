package com.airplanehome.flight.scheduler;

import com.airplanehome.flight.service.RestaurantService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RestaurantRefreshScheduler {

    private static final Logger log = LoggerFactory.getLogger(RestaurantRefreshScheduler.class);
    private final RestaurantService restaurantService;

    public RestaurantRefreshScheduler(RestaurantService restaurantService) {
        this.restaurantService = restaurantService;
    }

    @Scheduled(cron = "0 0 3 * * MON")
    public void refreshAll() {
        log.info("RESTAURANT_REFRESH_START");
        for (String cityCode : RestaurantService.CITY_META.keySet()) {
            try {
                restaurantService.fetchAndCache(cityCode);
                Thread.sleep(500L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("RESTAURANT_REFRESH_INTERRUPTED");
                break;
            } catch (Exception e) {
                log.error("RESTAURANT_REFRESH_FAILED city={}", cityCode, e);
            }
        }
        log.info("RESTAURANT_REFRESH_DONE");
    }
}
