package com.airplanehome.flight.controller;

import com.airplanehome.flight.controller.dto.AdminCacheRequest;
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
        flightPrefetchService.evictCache(normalizeCode(request.getOrigin()),
                normalizeCode(request.getDestination()),
                request.getDepartureDate());
        return Collections.singletonMap("message", "Cache entry evicted.");
    }

    @PostMapping("/refresh")
    public Map<String, String> refresh(@RequestHeader(value = ADMIN_TOKEN_HEADER, required = false) String token,
                                       @Valid @RequestBody AdminCacheRequest request) {
        authorize(token);
        flightPrefetchService.refresh(normalizeCode(request.getOrigin()),
                normalizeCode(request.getDestination()),
                request.getDepartureDate());
        return Collections.singletonMap("message", "Cache entry refreshed.");
    }

    @PostMapping("/clear")
    public Map<String, String> clear(@RequestHeader(value = ADMIN_TOKEN_HEADER, required = false) String token) {
        authorize(token);
        flightPrefetchService.clearCache();
        return Collections.singletonMap("message", "Cache cleared.");
    }

    private void authorize(String token) {
        if (!StringUtils.hasText(adminApiToken)) {
            throw new IllegalStateException("Admin cache API is disabled because ADMIN_API_TOKEN is not configured.");
        }
        if (!adminApiToken.equals(token)) {
            throw new AdminUnauthorizedException("Invalid admin token.");
        }
    }

    private String normalizeCode(String value) {
        return value.trim().toUpperCase();
    }

    static final class AdminUnauthorizedException extends RuntimeException {
        private AdminUnauthorizedException(String message) {
            super(message);
        }
    }
}
