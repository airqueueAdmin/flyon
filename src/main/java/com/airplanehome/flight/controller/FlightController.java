package com.airplanehome.flight.controller;

import com.airplanehome.flight.controller.dto.DailyPriceDto;
import com.airplanehome.flight.controller.dto.DealDto;
import com.airplanehome.flight.controller.dto.FlightSearchRequest;
import com.airplanehome.flight.controller.dto.KakaoLinkRequest;
import com.airplanehome.flight.controller.dto.TrackingRequest;
import com.airplanehome.flight.model.FlightPrice;
import com.airplanehome.flight.model.PriceHistory;
import com.airplanehome.flight.model.Tracking;
import com.airplanehome.flight.service.ExchangeRateService;
import com.airplanehome.flight.service.FlightService;
import java.util.List;
import java.util.Map;
import javax.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class FlightController {
    private static final String OWNER_TOKEN_HEADER = "X-Tracking-Owner-Token";
    private static final String KAKAO_CONNECTION_HEADER = "X-Kakao-Connection-Id";

    private final FlightService flightService;
    private final ExchangeRateService exchangeRateService;

    public FlightController(FlightService flightService, ExchangeRateService exchangeRateService) {
        this.flightService = flightService;
        this.exchangeRateService = exchangeRateService;
    }

    @PostMapping("/flights/search")
    public List<FlightPrice> searchLowestPrice(@Valid @RequestBody FlightSearchRequest request) {
        return flightService.searchLowestPrice(request);
    }

    @PostMapping("/trackings")
    @ResponseStatus(HttpStatus.CREATED)
    public Tracking createTracking(@Valid @RequestBody TrackingRequest request) {
        return flightService.createTracking(request, request.getOwnerToken());
    }

    @GetMapping("/trackings")
    public List<Tracking> getTrackings(@RequestHeader(value = OWNER_TOKEN_HEADER, required = false) String ownerToken,
                                       @RequestHeader(value = KAKAO_CONNECTION_HEADER, required = false) String kakaoConnectionId) {
        return flightService.getTrackings(ownerToken, kakaoConnectionId);
    }

    @GetMapping("/trackings/{id}")
    public Tracking getTracking(@PathVariable Long id,
                                @RequestHeader(value = OWNER_TOKEN_HEADER, required = false) String ownerToken,
                                @RequestHeader(value = KAKAO_CONNECTION_HEADER, required = false) String kakaoConnectionId) {
        return flightService.getTracking(id, ownerToken, kakaoConnectionId);
    }

    @PatchMapping("/trackings/{id}/kakao")
    public Tracking linkKakao(@PathVariable Long id,
                               @RequestBody KakaoLinkRequest request,
                               @RequestHeader(value = OWNER_TOKEN_HEADER, required = false) String ownerToken,
                               @RequestHeader(value = KAKAO_CONNECTION_HEADER, required = false) String kakaoConnectionId) {
        return flightService.linkKakao(id, ownerToken, kakaoConnectionId, request);
    }

    @DeleteMapping("/trackings/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTracking(@PathVariable Long id,
                               @RequestHeader(value = OWNER_TOKEN_HEADER, required = false) String ownerToken,
                               @RequestHeader(value = KAKAO_CONNECTION_HEADER, required = false) String kakaoConnectionId) {
        flightService.deleteTracking(id, ownerToken, kakaoConnectionId);
    }

    @GetMapping("/trackings/{id}/history")
    public List<PriceHistory> getTrackingHistory(@PathVariable Long id,
                                                  @RequestHeader(value = OWNER_TOKEN_HEADER, required = false) String ownerToken,
                                                  @RequestHeader(value = KAKAO_CONNECTION_HEADER, required = false) String kakaoConnectionId) {
        return flightService.getTrackingHistory(id, ownerToken, kakaoConnectionId);
    }

    @GetMapping("/flights/calendar")
    public List<DailyPriceDto> getCalendar(@RequestParam String origin,
                                            @RequestParam String destination) {
        return flightService.getCalendar(origin, destination);
    }

    @GetMapping("/flights/deals")
    public List<DealDto> getDeals(@RequestParam(defaultValue = "ICN") String origin) {
        return flightService.getDeals(origin);
    }

    @GetMapping("/exchange-rates")
    public Map<String, Object> getExchangeRates() {
        return exchangeRateService.getDisplayRates();
    }
}
