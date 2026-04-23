package com.airplanehome.flight.controller;

import com.airplanehome.flight.service.KakaoNotificationService;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    private final KakaoNotificationService kakaoNotificationService;

    public NotificationController(KakaoNotificationService kakaoNotificationService) {
        this.kakaoNotificationService = kakaoNotificationService;
    }

    @GetMapping("/kakao/example")
    public Map<String, Object> getKakaoExample() {
        return kakaoNotificationService.examplePayload();
    }
}
