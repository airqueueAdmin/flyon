package com.airplanehome.flight.repository;

import com.airplanehome.flight.model.FlightPrice;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FlightPriceRepository extends JpaRepository<FlightPrice, Long> {
    List<FlightPrice> findByPrefetchedAtAfter(LocalDateTime prefetchedAt);

    void deleteByOriginAndDestinationAndDepartureDate(String origin, String destination, LocalDate departureDate);

    List<FlightPrice> findByOriginAndDestinationAndDepartureDateOrderByPriceAsc(
            String origin,
            String destination,
            LocalDate departureDate);

    List<FlightPrice> findByOriginAndDestinationAndDepartureDateBetweenOrderByDepartureDateAscPriceAsc(
            String origin,
            String destination,
            LocalDate departureDateFrom,
            LocalDate departureDateTo);
}
