package com.airplanehome.flight.service;

import com.airplanehome.flight.controller.dto.DailyPriceDto;
import com.airplanehome.flight.controller.dto.DealDto;
import com.airplanehome.flight.controller.dto.FlightSearchRequest;
import com.airplanehome.flight.controller.dto.KakaoLinkRequest;
import com.airplanehome.flight.controller.dto.TrackingRequest;
import com.airplanehome.flight.model.FlightPrice;
import com.airplanehome.flight.model.KakaoAuthConnection;
import com.airplanehome.flight.model.PriceHistory;
import com.airplanehome.flight.model.Tracking;
import com.airplanehome.flight.model.TripType;
import com.airplanehome.flight.repository.FlightPriceRepository;
import com.airplanehome.flight.repository.PriceHistoryRepository;
import com.airplanehome.flight.repository.TrackingRepository;
import com.airplanehome.flight.time.TimeSupport;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class FlightService {
    private static final Logger log = LoggerFactory.getLogger(FlightService.class);
    private static final int SUPPORTED_SEARCH_WINDOW_DAYS = 7;
    private static final String OWNER_TOKEN_REQUIRED_MESSAGE = "브라우저 식별 정보가 없어 추적을 저장할 수 없습니다. 새로고침 후 다시 시도해 주세요.";
    private static final String TRACKING_ACCESS_DENIED_MESSAGE = "해당 추적 목록에 접근할 권한이 없습니다.";

    private final TrackingRepository trackingRepository;
    private final PriceHistoryRepository priceHistoryRepository;
    private final FlightPriceRepository flightPriceRepository;
    private final ExchangeRateService exchangeRateService;
    private final FlightPrefetchService flightPrefetchService;
    private final KakaoAuthService kakaoAuthService;

    public FlightService(TrackingRepository trackingRepository,
                         PriceHistoryRepository priceHistoryRepository,
                         FlightPriceRepository flightPriceRepository,
                         ExchangeRateService exchangeRateService,
                         FlightPrefetchService flightPrefetchService,
                         KakaoAuthService kakaoAuthService) {
        this.trackingRepository = trackingRepository;
        this.priceHistoryRepository = priceHistoryRepository;
        this.flightPriceRepository = flightPriceRepository;
        this.exchangeRateService = exchangeRateService;
        this.flightPrefetchService = flightPrefetchService;
        this.kakaoAuthService = kakaoAuthService;
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

    public Tracking createTracking(TrackingRequest request, String ownerToken) {
        validateTrackingRequest(request);
        if (!StringUtils.hasText(ownerToken)) {
            throw new IllegalArgumentException(OWNER_TOKEN_REQUIRED_MESSAGE);
        }

        Tracking tracking = new Tracking();
        TripType tripType = normalizeTripType(request.getTripType(), request.getReturnDate());
        LocalDate returnDate = normalizeReturnDate(tripType, request.getDepartureDate(), request.getReturnDate());
        boolean kakaoEnabled = Boolean.TRUE.equals(request.getKakaoNotificationEnabled());
        boolean kakaoOptIn = kakaoEnabled && Boolean.TRUE.equals(resolveKakaoOptIn(request));
        KakaoAuthConnection kakaoConnection = kakaoEnabled
                ? kakaoAuthService.getConnection(request.getKakaoConnectionId())
                : null;
        tracking.setTripType(tripType);
        tracking.setEmail(null);
        tracking.setOrigin(request.getOrigin().trim().toUpperCase());
        tracking.setDestination(request.getDestination().trim().toUpperCase());
        tracking.setDepartureDate(request.getDepartureDate());
        tracking.setReturnDate(returnDate);
        tracking.setPassengers(Integer.valueOf(normalizeAdults(request.getAdults())));
        tracking.setTargetPrice(request.getTargetPrice());
        tracking.setKakaoNotificationEnabled(Boolean.valueOf(kakaoEnabled));
        tracking.setPhoneNumber(null);
        tracking.setKakaoUserId(kakaoConnection == null ? null : kakaoConnection.getKakaoUserId());
        tracking.setKakaoConnectionId(kakaoConnection == null ? null : kakaoConnection.getId());
        tracking.setKakaoAccessToken(kakaoConnection == null ? null : kakaoConnection.getAccessToken());
        tracking.setKakaoRefreshToken(kakaoConnection == null ? null : kakaoConnection.getRefreshToken());
        tracking.setKakaoAccessTokenExpiresAt(kakaoConnection == null ? null : kakaoConnection.getAccessTokenExpiresAt());
        tracking.setKakaoRefreshTokenExpiresAt(kakaoConnection == null ? null : kakaoConnection.getRefreshTokenExpiresAt());
        tracking.setKakaoNickname(kakaoConnection == null ? null : kakaoConnection.getNickname());
        tracking.setKakaoOptIn(Boolean.valueOf(kakaoOptIn));
        tracking.setPersonalDataConsent(Boolean.FALSE);
        tracking.setPersonalDataConsentAt(null);
        tracking.setKakaoOptInAt(kakaoOptIn ? TimeSupport.nowKst() : null);
        tracking.setOwnerTokenHash(hashToken(ownerToken));
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

    public Tracking getTracking(Long id, String ownerToken, String kakaoConnectionId) {
        Tracking tracking = getTracking(id);
        assertAccessible(tracking, buildAccessContext(ownerToken, kakaoConnectionId));
        return tracking;
    }

    public List<Tracking> getTrackings(String ownerToken, String kakaoConnectionId) {
        TrackingAccessContext accessContext = buildAccessContext(ownerToken, kakaoConnectionId);
        Map<Long, Tracking> collected = new LinkedHashMap<Long, Tracking>();

        if (accessContext.ownerTokenHash != null) {
            for (Tracking tracking : trackingRepository.findByOwnerTokenHashOrderByLastUpdatedAtDesc(accessContext.ownerTokenHash)) {
                collected.put(tracking.getId(), tracking);
            }
        }

        if (accessContext.kakaoUserId != null) {
            for (Tracking tracking : trackingRepository.findByKakaoUserIdOrderByLastUpdatedAtDesc(accessContext.kakaoUserId)) {
                collected.put(tracking.getId(), tracking);
            }
        }

        List<Tracking> results = new ArrayList<Tracking>(collected.values());
        results.sort(Comparator
                .comparing(Tracking::getLastUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(Tracking::getId, Comparator.nullsLast(Comparator.reverseOrder())));
        return results;
    }

    @Transactional
    public void deleteTracking(Long id, String ownerToken, String kakaoConnectionId) {
        Tracking tracking = getTracking(id);
        assertAccessible(tracking, buildAccessContext(ownerToken, kakaoConnectionId));
        priceHistoryRepository.deleteByTrackingId(id);
        trackingRepository.deleteById(id);
    }

    public List<PriceDropNotification> checkTrackedPrices() {
        List<Tracking> trackings = trackingRepository.findAll();
        LocalDate todayKst = TimeSupport.nowKst().toLocalDate();
        List<PriceDropNotification> notifications = new ArrayList<PriceDropNotification>();
        for (Tracking tracking : trackings) {
            if (tracking.getDepartureDate().isBefore(todayKst)) {
                continue;
            }
            try {
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
                    }
                    continue;
                }

                FlightPrice currentPrice = currentPrices.get(0);

                boolean priceChanged = tracking.getLastCheckedPrice() == null
                        || currentPrice.getPrice().compareTo(tracking.getLastCheckedPrice()) != 0;
                if (priceChanged) {
                    saveHistory(tracking.getId(), currentPrice);
                }
                PriceDropNotification notification = buildPriceDropNotification(tracking, currentPrice);
                if (notification != null) {
                    notifications.add(notification);
                }
                if (priceChanged || notification != null) {
                    applyLatestPriceSnapshot(tracking, currentPrice);
                    tracking.setLastUpdatedAt(TimeSupport.nowKst());
                    trackingRepository.save(tracking);
                }
            } catch (Exception ex) {
                log.warn("TRACKING_SKIP: trackingId={} {}→{} {}: {}",
                        tracking.getId(),
                        tracking.getOrigin(),
                        tracking.getDestination(),
                        tracking.getDepartureDate(),
                        ex.getMessage());
            }
        }
        return notifications;
    }

    @Transactional
    public Tracking linkKakao(Long id, String ownerToken, String kakaoConnectionIdHeader, KakaoLinkRequest request) {
        if (!StringUtils.hasText(request.getKakaoConnectionId())) {
            throw new IllegalArgumentException("카카오 연결 ID가 없습니다.");
        }
        Tracking tracking = getTracking(id);
        assertAccessible(tracking, buildAccessContext(ownerToken, kakaoConnectionIdHeader));

        KakaoAuthConnection connection = kakaoAuthService.getConnection(request.getKakaoConnectionId());
        boolean kakaoOptIn = Boolean.TRUE.equals(request.getKakaoOptIn());

        tracking.setKakaoNotificationEnabled(Boolean.TRUE);
        tracking.setKakaoUserId(connection.getKakaoUserId());
        tracking.setKakaoConnectionId(connection.getId());
        tracking.setKakaoAccessToken(connection.getAccessToken());
        tracking.setKakaoRefreshToken(connection.getRefreshToken());
        tracking.setKakaoAccessTokenExpiresAt(connection.getAccessTokenExpiresAt());
        tracking.setKakaoRefreshTokenExpiresAt(connection.getRefreshTokenExpiresAt());
        tracking.setKakaoNickname(connection.getNickname());
        tracking.setKakaoOptIn(Boolean.valueOf(kakaoOptIn));
        if (kakaoOptIn && tracking.getKakaoOptInAt() == null) {
            tracking.setKakaoOptInAt(TimeSupport.nowKst());
        }
        tracking.setLastUpdatedAt(TimeSupport.nowKst());

        return trackingRepository.save(tracking);
    }

    public Tracking saveTracking(Tracking tracking) {
        return trackingRepository.save(tracking);
    }

    public List<PriceHistory> getTrackingHistory(Long id, String ownerToken, String kakaoConnectionId) {
        getTracking(id, ownerToken, kakaoConnectionId);
        return priceHistoryRepository.findByTrackingIdOrderByCheckedAtAsc(id);
    }

    public List<DailyPriceDto> getCalendar(String origin, String destination) {
        LocalDate today = TimeSupport.nowKst().toLocalDate();
        LocalDate endDate = today.plusDays(SUPPORTED_SEARCH_WINDOW_DAYS - 1);
        String normalizedOrigin = origin.trim().toUpperCase();
        String normalizedDest = destination.trim().toUpperCase();

        List<FlightPrice> prices = flightPriceRepository
                .findByTripTypeAndOriginAndDestinationAndDepartureDateBetweenAndReturnDateIsNullOrderByDepartureDateAscPriceAsc(
                        TripType.ONE_WAY, normalizedOrigin, normalizedDest, today, endDate);

        Double[] rate = {null};
        // exact(실제 API 조회) 우선, 해당 날짜에 exact 없을 때만 approximate 사용
        Map<LocalDate, DailyPriceDto> minExactByDate = new LinkedHashMap<>();
        Map<LocalDate, DailyPriceDto> minApproxByDate = new LinkedHashMap<>();
        for (FlightPrice fp : prices) {
            if (fp.getPrice() == null || fp.getDepartureDate() == null) continue;
            BigDecimal krw = toKrw(fp.getPrice(), fp.getCurrency(), rate);
            boolean isApprox = Boolean.TRUE.equals(fp.getApproximate());
            Map<LocalDate, DailyPriceDto> target = isApprox ? minApproxByDate : minExactByDate;
            DailyPriceDto existing = target.get(fp.getDepartureDate());
            if (existing == null || existing.getPrice() == null || krw.compareTo(existing.getPrice()) < 0) {
                target.put(fp.getDepartureDate(),
                        new DailyPriceDto(fp.getDepartureDate(), krw, isApprox, fp.getAirline()));
            }
        }
        // exact가 있는 날짜는 exact로 덮어씀
        Map<LocalDate, DailyPriceDto> minByDate = new LinkedHashMap<>(minApproxByDate);
        minByDate.putAll(minExactByDate);

        List<DailyPriceDto> result = new ArrayList<>();
        for (LocalDate date = today; !date.isAfter(endDate); date = date.plusDays(1)) {
            DailyPriceDto dto = minByDate.get(date);
            result.add(dto != null ? dto : new DailyPriceDto(date, null, false, null));
        }
        return result;
    }

    public List<DealDto> getDeals(String origin) {
        LocalDate today = TimeSupport.nowKst().toLocalDate();
        LocalDate endDate = today.plusDays(SUPPORTED_SEARCH_WINDOW_DAYS - 1);

        List<FlightPrice> prices = flightPriceRepository
                .findByTripTypeAndOriginAndDepartureDateBetweenAndReturnDateIsNullOrderByPriceAsc(
                        TripType.ONE_WAY, origin.trim().toUpperCase(), today, endDate);

        Double[] rate = {null};
        // approximate(근사) 데이터는 가격 정확도가 낮으므로 딜 표시에서 제외, exact만 사용
        Map<String, DealDto> minExactByDest = new LinkedHashMap<>();
        for (FlightPrice fp : prices) {
            if (fp.getPrice() == null || fp.getDestination() == null) continue;
            if (Boolean.TRUE.equals(fp.getApproximate())) continue;
            BigDecimal krw = toKrw(fp.getPrice(), fp.getCurrency(), rate);
            DealDto existing = minExactByDest.get(fp.getDestination());
            if (existing == null || krw.compareTo(existing.getPrice()) < 0) {
                minExactByDest.put(fp.getDestination(),
                        new DealDto(fp.getDestination(), krw, fp.getDepartureDate(), fp.getAirline(), false));
            }
        }

        return minExactByDest.values().stream()
                .sorted(Comparator.comparing(DealDto::getPrice))
                .collect(Collectors.toList());
    }

    private BigDecimal toKrw(BigDecimal price, String currency, Double[] rateHolder) {
        if (price == null) return null;
        String c = currency == null ? "KRW" : currency.trim().toUpperCase();
        if ("USD".equals(c)) {
            if (rateHolder[0] == null) {
                rateHolder[0] = exchangeRateService.getUsdToKrwRate();
            }
            return price.multiply(BigDecimal.valueOf(rateHolder[0])).setScale(0, RoundingMode.HALF_UP);
        }
        return price;
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
        boolean kakaoLinked = tracking.isKakaoLinked();
        boolean hasAccessToken = StringUtils.hasText(tracking.getKakaoAccessToken());

        if (kakaoEnabled && kakaoOptIn && kakaoLinked && hasAccessToken && (lowerThanPrevious || lowerThanTarget)) {
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
        if (kakaoEnabled && !kakaoOptIn) {
            throw new IllegalArgumentException("카카오 알림을 사용하려면 카카오 메시지 수신에 동의해야 합니다.");
        }
        if (kakaoEnabled && !StringUtils.hasText(request.getKakaoConnectionId())) {
            throw new IllegalArgumentException("카카오 알림을 사용하려면 먼저 카카오 로그인을 연결해야 합니다.");
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

    private void assertAccessible(Tracking tracking, TrackingAccessContext accessContext) {
        if (accessContext == null || !accessContext.canAccess(tracking)) {
            throw new TrackingAccessDeniedException(TRACKING_ACCESS_DENIED_MESSAGE);
        }
    }

    private TrackingAccessContext buildAccessContext(String ownerToken, String kakaoConnectionId) {
        String ownerTokenHash = StringUtils.hasText(ownerToken) ? hashToken(ownerToken) : null;
        Long kakaoUserId = null;
        if (StringUtils.hasText(kakaoConnectionId)) {
            kakaoUserId = kakaoAuthService.getConnection(kakaoConnectionId).getKakaoUserId();
        }
        return new TrackingAccessContext(ownerTokenHash, kakaoUserId);
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 해시를 초기화할 수 없습니다.", ex);
        }
    }

    private static final class TrackingAccessContext {
        private final String ownerTokenHash;
        private final Long kakaoUserId;

        private TrackingAccessContext(String ownerTokenHash, Long kakaoUserId) {
            this.ownerTokenHash = ownerTokenHash;
            this.kakaoUserId = kakaoUserId;
        }

        private boolean canAccess(Tracking tracking) {
            boolean ownerMatch = ownerTokenHash != null && ownerTokenHash.equals(tracking.getOwnerTokenHash());
            boolean kakaoMatch = kakaoUserId != null && kakaoUserId.equals(tracking.getKakaoUserId());
            return ownerMatch || kakaoMatch;
        }
    }
}
