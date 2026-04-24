package com.airplanehome.flight.client;

import com.airplanehome.flight.model.FlightPrice;
import com.airplanehome.flight.model.TripType;
import java.time.LocalDate;
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
    public List<FlightPrice> search(TripType tripType,
                                    String origin,
                                    String destination,
                                    LocalDate departureDate,
                                    LocalDate returnDate,
                                    Integer adults) {
        if (rapidApiClient.isCircuitOpen()) {
            log.warn("RapidAPI provider skipped because circuit breaker is open");
            return Collections.emptyList();
        }
        return rapidApiClient.searchFlights(tripType, origin, destination, departureDate, returnDate, adults);
    }
}
