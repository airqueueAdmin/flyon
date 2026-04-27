package com.airplanehome.flight.service;

import com.airplanehome.flight.controller.dto.FlightSearchRequest;
import com.airplanehome.flight.controller.dto.TrackingRequest;
import com.airplanehome.flight.model.FlightPrice;
import com.airplanehome.flight.model.PriceHistory;
import com.airplanehome.flight.model.Tracking;
import com.airplanehome.flight.model.TripType;
import com.airplanehome.flight.repository.PriceHistoryRepository;
import com.airplanehome.flight.repository.TrackingRepository;
import com.airplanehome.flight.time.TimeSupport;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class FlightService {
    private static final Logger log = LoggerFactory.getLogger(FlightService.class);
    private static final int SUPPORTED_SEARCH_WINDOW_DAYS = 7;

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

    public List<FlightPrice> searchLowestPrice(FlightSearchRequest request) {
        TripType tripType = normalizeTripType(request.getTripType(), request.getReturnDate());
        LocalDate returnDate = normalizeReturnDate(tripType, request.getDepartureDate(), request.getReturnDate());
        int adults = normalizeAdults(request.getAdults());
        return searchLowestPrice(
                tripType,
                request.getOrigin(),
                request.getDestination(),
                request.getDepartureDate(),
                returnDate,
                Integer.valueOf(adults));
    }

    public List<FlightPrice> searchLowestPrice(String origin,
                                               String destination,
                                               LocalDate departureDate,
                                               LocalDate returnDate,
                                               Integer adults) {
        return searchLowestPrice(normalizeTripType(null, returnDate), origin, destination, departureDate, returnDate, adults);
    }

    public List<FlightPrice> searchLowestPrice(TripType tripType,
                                               String origin,
                                               String destination,
                                               LocalDate departureDate,
                                               LocalDate returnDate,
                                               Integer adults) {
        validateSearch(tripType, origin, destination, departureDate, returnDate);
        String normalizedOrigin = origin.trim().toUpperCase();
        String normalizedDestination = destination.trim().toUpperCase();
        TripType resolvedTripType = normalizeTripType(tripType, returnDate);
        Integer normalizedAdults = Integer.valueOf(normalizeAdults(adults));

        if (!flightPrefetchService.isSupported(resolvedTripType, normalizedOrigin, normalizedDestination, departureDate, returnDate)) {
            LocalDate todayKst = TimeSupport.nowKst().toLocalDate();
            LocalDate maxSupportedDate = todayKst.plusDays(SUPPORTED_SEARCH_WINDOW_DAYS - 1L);
            throw new IllegalArgumentException(String.format(
                    "이 노선은 %s부터 %s까지의 날짜만 조회할 수 있습니다. (KST 기준)",
                    todayKst,
                    maxSupportedDate));
        }

        List<FlightPrice> results = flightPrefetchService.getCachedOrFetchFlights(
                resolvedTripType,
                normalizedOrigin,
                normalizedDestination,
                departureDate,
                returnDate,
                normalizedAdults);
        if (results.isEmpty()) {
            throw new IllegalStateException("항공권 데이터를 준비 중입니다. 잠시 후 다시 시도해 주세요.");
        }
        normalizePricesToKrw(results);
        results.sort(Comparator.comparing(FlightPrice::getPrice));
        return results;
    }

    public Tracking createTracking(TrackingRequest request) {
        validateTrackingRequest(request);

        Tracking tracking = new Tracking();
        TripType tripType = normalizeTripType(request.getTripType(), request.getReturnDate());
        LocalDate returnDate = normalizeReturnDate(tripType, request.getDepartureDate(), request.getReturnDate());
        boolean kakaoEnabled = Boolean.TRUE.equals(request.getKakaoNotificationEnabled());
        boolean personalDataConsent = Boolean.TRUE.equals(request.getPersonalDataConsent());
        boolean kakaoOptIn = kakaoEnabled && Boolean.TRUE.equals(resolveKakaoOptIn(request));
        tracking.setTripType(tripType);
        tracking.setEmail(null);
        tracking.setOrigin(request.getOrigin().trim().toUpperCase());
        tracking.setDestination(request.getDestination().trim().toUpperCase());
        tracking.setDepartureDate(request.getDepartureDate());
        tracking.setReturnDate(returnDate);
        tracking.setPassengers(Integer.valueOf(normalizeAdults(request.getAdults())));
        tracking.setTargetPrice(request.getTargetPrice());
        tracking.setKakaoNotificationEnabled(Boolean.valueOf(kakaoEnabled));
        tracking.setPhoneNumber(kakaoEnabled ? normalizePhoneNumber(request.getPhoneNumber()) : null);
        tracking.setKakaoOptIn(Boolean.valueOf(kakaoOptIn));
        tracking.setPersonalDataConsent(Boolean.valueOf(personalDataConsent));
        tracking.setPersonalDataConsentAt(personalDataConsent ? TimeSupport.nowKst() : null);
        tracking.setKakaoOptInAt(kakaoOptIn ? TimeSupport.nowKst() : null);
        tracking.setLastUpdatedAt(TimeSupport.nowKst());

        List<FlightPrice> currentPrices = searchLowestPrice(
                tracking.getTripType(),
                tracking.getOrigin(),
                tracking.getDestination(),
                tracking.getDepartureDate(),
                tracking.getReturnDate(),
                tracking.getPassengers());

        if (!currentPrices.isEmpty()) {
            applyLatestPriceSnapshot(tracking, currentPrices.get(0));
        }
        Tracking savedTracking = trackingRepository.save(tracking);
        if (!currentPrices.isEmpty()) {
            saveHistory(savedTracking.getId(), currentPrices.get(0));
        }
        return savedTracking;
    }

    public Tracking getTracking(Long id) {
        return trackingRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("추적 정보를 찾을 수 없습니다. ID: " + id));
    }

    public List<Tracking> getTrackings() {
        return trackingRepository.findAll();
    }

    public void deleteTracking(Long id) {
        if (!trackingRepository.existsById(id)) {
            throw new EntityNotFoundException("추적 정보를 찾을 수 없습니다. ID: " + id);
        }
        priceHistoryRepository.deleteByTrackingId(id);
        trackingRepository.deleteById(id);
    }

    public List<PriceDropNotification> checkTrackedPrices() {
        List<Tracking> trackings = trackingRepository.findAll();
        List<PriceDropNotification> notifications = new ArrayList<PriceDropNotification>();
        for (Tracking tracking : trackings) {
            List<FlightPrice> currentPrices = searchLowestPrice(
                    normalizeTripType(tracking.getTripType(), tracking.getReturnDate()),
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
            applyLatestPriceSnapshot(tracking, currentPrice);
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

    private void applyLatestPriceSnapshot(Tracking tracking, FlightPrice currentPrice) {
        tracking.setLastCheckedPrice(currentPrice.getPrice());
        tracking.setLastCheckedCurrency(currentPrice.getCurrency());
        tracking.setLastBookingUrl(currentPrice.getBookingUrl());
        tracking.setLastAirline(currentPrice.getAirline());
        tracking.setLastInboundAirline(currentPrice.getInboundAirline());
        tracking.setLastDepartureTime(currentPrice.getDepartureTime());
        tracking.setLastArrivalTime(currentPrice.getArrivalTime());
        tracking.setLastReturnDepartureTime(currentPrice.getReturnDepartureTime());
        tracking.setLastReturnArrivalTime(currentPrice.getReturnArrivalTime());
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
                result.setTotalPrice(convertedPrice);
                result.setCurrency("KRW");
                continue;
            }

            if ("KRW".equals(normalizedCurrency)) {
                log.info("Converted price: {} KRW -> {} KRW",
                        result.getPrice().stripTrailingZeros().toPlainString(),
                        result.getPrice().stripTrailingZeros().toPlainString());
                result.setCurrency("KRW");
                result.setTotalPrice(result.getPrice());
            }
        }
    }

    private void validateTrackingRequest(TrackingRequest request) {
        TripType tripType = normalizeTripType(request.getTripType(), request.getReturnDate());
        validateSearch(tripType, request.getOrigin(), request.getDestination(), request.getDepartureDate(), request.getReturnDate());
        boolean kakaoEnabled = Boolean.TRUE.equals(request.getKakaoNotificationEnabled());
        boolean kakaoOptIn = Boolean.TRUE.equals(resolveKakaoOptIn(request));
        boolean personalDataConsent = Boolean.TRUE.equals(request.getPersonalDataConsent());
        if (kakaoEnabled && !personalDataConsent) {
            throw new IllegalArgumentException("카카오 알림을 사용하려면 개인정보 수집·이용에 동의해야 합니다.");
        }
        if (kakaoEnabled && !kakaoOptIn) {
            throw new IllegalArgumentException("카카오 알림을 사용하려면 알림톡 발송을 위한 개인정보 제공에 동의해야 합니다.");
        }
        if (kakaoEnabled && !StringUtils.hasText(request.getPhoneNumber())) {
            throw new IllegalArgumentException("카카오 알림톡을 사용하는 경우 전화번호를 입력해야 합니다.");
        }
    }

    private void validateSearch(TripType tripType,
                                String origin,
                                String destination,
                                LocalDate departureDate,
                                LocalDate returnDate) {
        if (!StringUtils.hasText(origin) || !StringUtils.hasText(destination) || departureDate == null) {
            throw new IllegalArgumentException("출발지, 도착지, 출발일은 필수 입력값입니다.");
        }
        normalizeReturnDate(normalizeTripType(tripType, returnDate), departureDate, returnDate);
    }

    private TripType normalizeTripType(TripType tripType, LocalDate returnDate) {
        if (tripType != null) {
            return tripType;
        }
        return returnDate == null ? TripType.ONE_WAY : TripType.ROUND_TRIP;
    }

    private LocalDate normalizeReturnDate(TripType tripType, LocalDate departureDate, LocalDate returnDate) {
        if (!tripType.isRoundTrip()) {
            return null;
        }
        if (returnDate == null) {
            throw new IllegalArgumentException("왕복 여정은 귀국일을 반드시 입력해야 합니다.");
        }
        if (departureDate != null && returnDate.isBefore(departureDate)) {
            throw new IllegalArgumentException("귀국일은 출발일과 같거나 이후여야 합니다.");
        }
        return returnDate;
    }

    private int normalizeAdults(Integer adults) {
        if (adults == null) {
            return 1;
        }
        if (adults.intValue() < 1) {
            throw new IllegalArgumentException("성인 승객 수는 1명 이상이어야 합니다.");
        }
        return adults.intValue();
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
