package com.airplanehome.flight.scheduler;

import com.airplanehome.flight.model.Tracking;
import com.airplanehome.flight.service.FlightService;
import com.airplanehome.flight.service.KakaoNotificationService;
import com.airplanehome.flight.service.PriceDropNotification;
import java.math.BigDecimal;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PriceTrackerScheduler {
    private static final Logger log = LoggerFactory.getLogger(PriceTrackerScheduler.class);

    private final FlightService flightService;
    private final KakaoNotificationService kakaoNotificationService;
    private final BigDecimal minPriceDropKrw;
    private final BigDecimal minPriceDropPercent;

    public PriceTrackerScheduler(FlightService flightService,
                                 KakaoNotificationService kakaoNotificationService,
                                 @Value("${app.kakao.min-price-drop-krw:10000}") BigDecimal minPriceDropKrw,
                                 @Value("${app.kakao.min-price-drop-percent:5}") BigDecimal minPriceDropPercent) {
        this.flightService = flightService;
        this.kakaoNotificationService = kakaoNotificationService;
        this.minPriceDropKrw = minPriceDropKrw;
        this.minPriceDropPercent = minPriceDropPercent;
    }

    @Scheduled(fixedDelayString = "${app.tracking.interval-ms:300000}")
    public void checkTrackedPrices() {
        List<PriceDropNotification> notifications = flightService.checkTrackedPrices();
        for (PriceDropNotification notification : notifications) {
            try {
                Tracking tracking = flightService.getTracking(notification.getTrackingId());
                if (isDuplicateNotification(tracking, notification.getCurrentPrice())) {
                    log.info("KAKAO_SKIPPED: duplicate {}→{} {}",
                            tracking.getOrigin(),
                            tracking.getDestination(),
                            notification.getCurrentPrice());
                    continue;
                }

                if (!meetsNotificationThreshold(notification.getPreviousPrice(), notification.getCurrentPrice())) {
                    log.info("KAKAO_SKIPPED: below threshold {}→{} {}→{}",
                            tracking.getOrigin(),
                            tracking.getDestination(),
                            notification.getPreviousPrice(),
                            notification.getCurrentPrice());
                    continue;
                }

                int oldPrice = toInt(notification.getPreviousPrice());
                int newPrice = toInt(notification.getCurrentPrice());
                boolean sent = kakaoNotificationService.sendAlimTalk(tracking, oldPrice, newPrice);
                if (sent) {
                    tracking.setLastNotifiedPrice(notification.getCurrentPrice());
                    flightService.saveTracking(tracking);
                }
            } catch (Exception ex) {
                log.error("KAKAO_FAILED: {}", ex.getMessage());
            }
        }
    }

    private boolean isDuplicateNotification(Tracking tracking, BigDecimal newPrice) {
        return tracking.getLastNotifiedPrice() != null
                && newPrice != null
                && newPrice.compareTo(tracking.getLastNotifiedPrice()) == 0;
    }

    private int toInt(BigDecimal price) {
        return price == null ? 0 : price.intValue();
    }

    private boolean meetsNotificationThreshold(BigDecimal previousPrice, BigDecimal currentPrice) {
        if (previousPrice == null || currentPrice == null) {
            return false;
        }

        BigDecimal priceDrop = previousPrice.subtract(currentPrice);
        if (priceDrop.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        if (priceDrop.compareTo(minPriceDropKrw) >= 0) {
            return true;
        }

        BigDecimal priceDropPercent = priceDrop
                .multiply(BigDecimal.valueOf(100))
                .divide(previousPrice, 2, java.math.RoundingMode.HALF_UP);
        return priceDropPercent.compareTo(minPriceDropPercent) >= 0;
    }
}
