package com.airplanehome.flight.repository;

import com.airplanehome.flight.model.FlightPrice;
import com.airplanehome.flight.model.TripType;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FlightPriceRepository extends JpaRepository<FlightPrice, Long> {
    List<FlightPrice> findByPrefetchedAtAfter(LocalDateTime prefetchedAt);

    void deleteByTripTypeAndOriginAndDestinationAndDepartureDateAndReturnDateIsNull(TripType tripType,
                                                                                     String origin,
                                                                                     String destination,
                                                                                     LocalDate departureDate);

    void deleteByTripTypeAndOriginAndDestinationAndDepartureDateAndReturnDate(TripType tripType,
                                                                              String origin,
                                                                              String destination,
                                                                              LocalDate departureDate,
                                                                              LocalDate returnDate);

    List<FlightPrice> findByTripTypeAndOriginAndDestinationAndDepartureDateAndReturnDateIsNullOrderByPriceAsc(TripType tripType,
                                                                                                               String origin,
                                                                                                               String destination,
                                                                                                               LocalDate departureDate);

    List<FlightPrice> findByTripTypeAndOriginAndDestinationAndDepartureDateAndReturnDateOrderByPriceAsc(TripType tripType,
                                                                                                         String origin,
                                                                                                         String destination,
                                                                                                         LocalDate departureDate,
                                                                                                         LocalDate returnDate);

    List<FlightPrice> findByTripTypeAndOriginAndDestinationAndDepartureDateBetweenAndReturnDateIsNullOrderByDepartureDateAscPriceAsc(TripType tripType,
                                                                                                                                      String origin,
                                                                                                                                      String destination,
                                                                                                                                      LocalDate departureDateFrom,
                                                                                                                                      LocalDate departureDateTo);

    List<FlightPrice> findByTripTypeAndOriginAndDestinationAndDepartureDateBetweenAndReturnDateBetweenOrderByDepartureDateAscPriceAsc(TripType tripType,
                                                                                                                                       String origin,
                                                                                                                                       String destination,
                                                                                                                                       LocalDate departureDateFrom,
                                                                                                                                       LocalDate departureDateTo,
                                                                                                                                       LocalDate returnDateFrom,
                                                                                                                                       LocalDate returnDateTo);

    List<FlightPrice> findByTripTypeAndOriginAndDepartureDateBetweenAndReturnDateIsNullOrderByPriceAsc(TripType tripType,
                                                                                                        String origin,
                                                                                                        LocalDate departureDateFrom,
                                                                                                        LocalDate departureDateTo);
}
