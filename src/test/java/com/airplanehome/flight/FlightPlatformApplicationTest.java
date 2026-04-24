package com.airplanehome.flight;

import com.airplanehome.flight.client.RapidApiClient;
import com.airplanehome.flight.model.FlightPrice;
import com.airplanehome.flight.model.TripType;
import com.airplanehome.flight.service.ExchangeRateService;
import com.airplanehome.flight.service.FlightPrefetchService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "admin.api.token=test-admin-token",
        "spring.datasource.url=jdbc:h2:mem:flight-platform-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password="
})
@AutoConfigureMockMvc
class FlightPlatformApplicationTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RapidApiClient rapidApiClient;

    @MockBean
    private ExchangeRateService exchangeRateService;

    @MockBean
    private FlightPrefetchService flightPrefetchService;

    @Test
    void shouldSearchFlightPrice() throws Exception {
        given(flightPrefetchService.isSupported(TripType.ONE_WAY, "ICN", "SFO", LocalDate.of(2026, 6, 1), null)).willReturn(true);
        given(flightPrefetchService.getCachedOrFetchFlights(TripType.ONE_WAY, "ICN", "SFO", LocalDate.of(2026, 6, 1), null, 1))
                .willReturn(sampleOneWayFlights());
        given(exchangeRateService.getUsdToKrwRate()).willReturn(1300d);

        mockMvc.perform(post("/api/flights/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"origin\":\"ICN\",\"destination\":\"SFO\",\"departureDate\":\"2026-06-01\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].provider").value("api.test"))
                .andExpect(jsonPath("$[0].price").value(416000))
                .andExpect(jsonPath("$[0].currency").value("KRW"))
                .andExpect(jsonPath("$[0].tripType").value("ONE_WAY"));
    }

    @Test
    void shouldSearchRoundTripFlightPrice() throws Exception {
        given(flightPrefetchService.isSupported(TripType.ROUND_TRIP, "ICN", "NRT", LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 5))).willReturn(true);
        given(flightPrefetchService.getCachedOrFetchFlights(
                TripType.ROUND_TRIP,
                "ICN",
                "NRT",
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 5),
                2)).willReturn(sampleRoundTripFlights());

        mockMvc.perform(post("/api/flights/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tripType\":\"ROUND_TRIP\",\"origin\":\"ICN\",\"destination\":\"NRT\",\"departureDate\":\"2026-06-01\",\"returnDate\":\"2026-06-05\",\"adults\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tripType").value("ROUND_TRIP"))
                .andExpect(jsonPath("$[0].returnDate").value("2026-06-05"))
                .andExpect(jsonPath("$[0].outboundAirline").value("Korean Air"))
                .andExpect(jsonPath("$[0].inboundAirline").value("Asiana Airlines"))
                .andExpect(jsonPath("$[0].price").value(320000));
    }

    @Test
    void shouldCreateTracking() throws Exception {
        given(flightPrefetchService.isSupported(TripType.ONE_WAY, "ICN", "SFO", LocalDate.of(2026, 6, 1), null)).willReturn(true);
        given(flightPrefetchService.getCachedOrFetchFlights(TripType.ONE_WAY, "ICN", "SFO", LocalDate.of(2026, 6, 1), null, 1))
                .willReturn(sampleOneWayFlights());
        given(exchangeRateService.getUsdToKrwRate()).willReturn(1300d);

        mockMvc.perform(post("/api/trackings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@example.com\",\"origin\":\"ICN\",\"destination\":\"SFO\",\"departureDate\":\"2026-06-01\",\"targetPrice\":300}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.tripType").value("ONE_WAY"))
                .andExpect(jsonPath("$.lastCheckedPrice").value(416000))
                .andExpect(jsonPath("$.lastCheckedCurrency").value("KRW"))
                .andExpect(jsonPath("$.kakaoNotificationEnabled").value(false))
                .andExpect(jsonPath("$.kakaoOptIn").value(false));
    }

    @Test
    void shouldListAndDeleteTrackings() throws Exception {
        given(flightPrefetchService.isSupported(TripType.ROUND_TRIP, "ICN", "NRT", LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 5))).willReturn(true);
        given(flightPrefetchService.getCachedOrFetchFlights(
                TripType.ROUND_TRIP,
                "ICN",
                "NRT",
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 5),
                1)).willReturn(sampleRoundTripFlights());

        String body = mockMvc.perform(post("/api/trackings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tripType\":\"ROUND_TRIP\",\"origin\":\"ICN\",\"destination\":\"NRT\",\"departureDate\":\"2026-06-01\",\"returnDate\":\"2026-06-05\",\"targetPrice\":300000,\"kakaoNotificationEnabled\":true,\"kakaoOptIn\":true,\"phoneNumber\":\"01012345678\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tripType").value("ROUND_TRIP"))
                .andExpect(jsonPath("$.returnDate").value("2026-06-05"))
                .andExpect(jsonPath("$.kakaoNotificationEnabled").value(true))
                .andExpect(jsonPath("$.kakaoOptIn").value(true))
                .andExpect(jsonPath("$.phoneNumber").value("01012345678"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String id = body.replaceAll(".*\"id\":(\\d+).*", "$1");

        mockMvc.perform(get("/api/trackings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].origin").value("ICN"))
                .andExpect(jsonPath("$[0].tripType").value("ROUND_TRIP"));

        mockMvc.perform(delete("/api/trackings/{id}", id))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/trackings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void shouldReturnServiceUnavailableWhenCacheIsMissing() throws Exception {
        given(flightPrefetchService.isSupported(TripType.ONE_WAY, "ICN", "SFO", LocalDate.of(2026, 6, 1), null)).willReturn(true);
        given(flightPrefetchService.getCachedOrFetchFlights(TripType.ONE_WAY, "ICN", "SFO", LocalDate.of(2026, 6, 1), null, 1))
                .willReturn(java.util.Collections.<FlightPrice>emptyList());

        mockMvc.perform(post("/api/flights/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"origin\":\"ICN\",\"destination\":\"SFO\",\"departureDate\":\"2026-06-01\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("Flight data is being prepared. Please try again in a few minutes."));
    }

    @Test
    void shouldRejectUnsupportedRoute() throws Exception {
        given(flightPrefetchService.isSupported(TripType.ONE_WAY, "ICN", "LAX", LocalDate.of(2026, 6, 1), null)).willReturn(false);

        mockMvc.perform(post("/api/flights/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"origin\":\"ICN\",\"destination\":\"LAX\",\"departureDate\":\"2026-06-01\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("This route is supported only for dates from 2026-04-24 to 2026-04-30 (KST)."));
    }

    @Test
    void shouldRejectRoundTripWithoutReturnDate() throws Exception {
        mockMvc.perform(post("/api/flights/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tripType\":\"ROUND_TRIP\",\"origin\":\"ICN\",\"destination\":\"NRT\",\"departureDate\":\"2026-06-01\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("returnDate is required for ROUND_TRIP."));
    }

    @Test
    void shouldRefreshRoundTripCacheThroughAdminEndpoint() throws Exception {
        mockMvc.perform(post("/api/admin/cache/refresh")
                        .header("X-Admin-Token", "test-admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tripType\":\"ROUND_TRIP\",\"origin\":\"icn\",\"destination\":\"nrt\",\"departureDate\":\"2026-06-01\",\"returnDate\":\"2026-06-05\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Cache entry refreshed."));

        verify(flightPrefetchService).refresh(TripType.ROUND_TRIP, "ICN", "NRT", LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 5));
    }

    @Test
    void shouldRejectAdminEndpointWithoutValidToken() throws Exception {
        mockMvc.perform(post("/api/admin/cache/clear")
                        .header("X-Admin-Token", "wrong-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid admin token."));
    }

    private List<FlightPrice> sampleOneWayFlights() {
        FlightPrice first = new FlightPrice();
        first.setTripType(TripType.ONE_WAY);
        first.setOrigin("ICN");
        first.setDestination("SFO");
        first.setDepartureDate(LocalDate.of(2026, 6, 1));
        first.setPrice(BigDecimal.valueOf(320));
        first.setTotalPrice(BigDecimal.valueOf(320));
        first.setCurrency("USD");
        first.setProvider("api.test");
        first.setAirline("Korean Air");
        first.setOutboundAirline("Korean Air");
        first.setDepartureTime(LocalDateTime.of(2026, 6, 1, 10, 0));
        first.setArrivalTime(LocalDateTime.of(2026, 6, 1, 19, 0));

        FlightPrice second = new FlightPrice();
        second.setTripType(TripType.ONE_WAY);
        second.setOrigin("ICN");
        second.setDestination("SFO");
        second.setDepartureDate(LocalDate.of(2026, 6, 1));
        second.setPrice(BigDecimal.valueOf(410));
        second.setTotalPrice(BigDecimal.valueOf(410));
        second.setCurrency("USD");
        second.setProvider("api.test");
        second.setAirline("Asiana");
        second.setOutboundAirline("Asiana");
        second.setDepartureTime(LocalDateTime.of(2026, 6, 1, 13, 0));
        second.setArrivalTime(LocalDateTime.of(2026, 6, 1, 22, 0));

        return java.util.Arrays.asList(first, second);
    }

    private List<FlightPrice> sampleRoundTripFlights() {
        FlightPrice first = new FlightPrice();
        first.setTripType(TripType.ROUND_TRIP);
        first.setOrigin("ICN");
        first.setDestination("NRT");
        first.setDepartureDate(LocalDate.of(2026, 6, 1));
        first.setReturnDate(LocalDate.of(2026, 6, 5));
        first.setPrice(BigDecimal.valueOf(320000));
        first.setTotalPrice(BigDecimal.valueOf(320000));
        first.setCurrency("KRW");
        first.setProvider("api.test");
        first.setAirline("Korean Air");
        first.setOutboundAirline("Korean Air");
        first.setInboundAirline("Asiana Airlines");
        first.setDepartureTime(LocalDateTime.of(2026, 6, 1, 10, 0));
        first.setArrivalTime(LocalDateTime.of(2026, 6, 1, 12, 30));
        first.setReturnDepartureTime(LocalDateTime.of(2026, 6, 5, 16, 0));
        first.setReturnArrivalTime(LocalDateTime.of(2026, 6, 5, 18, 30));
        return java.util.Collections.singletonList(first);
    }
}
