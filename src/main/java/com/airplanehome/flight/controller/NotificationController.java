package com.airplanehome.flight.controller;

import com.airplanehome.flight.model.KakaoAuthConnection;
import com.airplanehome.flight.service.FlightService;
import com.airplanehome.flight.service.KakaoAuthService;
import com.airplanehome.flight.service.KakaoNotificationService;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    private static final String ADMIN_TOKEN_HEADER = "X-Admin-Token";

    private final FlightService flightService;
    private final KakaoNotificationService kakaoNotificationService;
    private final KakaoAuthService kakaoAuthService;
    private final String adminApiToken;

    public NotificationController(FlightService flightService,
                                  KakaoNotificationService kakaoNotificationService,
                                  KakaoAuthService kakaoAuthService,
                                  @Value("${admin.api.token:}") String adminApiToken) {
        this.flightService = flightService;
        this.kakaoNotificationService = kakaoNotificationService;
        this.kakaoAuthService = kakaoAuthService;
        this.adminApiToken = adminApiToken;
    }

    @GetMapping("/kakao/status")
    public Map<String, Object> getKakaoStatus() {
        return kakaoNotificationService.status();
    }

    @GetMapping("/kakao/auth/status")
    public Map<String, Object> getKakaoAuthStatus() {
        return kakaoAuthService.status();
    }

    @GetMapping("/kakao/auth/start")
    public Map<String, Object> startKakaoAuth() {
        return kakaoAuthService.authStart();
    }

    @GetMapping("/kakao/auth/callback")
    public Map<String, Object> completeKakaoAuth(@RequestParam String code,
                                                 @RequestParam(required = false) String state) {
        KakaoAuthConnection connection = kakaoAuthService.createConnection(code);
        Map<String, Object> response = new LinkedHashMap<String, Object>();
        response.put("connectionId", connection.getId());
        response.put("kakaoUserId", connection.getKakaoUserId());
        response.put("nickname", connection.getNickname());
        response.put("state", state);
        return response;
    }

    @GetMapping("/kakao/auth/connections/{connectionId}")
    public Map<String, Object> getKakaoConnection(@PathVariable String connectionId) {
        KakaoAuthConnection connection = kakaoAuthService.getConnection(connectionId);
        Map<String, Object> response = new LinkedHashMap<String, Object>();
        response.put("connectionId", connection.getId());
        response.put("kakaoUserId", connection.getKakaoUserId());
        response.put("nickname", connection.getNickname());
        response.put("createdAt", connection.getCreatedAt());
        return response;
    }

    @GetMapping("/kakao/example")
    public Map<String, Object> getKakaoExample() {
        return kakaoNotificationService.examplePayload();
    }

    @GetMapping("/kakao/trackings/{trackingId}/preview")
    public Map<String, Object> previewTrackingNotification(@PathVariable Long trackingId,
                                                           @RequestParam int previousPrice,
                                                           @RequestParam int currentPrice) {
        return kakaoNotificationService.previewPayload(
                flightService.getTracking(trackingId),
                previousPrice,
                currentPrice);
    }

    @PostMapping("/kakao/trackings/{trackingId}/test")
    public Map<String, Object> sendTestTrackingNotification(@PathVariable Long trackingId,
                                                            @RequestHeader(value = ADMIN_TOKEN_HEADER, required = false) String token,
                                                            @RequestParam(required = false) Integer previousPrice,
                                                            @RequestParam(required = false) Integer currentPrice) {
        authorize(token);
        com.airplanehome.flight.model.Tracking tracking = flightService.getTracking(trackingId);
        int resolvedCurrentPrice = currentPrice == null ? toInt(tracking.getLastCheckedPrice(), 100000) : currentPrice.intValue();
        int resolvedPreviousPrice = previousPrice == null ? resolvedCurrentPrice + 10000 : previousPrice.intValue();
        boolean sent = kakaoNotificationService.sendAlimTalk(tracking, resolvedPreviousPrice, resolvedCurrentPrice);

        Map<String, Object> response = new LinkedHashMap<String, Object>();
        response.put("trackingId", tracking.getId());
        response.put("sent", Boolean.valueOf(sent));
        response.put("previousPrice", Integer.valueOf(resolvedPreviousPrice));
        response.put("currentPrice", Integer.valueOf(resolvedCurrentPrice));
        response.put("preview", kakaoNotificationService.previewPayload(tracking, resolvedPreviousPrice, resolvedCurrentPrice));
        return response;
    }

    private void authorize(String token) {
        if (!StringUtils.hasText(adminApiToken)) {
            throw new IllegalStateException("ADMIN_API_TOKEN이 설정되지 않아 관리자 알림 API를 사용할 수 없습니다.");
        }
        if (!adminApiToken.equals(token)) {
            throw new AdminCacheController.AdminUnauthorizedException("관리자 토큰이 올바르지 않습니다.");
        }
    }

    private int toInt(BigDecimal value, int fallback) {
        return value == null ? fallback : value.intValue();
    }
}
