package com.airplanehome.flight.controller;

import com.airplanehome.flight.model.KakaoAuthConnection;
import com.airplanehome.flight.service.FlightService;
import com.airplanehome.flight.service.KakaoAuthService;
import com.airplanehome.flight.service.KakaoNotificationService;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    private final FlightService flightService;
    private final KakaoNotificationService kakaoNotificationService;
    private final KakaoAuthService kakaoAuthService;

    public NotificationController(FlightService flightService,
                                  KakaoNotificationService kakaoNotificationService,
                                  KakaoAuthService kakaoAuthService) {
        this.flightService = flightService;
        this.kakaoNotificationService = kakaoNotificationService;
        this.kakaoAuthService = kakaoAuthService;
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
}
