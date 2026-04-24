package com.airplanehome.flight.client;

import com.airplanehome.flight.model.FlightPrice;
import com.airplanehome.flight.model.TripType;
import java.time.LocalDate;
import java.util.List;

public interface FlightProvider {
    List<FlightPrice> search(TripType tripType,
                             String origin,
                             String destination,
                             LocalDate departureDate,
                             LocalDate returnDate,
                             Integer adults);

    default List<FlightPrice> search(String origin, String destination, String departureDate) {
        return search(TripType.ONE_WAY, origin, destination, LocalDate.parse(departureDate), null, null);
    }
}
