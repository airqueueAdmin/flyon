package com.airplanehome.flight.service;

import com.airplanehome.flight.client.FlightProvider;
import com.airplanehome.flight.model.FlightPrice;
import com.airplanehome.flight.model.TripType;
import com.airplanehome.flight.repository.FlightPriceRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class FlightPrefetchServiceTest {

    @Test
    void shouldReturnExactDbResultAndPopulateCacheOnCacheMiss() {
        FlightProvider primaryFlightProvider = mock(FlightProvider.class);
        FlightProvider fallbackFlightProvider = mock(FlightProvider.class);
        FlightPriceRepository flightPriceRepository = mock(FlightPriceRepository.class);
        SharedFlightCacheService sharedFlightCacheService = new SharedFlightCacheService();
        FlightPrefetchService service = new FlightPrefetchService(
                primaryFlightProvider,
                fallbackFlightProvider,
                sharedFlightCacheService,
                flightPriceRepository,
                "ICN|NRT",
                7,
                "0,3",
                "2,3,5,7",
                5,
                2,
                6);

        LocalDate departureDate = LocalDate.of(2026, 4, 20);
        FlightPrice dbFlight = flight(TripType.ONE_WAY, "ICN", "NRT", departureDate, null, 270000, false, departureDate);

        given(flightPriceRepository.findByTripTypeAndOriginAndDestinationAndDepartureDateAndReturnDateIsNullOrderByPriceAsc(
                TripType.ONE_WAY, "ICN", "NRT", departureDate))
                .willReturn(Collections.singletonList(dbFlight));

        List<FlightPrice> result = service.getCachedFlights("ICN", "NRT", departureDate);

        assertEquals(1, result.size());
        assertEquals(BigDecimal.valueOf(270000), result.get(0).getPrice());
        assertFalse(sharedFlightCacheService.getFresh(TripType.ONE_WAY, "ICN", "NRT", departureDate, null).isEmpty());
    }

    @Test
    void shouldReturnNearestDbResultWhenExactDateMissing() {
        FlightProvider primaryFlightProvider = mock(FlightProvider.class);
        FlightProvider fallbackFlightProvider = mock(FlightProvider.class);
        FlightPriceRepository flightPriceRepository = mock(FlightPriceRepository.class);
        SharedFlightCacheService sharedFlightCacheService = new SharedFlightCacheService();
        FlightPrefetchService service = new FlightPrefetchService(
                primaryFlightProvider,
                fallbackFlightProvider,
                sharedFlightCacheService,
                flightPriceRepository,
                "ICN|NRT",
                7,
                "0,3",
                "2,3,5,7",
                5,
                2,
                6);

        LocalDate requestedDate = LocalDate.of(2026, 4, 20);
        LocalDate nearestDate = requestedDate.plusDays(1);
        FlightPrice morning = flight(TripType.ONE_WAY, "ICN", "NRT", nearestDate, null, 272000, true, requestedDate);
        FlightPrice evening = flight(TripType.ONE_WAY, "ICN", "NRT", nearestDate, null, 262000, true, requestedDate);

        given(flightPriceRepository.findByTripTypeAndOriginAndDestinationAndDepartureDateAndReturnDateIsNullOrderByPriceAsc(
                TripType.ONE_WAY, "ICN", "NRT", requestedDate))
                .willReturn(Collections.<FlightPrice>emptyList());
        given(flightPriceRepository.findByTripTypeAndOriginAndDestinationAndDepartureDateBetweenAndReturnDateIsNullOrderByDepartureDateAscPriceAsc(
                TripType.ONE_WAY,
                "ICN",
                "NRT",
                requestedDate.minusDays(2),
                requestedDate.plusDays(2)))
                .willReturn(Arrays.asList(morning, evening));

        List<FlightPrice> result = service.getCachedFlights("ICN", "NRT", requestedDate);

        assertEquals(2, result.size());
        assertEquals(nearestDate, result.get(0).getDepartureDate());
        assertEquals(nearestDate, result.get(1).getDepartureDate());
        assertFalse(sharedFlightCacheService.getFresh(TripType.ONE_WAY, "ICN", "NRT", nearestDate, null).isEmpty());
    }

    @Test
    void shouldUseFallbackProviderWhenPrimaryReturnsEmpty() {
        FlightProvider primaryFlightProvider = mock(FlightProvider.class);
        FlightProvider fallbackFlightProvider = mock(FlightProvider.class);
        FlightPriceRepository flightPriceRepository = mock(FlightPriceRepository.class);
        SharedFlightCacheService sharedFlightCacheService = new SharedFlightCacheService();
        FlightPrefetchService service = new FlightPrefetchService(
                primaryFlightProvider,
                fallbackFlightProvider,
                sharedFlightCacheService,
                flightPriceRepository,
                "ICN|NRT",
                7,
                "0",
                "2,3,5,7",
                5,
                2,
                6);

        LocalDate departureDate = LocalDate.of(2026, 4, 20);
        given(primaryFlightProvider.search(TripType.ONE_WAY, "ICN", "NRT", departureDate, null, null))
                .willReturn(Collections.<FlightPrice>emptyList());
        given(fallbackFlightProvider.search(TripType.ONE_WAY, "ICN", "NRT", departureDate, null, null))
                .willReturn(Collections.singletonList(flight(TripType.ONE_WAY, "ICN", "NRT", departureDate, null, 250000, false, departureDate)));

        service.refresh("ICN", "NRT", departureDate);

        verify(primaryFlightProvider).search(TripType.ONE_WAY, "ICN", "NRT", departureDate, null, null);
        verify(fallbackFlightProvider).search(TripType.ONE_WAY, "ICN", "NRT", departureDate, null, null);
    }

    @Test
    void shouldNotUseFallbackProviderWhenPrimarySucceeds() {
        FlightProvider primaryFlightProvider = mock(FlightProvider.class);
        FlightProvider fallbackFlightProvider = mock(FlightProvider.class);
        FlightPriceRepository flightPriceRepository = mock(FlightPriceRepository.class);
        SharedFlightCacheService sharedFlightCacheService = new SharedFlightCacheService();
        FlightPrefetchService service = new FlightPrefetchService(
                primaryFlightProvider,
                fallbackFlightProvider,
                sharedFlightCacheService,
                flightPriceRepository,
                "ICN|NRT",
                7,
                "0",
                "2,3,5,7",
                5,
                2,
                6);

        LocalDate departureDate = LocalDate.of(2026, 4, 20);
        given(primaryFlightProvider.search(TripType.ONE_WAY, "ICN", "NRT", departureDate, null, null))
                .willReturn(Collections.singletonList(flight(TripType.ONE_WAY, "ICN", "NRT", departureDate, null, 250000, false, departureDate)));

        service.refresh("ICN", "NRT", departureDate);

        verify(primaryFlightProvider).search(TripType.ONE_WAY, "ICN", "NRT", departureDate, null, null);
        verify(fallbackFlightProvider, never()).search(any(), eq("ICN"), eq("NRT"), eq(departureDate), eq(null), eq(null));
    }

    @Test
    void shouldFetchOnDemandAndPopulateCacheOnColdMiss() {
        FlightProvider primaryFlightProvider = mock(FlightProvider.class);
        FlightProvider fallbackFlightProvider = mock(FlightProvider.class);
        FlightPriceRepository flightPriceRepository = mock(FlightPriceRepository.class);
        SharedFlightCacheService sharedFlightCacheService = new SharedFlightCacheService();
        FlightPrefetchService service = new FlightPrefetchService(
                primaryFlightProvider,
                fallbackFlightProvider,
                sharedFlightCacheService,
                flightPriceRepository,
                "ICN|NRT",
                7,
                "0",
                "2,3,5,7",
                5,
                2,
                6);

        LocalDate departureDate = LocalDate.of(2026, 4, 20);
        given(flightPriceRepository.findByTripTypeAndOriginAndDestinationAndDepartureDateAndReturnDateIsNullOrderByPriceAsc(
                TripType.ONE_WAY, "ICN", "NRT", departureDate))
                .willReturn(Collections.<FlightPrice>emptyList());
        given(flightPriceRepository.findByTripTypeAndOriginAndDestinationAndDepartureDateBetweenAndReturnDateIsNullOrderByDepartureDateAscPriceAsc(
                TripType.ONE_WAY,
                "ICN",
                "NRT",
                departureDate.minusDays(2),
                departureDate.plusDays(2)))
                .willReturn(Collections.<FlightPrice>emptyList());
        given(primaryFlightProvider.search(TripType.ONE_WAY, "ICN", "NRT", departureDate, null, 1))
                .willReturn(Collections.singletonList(flight(TripType.ONE_WAY, "ICN", "NRT", departureDate, null, 250000, false, departureDate)));

        List<FlightPrice> result = service.getCachedOrFetchFlights(TripType.ONE_WAY, "ICN", "NRT", departureDate, null, 1);

        assertEquals(3, result.size());
        verify(primaryFlightProvider).search(TripType.ONE_WAY, "ICN", "NRT", departureDate, null, 1);
        verify(fallbackFlightProvider, never()).search(any(), eq("ICN"), eq("NRT"), eq(departureDate), eq(null), eq(1));
        assertFalse(sharedFlightCacheService.getFresh(TripType.ONE_WAY, "ICN", "NRT", departureDate, null).isEmpty());
    }

    @Test
    void shouldUseRoundTripCacheKey() {
        SharedFlightCacheService cache = new SharedFlightCacheService();
        String oneWayKey = cache.buildKey(TripType.ONE_WAY, "ICN", "NRT", LocalDate.of(2026, 6, 1), null);
        String roundTripKey = cache.buildKey(TripType.ROUND_TRIP, "ICN", "NRT", LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 5));

        assertEquals("ONE_WAY|ICN|NRT|2026-06-01", oneWayKey);
        assertEquals("ROUND_TRIP|ICN|NRT|2026-06-01|2026-06-05", roundTripKey);
    }

    @Test
    void shouldFetchRoundTripFlightsWithMatchingReturnDate() {
        FlightProvider primaryFlightProvider = mock(FlightProvider.class);
        FlightProvider fallbackFlightProvider = mock(FlightProvider.class);
        FlightPriceRepository flightPriceRepository = mock(FlightPriceRepository.class);
        SharedFlightCacheService sharedFlightCacheService = new SharedFlightCacheService();
        FlightPrefetchService service = new FlightPrefetchService(
                primaryFlightProvider,
                fallbackFlightProvider,
                sharedFlightCacheService,
                flightPriceRepository,
                "ICN|NRT",
                7,
                "0",
                "2,3,5,7",
                5,
                2,
                6);

        LocalDate departureDate = LocalDate.of(2026, 4, 20);
        LocalDate returnDate = LocalDate.of(2026, 4, 25);
        given(flightPriceRepository.findByTripTypeAndOriginAndDestinationAndDepartureDateAndReturnDateOrderByPriceAsc(
                TripType.ROUND_TRIP, "ICN", "NRT", departureDate, returnDate))
                .willReturn(Collections.<FlightPrice>emptyList());
        given(flightPriceRepository.findByTripTypeAndOriginAndDestinationAndDepartureDateBetweenAndReturnDateBetweenOrderByDepartureDateAscPriceAsc(
                TripType.ROUND_TRIP,
                "ICN",
                "NRT",
                departureDate.minusDays(2),
                departureDate.plusDays(2),
                returnDate.minusDays(2),
                returnDate.plusDays(2)))
                .willReturn(Collections.<FlightPrice>emptyList());
        given(primaryFlightProvider.search(TripType.ROUND_TRIP, "ICN", "NRT", departureDate, returnDate, 1))
                .willReturn(Collections.singletonList(flight(TripType.ROUND_TRIP, "ICN", "NRT", departureDate, returnDate, 400000, false, departureDate)));

        List<FlightPrice> result = service.getCachedOrFetchFlights(TripType.ROUND_TRIP, "ICN", "NRT", departureDate, returnDate, 1);

        assertEquals(3, result.size());
        assertEquals(TripType.ROUND_TRIP, result.get(0).getTripType());
        assertEquals(returnDate, result.get(0).getReturnDate());
        verify(primaryFlightProvider).search(TripType.ROUND_TRIP, "ICN", "NRT", departureDate, returnDate, 1);
    }

    @Test
    void shouldFilterDuffelAirwaysFromFallbackProviderBeforePersisting() {
        FlightProvider primaryFlightProvider = mock(FlightProvider.class);
        FlightProvider fallbackFlightProvider = mock(FlightProvider.class);
        FlightPriceRepository flightPriceRepository = mock(FlightPriceRepository.class);
        SharedFlightCacheService sharedFlightCacheService = new SharedFlightCacheService();
        FlightPrefetchService service = new FlightPrefetchService(
                primaryFlightProvider,
                fallbackFlightProvider,
                sharedFlightCacheService,
                flightPriceRepository,
                "ICN|NRT",
                7,
                "0",
                "2,3,5,7",
                5,
                2,
                6);

        LocalDate departureDate = LocalDate.of(2026, 4, 20);
        given(primaryFlightProvider.search(TripType.ONE_WAY, "ICN", "NRT", departureDate, null, null)).willReturn(Collections.<FlightPrice>emptyList());
        given(fallbackFlightProvider.search(TripType.ONE_WAY, "ICN", "NRT", departureDate, null, null))
                .willReturn(Arrays.asList(
                        flight(TripType.ONE_WAY, "ICN", "NRT", departureDate, null, 250000, false, departureDate, "Duffel Airways"),
                        flight(TripType.ONE_WAY, "ICN", "NRT", departureDate, null, 260000, false, departureDate, "Korean Air")));

        service.refresh("ICN", "NRT", departureDate);

        List<FlightPrice> result = sharedFlightCacheService.getFresh(TripType.ONE_WAY, "ICN", "NRT", departureDate, null);
        assertEquals(3, result.size());
        assertTrue(result.stream().allMatch(flight -> "Korean Air".equals(flight.getAirline())));
    }

    private FlightPrice flight(TripType tripType,
                               String origin,
                               String destination,
                               LocalDate departureDate,
                               LocalDate returnDate,
                               int price,
                               boolean approximate,
                               LocalDate sourceDepartureDate) {
        return flight(tripType, origin, destination, departureDate, returnDate, price, approximate, sourceDepartureDate, "Korean Air");
    }

    private FlightPrice flight(TripType tripType,
                               String origin,
                               String destination,
                               LocalDate departureDate,
                               LocalDate returnDate,
                               int price,
                               boolean approximate,
                               LocalDate sourceDepartureDate,
                               String airline) {
        FlightPrice flightPrice = new FlightPrice();
        flightPrice.setTripType(tripType);
        flightPrice.setOrigin(origin);
        flightPrice.setDestination(destination);
        flightPrice.setDepartureDate(departureDate);
        flightPrice.setReturnDate(returnDate);
        flightPrice.setPrice(BigDecimal.valueOf(price));
        flightPrice.setTotalPrice(BigDecimal.valueOf(price));
        flightPrice.setCurrency("KRW");
        flightPrice.setAirline(airline);
        flightPrice.setOutboundAirline(airline);
        if (tripType == TripType.ROUND_TRIP) {
            flightPrice.setInboundAirline("Asiana Airlines");
        }
        flightPrice.setApproximate(Boolean.valueOf(approximate));
        flightPrice.setSourceDepartureDate(sourceDepartureDate);
        return flightPrice;
    }
}
