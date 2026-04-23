package com.airplanehome.flight.client;

import com.airplanehome.flight.model.FlightPrice;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component("rapidApiProvider")
public class RapidApiProvider implements FlightProvider {
    private static final Logger log = LoggerFactory.getLogger(RapidApiProvider.class);

    private final RapidApiClient rapidApiClient;

    public RapidApiProvider(RapidApiClient rapidApiClient) {
        this.rapidApiClient = rapidApiClient;
    }

    @Override
    public List<FlightPrice> search(String origin, String destination, String departureDate) {
        if (rapidApiClient.isCircuitOpen()) {
            log.warn("RapidAPI provider skipped because circuit breaker is open");
            return Collections.emptyList();
        }
        try {
            return rapidApiClient.searchFlights(origin, destination, LocalDate.parse(departureDate), null, null);
        } catch (DateTimeParseException exception) {
            log.warn("RapidAPI provider rejected invalid departureDate={}", departureDate);
            return Collections.emptyList();
        }
    }
}
