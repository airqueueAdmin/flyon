package com.airplanehome.flight.client;

import com.airplanehome.flight.model.FlightPrice;
import java.util.List;

public interface FlightProvider {
    List<FlightPrice> search(String origin, String destination, String departureDate);
}
