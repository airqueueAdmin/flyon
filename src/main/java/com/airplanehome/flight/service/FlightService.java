package com.airplanehome.flight.service;

import com.airplanehome.flight.controller.dto.TrackingRequest;
import com.airplanehome.flight.model.FlightPrice;
import com.airplanehome.flight.model.PriceHistory;
import com.airplanehome.flight.model.Tracking;
import com.airplanehome.flight.repository.PriceHistoryRepository;
import com.airplanehome.flight.repository.TrackingRepository;
import com.airplanehome.flight.time.TimeSupport;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import javax.persistence.EntityNotFoundException;
import org.springframework.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class FlightService {
    private static final Logger log = LoggerFactory.getLogger(FlightService.class);

    private final TrackingRepository trackingRepository;
    private final PriceHistoryRepository priceHistoryRepository;
    private final ExchangeRateService exchangeRateService;
    private final FlightPrefetchService flightPrefetchService;

    public FlightService(TrackingRepository trackingRepository,
                         PriceHistoryRepository priceHistoryRepository,
                         ExchangeRateService exchangeRateService,
                         FlightPrefetchService flightPrefetchService) {
        this.trackingRepository = trackingRepository;
        this.priceHistoryRepository = priceHistoryRepository;
        this.exchangeRateService = exchangeRateService;
        this.flightPrefetchService = flightPrefetchService;
    }

    public List<FlightPrice> searchLowestPrice(String origin, String destination, LocalDate departureDate,
                                               LocalDate returnDate, Integer passengers) {
        String normalizedOrigin = origin.trim().toUpperCase();
        String normalizedDestination = destination.trim().toUpperCase();
        if (!flightPrefetchService.isSupported(normalizedOrigin, normalizedDestination, departureDate)) {
            throw new IllegalArgumentException("This route is not yet supported.");
        }

        List<FlightPrice> results = flightPrefetchService.getCachedOrFetchFlights(
                normalizedOrigin,
                normalizedDestination,
                departureDate);
        if (results.isEmpty()) {
            throw new IllegalStateException("Flight data is being prepared. Please try again in a few minutes.");
        }
        normalizePricesToKrw(results);
        results.sort(Comparator.comparing(FlightPrice::getPrice));
        return results;
    }

    public Tracking createTracking(TrackingRequest request) {
        validateTrackingRequest(request);

        Tracking tracking = new Tracking();
        tracking.setEmail(request.getEmail());
        tracking.setOrigin(request.getOrigin().trim().toUpperCase());
        tracking.setDestination(request.getDestination().trim().toUpperCase());
        tracking.setDepartureDate(request.getDepartureDate());
        tracking.setReturnDate(request.getReturnDate());
        tracking.setPassengers(request.getPassengers());
        tracking.setTargetPrice(request.getTargetPrice());
        tracking.setKakaoNotificationEnabled(Boolean.TRUE.equals(request.getKakaoNotificationEnabled()));
        tracking.setPhoneNumber(normalizePhoneNumber(request.getPhoneNumber()));
        tracking.setKakaoOptIn(resolveKakaoOptIn(request));
        tracking.setLastUpdatedAt(TimeSupport.nowKst());

        List<FlightPrice> currentPrices = searchLowestPrice(
                tracking.getOrigin(),
                tracking.getDestination(),
                tracking.getDepartureDate(),
                tracking.getReturnDate(),
                tracking.getPassengers());

        if (!currentPrices.isEmpty()) {
            tracking.setLastCheckedPrice(currentPrices.get(0).getPrice());
            tracking.setLastCheckedCurrency(currentPrices.get(0).getCurrency());
        }
        Tracking savedTracking = trackingRepository.save(tracking);
        if (!currentPrices.isEmpty()) {
            saveHistory(savedTracking.getId(), currentPrices.get(0));
        }
        return savedTracking;
    }

    public Tracking getTracking(Long id) {
        return trackingRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Tracking not found: " + id));
    }

    public List<Tracking> getTrackings() {
        return trackingRepository.findAll();
    }

    public void deleteTracking(Long id) {
        if (!trackingRepository.existsById(id)) {
            throw new EntityNotFoundException("Tracking not found: " + id);
        }
        trackingRepository.deleteById(id);
    }

    public List<PriceDropNotification> checkTrackedPrices() {
        List<Tracking> trackings = trackingRepository.findAll();
        List<PriceDropNotification> notifications = new ArrayList<>();
        for (Tracking tracking : trackings) {
            List<FlightPrice> currentPrices = searchLowestPrice(
                    tracking.getOrigin(),
                    tracking.getDestination(),
                    tracking.getDepartureDate(),
                    tracking.getReturnDate(),
                    tracking.getPassengers());
            if (currentPrices.isEmpty()) {
                if (tracking.getLastCheckedPrice() != null) {
                    log.info("Using last known tracked price trackingId={} price={}",
                            tracking.getId(),
                            tracking.getLastCheckedPrice().stripTrailingZeros().toPlainString());
                    tracking.setLastUpdatedAt(TimeSupport.nowKst());
                    trackingRepository.save(tracking);
                }
                continue;
            }

            FlightPrice currentPrice = currentPrices.get(0);

            saveHistory(tracking.getId(), currentPrice);
            PriceDropNotification notification = buildPriceDropNotification(tracking, currentPrice);
            if (notification != null) {
                notifications.add(notification);
            }
            tracking.setLastCheckedPrice(currentPrice.getPrice());
            tracking.setLastCheckedCurrency(currentPrice.getCurrency());
            tracking.setLastUpdatedAt(TimeSupport.nowKst());
            trackingRepository.save(tracking);
        }
        return notifications;
    }

    public Tracking saveTracking(Tracking tracking) {
        return trackingRepository.save(tracking);
    }

    private void saveHistory(Long trackingId, FlightPrice currentPrice) {
        PriceHistory history = new PriceHistory();
        history.setTrackingId(trackingId);
        history.setPrice(currentPrice.getPrice());
        history.setCurrency(currentPrice.getCurrency());
        history.setCheckedAt(LocalDateTime.now());
        priceHistoryRepository.save(history);
    }

    private PriceDropNotification buildPriceDropNotification(Tracking tracking, FlightPrice currentPrice) {
        BigDecimal previousPrice = tracking.getLastCheckedPrice();
        boolean lowerThanPrevious = previousPrice != null && currentPrice.getPrice().compareTo(previousPrice) < 0;
        boolean lowerThanTarget = tracking.getTargetPrice() != null
                && currentPrice.getPrice().compareTo(tracking.getTargetPrice()) <= 0;
        boolean kakaoEnabled = Boolean.TRUE.equals(tracking.getKakaoNotificationEnabled());
        boolean kakaoOptIn = Boolean.TRUE.equals(tracking.getKakaoOptIn());
        boolean hasPhoneNumber = StringUtils.hasText(tracking.getPhoneNumber());

        if (kakaoEnabled && kakaoOptIn && hasPhoneNumber && (lowerThanPrevious || lowerThanTarget)) {
            return new PriceDropNotification(
                    tracking.getId(),
                    tracking.getOrigin(),
                    tracking.getDestination(),
                    tracking.getDepartureDate(),
                    previousPrice,
                    currentPrice.getPrice(),
                    currentPrice.getCurrency());
        }
        return null;
    }

    private void normalizePricesToKrw(List<FlightPrice> results) {
        Double usdToKrwRate = null;
        for (FlightPrice result : results) {
            if (result.getPrice() == null) {
                continue;
            }

            String normalizedCurrency = result.getCurrency() == null
                    ? "KRW"
                    : result.getCurrency().trim().toUpperCase();

            if ("USD".equals(normalizedCurrency)) {
                if (usdToKrwRate == null) {
                    usdToKrwRate = exchangeRateService.getUsdToKrwRate();
                }
                BigDecimal convertedPrice = result.getPrice()
                        .multiply(BigDecimal.valueOf(usdToKrwRate))
                        .setScale(0, RoundingMode.HALF_UP);
                log.info("Converted price: {} USD -> {} KRW",
                        result.getPrice().stripTrailingZeros().toPlainString(),
                        convertedPrice.toPlainString());
                result.setPrice(convertedPrice);
                result.setCurrency("KRW");
                continue;
            }

            if ("KRW".equals(normalizedCurrency)) {
                log.info("Converted price: {} KRW -> {} KRW",
                        result.getPrice().stripTrailingZeros().toPlainString(),
                        result.getPrice().stripTrailingZeros().toPlainString());
                result.setCurrency("KRW");
            }
        }
    }

    private void validateTrackingRequest(TrackingRequest request) {
        boolean kakaoEnabled = Boolean.TRUE.equals(request.getKakaoNotificationEnabled());
        boolean kakaoOptIn = Boolean.TRUE.equals(resolveKakaoOptIn(request));
        if (kakaoEnabled && kakaoOptIn && !StringUtils.hasText(request.getPhoneNumber())) {
            throw new IllegalArgumentException("phoneNumber is required when Kakao AlimTalk is enabled.");
        }
    }

    private Boolean resolveKakaoOptIn(TrackingRequest request) {
        if (request.getKakaoOptIn() != null) {
            return request.getKakaoOptIn();
        }
        return Boolean.TRUE.equals(request.getKakaoNotificationEnabled());
    }

    private String normalizePhoneNumber(String phoneNumber) {
        if (!StringUtils.hasText(phoneNumber)) {
            return null;
        }
        return phoneNumber.replaceAll("[^0-9]", "");
    }
}
