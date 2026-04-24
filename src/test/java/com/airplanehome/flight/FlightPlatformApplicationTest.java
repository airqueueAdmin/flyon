package com.airplanehome.flight;

import com.airplanehome.flight.client.RapidApiClient;
import com.airplanehome.flight.model.FlightPrice;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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
        given(flightPrefetchService.isSupported("ICN", "SFO", LocalDate.of(2026, 6, 1))).willReturn(true);
        given(flightPrefetchService.getCachedOrFetchFlights("ICN", "SFO", LocalDate.of(2026, 6, 1)))
                .willReturn(sampleFlights());
        given(exchangeRateService.getUsdToKrwRate()).willReturn(1300d);

        mockMvc.perform(post("/api/flights/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"origin\":\"ICN\",\"destination\":\"SFO\",\"departureDate\":\"2026-06-01\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].provider").value("api.test"))
                .andExpect(jsonPath("$[0].price").value(416000))
                .andExpect(jsonPath("$[0].currency").value("KRW"));
    }

    @Test
    void shouldCreateTracking() throws Exception {
        given(flightPrefetchService.isSupported("ICN", "SFO", LocalDate.of(2026, 6, 1))).willReturn(true);
        given(flightPrefetchService.getCachedOrFetchFlights("ICN", "SFO", LocalDate.of(2026, 6, 1)))
                .willReturn(sampleFlights());
        given(exchangeRateService.getUsdToKrwRate()).willReturn(1300d);

        mockMvc.perform(post("/api/trackings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@example.com\",\"origin\":\"ICN\",\"destination\":\"SFO\",\"departureDate\":\"2026-06-01\",\"targetPrice\":300}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.lastCheckedPrice").value(416000))
                .andExpect(jsonPath("$.lastCheckedCurrency").value("KRW"))
                .andExpect(jsonPath("$.kakaoNotificationEnabled").value(false))
                .andExpect(jsonPath("$.kakaoOptIn").value(false));
    }

    @Test
    void shouldListAndDeleteTrackings() throws Exception {
        given(flightPrefetchService.isSupported("ICN", "NRT", LocalDate.of(2026, 6, 1))).willReturn(true);
        given(flightPrefetchService.getCachedOrFetchFlights("ICN", "NRT", LocalDate.of(2026, 6, 1)))
                .willReturn(sampleFlights());
        given(exchangeRateService.getUsdToKrwRate()).willReturn(1300d);

        String body = mockMvc.perform(post("/api/trackings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"origin\":\"ICN\",\"destination\":\"NRT\",\"departureDate\":\"2026-06-01\",\"targetPrice\":300,\"kakaoNotificationEnabled\":true,\"kakaoOptIn\":true,\"phoneNumber\":\"01012345678\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.kakaoNotificationEnabled").value(true))
                .andExpect(jsonPath("$.kakaoOptIn").value(true))
                .andExpect(jsonPath("$.phoneNumber").value("01012345678"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String id = body.replaceAll(".*\"id\":(\\d+).*", "$1");

        mockMvc.perform(get("/api/trackings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].origin").value("ICN"));

        mockMvc.perform(delete("/api/trackings/{id}", id))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/trackings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void shouldReturnServiceUnavailableWhenCacheIsMissing() throws Exception {
        given(flightPrefetchService.isSupported("ICN", "SFO", LocalDate.of(2026, 6, 1))).willReturn(true);
        given(flightPrefetchService.getCachedOrFetchFlights("ICN", "SFO", LocalDate.of(2026, 6, 1)))
                .willReturn(java.util.Collections.<FlightPrice>emptyList());

        mockMvc.perform(post("/api/flights/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"origin\":\"ICN\",\"destination\":\"SFO\",\"departureDate\":\"2026-06-01\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("Flight data is being prepared. Please try again in a few minutes."));
    }

    @Test
    void shouldRejectUnsupportedRoute() throws Exception {
        given(flightPrefetchService.isSupported("ICN", "LAX", LocalDate.of(2026, 6, 1))).willReturn(false);

        mockMvc.perform(post("/api/flights/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"origin\":\"ICN\",\"destination\":\"LAX\",\"departureDate\":\"2026-06-01\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("This route is not yet supported."));
    }

    @Test
    void shouldRefreshCacheThroughAdminEndpoint() throws Exception {
        mockMvc.perform(post("/api/admin/cache/refresh")
                        .header("X-Admin-Token", "test-admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"origin\":\"icn\",\"destination\":\"nrt\",\"departureDate\":\"2026-06-01\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Cache entry refreshed."));

        verify(flightPrefetchService).refresh("ICN", "NRT", LocalDate.of(2026, 6, 1));
    }

    @Test
    void shouldRejectAdminEndpointWithoutValidToken() throws Exception {
        mockMvc.perform(post("/api/admin/cache/clear")
                        .header("X-Admin-Token", "wrong-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid admin token."));
    }

    private List<FlightPrice> sampleFlights() {
        FlightPrice first = new FlightPrice();
        first.setOrigin("ICN");
        first.setDestination("SFO");
        first.setDepartureDate(LocalDate.of(2026, 6, 1));
        first.setPrice(BigDecimal.valueOf(320));
        first.setCurrency("USD");
        first.setProvider("api.test");
        first.setAirline("Korean Air");
        first.setDepartureTime(LocalDateTime.of(2026, 6, 1, 10, 0));
        first.setArrivalTime(LocalDateTime.of(2026, 6, 1, 19, 0));

        FlightPrice second = new FlightPrice();
        second.setOrigin("ICN");
        second.setDestination("SFO");
        second.setDepartureDate(LocalDate.of(2026, 6, 1));
        second.setPrice(BigDecimal.valueOf(410));
        second.setCurrency("USD");
        second.setProvider("api.test");
        second.setAirline("Asiana");
        second.setDepartureTime(LocalDateTime.of(2026, 6, 1, 13, 0));
        second.setArrivalTime(LocalDateTime.of(2026, 6, 1, 22, 0));

        return java.util.Arrays.asList(first, second);
    }
}
