package com.airplanehome.flight.controller;

import com.airplanehome.flight.controller.dto.AdminCacheRequest;
import com.airplanehome.flight.model.TripType;
import com.airplanehome.flight.service.FlightPrefetchService;
import java.util.Collections;
import java.util.Map;
import javax.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/cache")
public class AdminCacheController {
    private static final String ADMIN_TOKEN_HEADER = "X-Admin-Token";

    private final FlightPrefetchService flightPrefetchService;
    private final String adminApiToken;

    public AdminCacheController(FlightPrefetchService flightPrefetchService,
                                @Value("${admin.api.token:}") String adminApiToken) {
        this.flightPrefetchService = flightPrefetchService;
        this.adminApiToken = adminApiToken;
    }

    @PostMapping("/evict")
    public Map<String, String> evict(@RequestHeader(value = ADMIN_TOKEN_HEADER, required = false) String token,
                                     @Valid @RequestBody AdminCacheRequest request) {
        authorize(token);
        TripType tripType = resolveTripType(request.getTripType(), request.getReturnDate());
        flightPrefetchService.evictCache(tripType,
                normalizeCode(request.getOrigin()),
                normalizeCode(request.getDestination()),
                request.getDepartureDate(),
                normalizeReturnDate(tripType, request.getDepartureDate(), request.getReturnDate()));
        return Collections.singletonMap("message", "캐시 항목을 삭제했습니다.");
    }

    @PostMapping("/refresh")
    public Map<String, String> refresh(@RequestHeader(value = ADMIN_TOKEN_HEADER, required = false) String token,
                                       @Valid @RequestBody AdminCacheRequest request) {
        authorize(token);
        TripType tripType = resolveTripType(request.getTripType(), request.getReturnDate());
        flightPrefetchService.refresh(tripType,
                normalizeCode(request.getOrigin()),
                normalizeCode(request.getDestination()),
                request.getDepartureDate(),
                normalizeReturnDate(tripType, request.getDepartureDate(), request.getReturnDate()));
        return Collections.singletonMap("message", "캐시 항목을 새로고침했습니다.");
    }

    @PostMapping("/clear")
    public Map<String, String> clear(@RequestHeader(value = ADMIN_TOKEN_HEADER, required = false) String token) {
        authorize(token);
        flightPrefetchService.clearCache();
        return Collections.singletonMap("message", "캐시를 모두 비웠습니다.");
    }

    private void authorize(String token) {
        if (!StringUtils.hasText(adminApiToken)) {
            throw new IllegalStateException("ADMIN_API_TOKEN이 설정되지 않아 관리자 캐시 API를 사용할 수 없습니다.");
        }
        if (!adminApiToken.equals(token)) {
            throw new AdminUnauthorizedException("관리자 토큰이 올바르지 않습니다.");
        }
    }

    private String normalizeCode(String value) {
        return value.trim().toUpperCase();
    }

    private TripType resolveTripType(TripType tripType, java.time.LocalDate returnDate) {
        if (tripType != null) {
            return tripType;
        }
        return returnDate == null ? TripType.ONE_WAY : TripType.ROUND_TRIP;
    }

    private java.time.LocalDate normalizeReturnDate(TripType tripType,
                                                    java.time.LocalDate departureDate,
                                                    java.time.LocalDate returnDate) {
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

    static final class AdminUnauthorizedException extends RuntimeException {
        AdminUnauthorizedException(String message) {
            super(message);
        }
    }
}
