package com.airplanehome.flight.service;

import com.airplanehome.flight.client.FlightProvider;
import com.airplanehome.flight.model.FlightPrice;
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
import static org.mockito.ArgumentMatchers.anyString;
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
                5,
                2,
                6);

        LocalDate departureDate = LocalDate.of(2026, 4, 20);
        FlightPrice dbFlight = flight("ICN", "NRT", departureDate, 270000, false, departureDate);

        given(flightPriceRepository.findByOriginAndDestinationAndDepartureDateOrderByPriceAsc("ICN", "NRT", departureDate))
                .willReturn(Collections.singletonList(dbFlight));

        List<FlightPrice> result = service.getCachedFlights("ICN", "NRT", departureDate);

        assertEquals(1, result.size());
        assertEquals(BigDecimal.valueOf(270000), result.get(0).getPrice());
        assertFalse(sharedFlightCacheService.getFresh("ICN", "NRT", departureDate).isEmpty());
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
                5,
                2,
                6);

        LocalDate requestedDate = LocalDate.of(2026, 4, 20);
        LocalDate nearestDate = requestedDate.plusDays(1);
        FlightPrice morning = flight("ICN", "NRT", nearestDate, 272000, true, requestedDate);
        FlightPrice evening = flight("ICN", "NRT", nearestDate, 262000, true, requestedDate);

        given(flightPriceRepository.findByOriginAndDestinationAndDepartureDateOrderByPriceAsc("ICN", "NRT", requestedDate))
                .willReturn(Collections.emptyList());
        given(flightPriceRepository.findByOriginAndDestinationAndDepartureDateBetweenOrderByDepartureDateAscPriceAsc(
                "ICN",
                "NRT",
                requestedDate.minusDays(2),
                requestedDate.plusDays(2)))
                .willReturn(Arrays.asList(morning, evening));

        List<FlightPrice> result = service.getCachedFlights("ICN", "NRT", requestedDate);

        assertEquals(2, result.size());
        assertEquals(nearestDate, result.get(0).getDepartureDate());
        assertEquals(nearestDate, result.get(1).getDepartureDate());
        assertFalse(sharedFlightCacheService.getFresh("ICN", "NRT", nearestDate).isEmpty());
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
                5,
                2,
                6);

        LocalDate departureDate = LocalDate.of(2026, 4, 20);
        given(primaryFlightProvider.search("ICN", "NRT", "2026-04-20")).willReturn(Collections.<FlightPrice>emptyList());
        given(fallbackFlightProvider.search("ICN", "NRT", "2026-04-20"))
                .willReturn(Collections.singletonList(flight("ICN", "NRT", departureDate, 250000, false, departureDate)));

        service.refresh("ICN", "NRT", departureDate);

        verify(primaryFlightProvider).search("ICN", "NRT", "2026-04-20");
        verify(fallbackFlightProvider).search("ICN", "NRT", "2026-04-20");
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
                5,
                2,
                6);

        LocalDate departureDate = LocalDate.of(2026, 4, 20);
        given(primaryFlightProvider.search("ICN", "NRT", "2026-04-20"))
                .willReturn(Collections.singletonList(flight("ICN", "NRT", departureDate, 250000, false, departureDate)));

        service.refresh("ICN", "NRT", departureDate);

        verify(primaryFlightProvider).search("ICN", "NRT", "2026-04-20");
        verify(fallbackFlightProvider, never()).search(anyString(), anyString(), anyString());
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
                5,
                2,
                6);

        LocalDate departureDate = LocalDate.of(2026, 4, 20);
        given(flightPriceRepository.findByOriginAndDestinationAndDepartureDateOrderByPriceAsc("ICN", "NRT", departureDate))
                .willReturn(Collections.<FlightPrice>emptyList());
        given(flightPriceRepository.findByOriginAndDestinationAndDepartureDateBetweenOrderByDepartureDateAscPriceAsc(
                "ICN",
                "NRT",
                departureDate.minusDays(2),
                departureDate.plusDays(2)))
                .willReturn(Collections.<FlightPrice>emptyList());
        given(primaryFlightProvider.search("ICN", "NRT", "2026-04-20"))
                .willReturn(Collections.singletonList(flight("ICN", "NRT", departureDate, 250000, false, departureDate)));

        List<FlightPrice> result = service.getCachedOrFetchFlights("ICN", "NRT", departureDate);

        assertEquals(3, result.size());
        verify(primaryFlightProvider).search("ICN", "NRT", "2026-04-20");
        verify(fallbackFlightProvider, never()).search(anyString(), anyString(), anyString());
        assertFalse(sharedFlightCacheService.getFresh("ICN", "NRT", departureDate).isEmpty());
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
                5,
                2,
                6);

        LocalDate departureDate = LocalDate.of(2026, 4, 20);
        given(primaryFlightProvider.search("ICN", "NRT", "2026-04-20")).willReturn(Collections.<FlightPrice>emptyList());
        given(fallbackFlightProvider.search("ICN", "NRT", "2026-04-20"))
                .willReturn(Arrays.asList(
                        flight("ICN", "NRT", departureDate, 250000, false, departureDate, "Duffel Airways"),
                        flight("ICN", "NRT", departureDate, 260000, false, departureDate, "Korean Air")));

        service.refresh("ICN", "NRT", departureDate);

        List<FlightPrice> result = sharedFlightCacheService.getFresh("ICN", "NRT", departureDate);
        assertEquals(3, result.size());
        assertTrue(result.stream().allMatch(flight -> "Korean Air".equals(flight.getAirline())));
    }

    @Test
    void shouldFilterDuffelAirwaysFromDbResults() {
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
                5,
                2,
                6);

        LocalDate departureDate = LocalDate.of(2026, 4, 20);
        given(flightPriceRepository.findByOriginAndDestinationAndDepartureDateOrderByPriceAsc("ICN", "NRT", departureDate))
                .willReturn(Arrays.asList(
                        flight("ICN", "NRT", departureDate, 240000, false, departureDate, "Duffel Airways"),
                        flight("ICN", "NRT", departureDate, 270000, false, departureDate, "Asiana Airlines")));

        List<FlightPrice> result = service.getCachedFlights("ICN", "NRT", departureDate);

        assertEquals(1, result.size());
        assertEquals("Asiana Airlines", result.get(0).getAirline());
    }

    private FlightPrice flight(String origin,
                               String destination,
                               LocalDate departureDate,
                               int price,
                               boolean approximate,
                               LocalDate sourceDepartureDate) {
        return flight(origin, destination, departureDate, price, approximate, sourceDepartureDate, "Korean Air");
    }

    private FlightPrice flight(String origin,
                               String destination,
                               LocalDate departureDate,
                               int price,
                               boolean approximate,
                               LocalDate sourceDepartureDate,
                               String airline) {
        FlightPrice flightPrice = new FlightPrice();
        flightPrice.setOrigin(origin);
        flightPrice.setDestination(destination);
        flightPrice.setDepartureDate(departureDate);
        flightPrice.setPrice(BigDecimal.valueOf(price));
        flightPrice.setCurrency("KRW");
        flightPrice.setAirline(airline);
        flightPrice.setApproximate(Boolean.valueOf(approximate));
        flightPrice.setSourceDepartureDate(sourceDepartureDate);
        return flightPrice;
    }
}
