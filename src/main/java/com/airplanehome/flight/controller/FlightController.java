package com.airplanehome.flight.controller;

import com.airplanehome.flight.controller.dto.FlightSearchRequest;
import com.airplanehome.flight.controller.dto.TrackingRequest;
import com.airplanehome.flight.model.FlightPrice;
import com.airplanehome.flight.model.Tracking;
import com.airplanehome.flight.service.FlightService;
import java.util.List;
import javax.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class FlightController {
    private final FlightService flightService;

    public FlightController(FlightService flightService) {
        this.flightService = flightService;
    }

    @PostMapping("/flights/search")
    public List<FlightPrice> searchLowestPrice(@Valid @RequestBody FlightSearchRequest request) {
        return flightService.searchLowestPrice(request);
    }

    @PostMapping("/trackings")
    @ResponseStatus(HttpStatus.CREATED)
    public Tracking createTracking(@Valid @RequestBody TrackingRequest request) {
        return flightService.createTracking(request);
    }

    @GetMapping("/trackings")
    public List<Tracking> getTrackings() {
        return flightService.getTrackings();
    }

    @GetMapping("/trackings/{id}")
    public Tracking getTracking(@PathVariable Long id) {
        return flightService.getTracking(id);
    }

    @DeleteMapping("/trackings/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTracking(@PathVariable Long id) {
        flightService.deleteTracking(id);
    }
}
