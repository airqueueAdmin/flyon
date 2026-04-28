package com.airplanehome.flight.controller;

import com.airplanehome.flight.service.FlightService;
import com.airplanehome.flight.service.KakaoNotificationService;
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

    public NotificationController(FlightService flightService,
                                  KakaoNotificationService kakaoNotificationService) {
        this.flightService = flightService;
        this.kakaoNotificationService = kakaoNotificationService;
    }

    @GetMapping("/kakao/status")
    public Map<String, Object> getKakaoStatus() {
        return kakaoNotificationService.status();
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
