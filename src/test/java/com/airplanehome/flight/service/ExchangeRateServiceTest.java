package com.airplanehome.flight.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ExchangeRateServiceTest {
    @Test
    void shouldFetchAndCacheUsdToKrwRate() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(once(), requestTo("https://example.com/rates"))
                .andRespond(withSuccess("{\"rates\":{\"KRW\":1320.5}}", MediaType.APPLICATION_JSON));

        ExchangeRateService service = new ExchangeRateService(restTemplate, "https://example.com/rates", 60, 1300d);

        assertEquals(1320.5d, service.getUsdToKrwRate());
        assertEquals(1320.5d, service.getUsdToKrwRate());
        server.verify();
    }

    @Test
    void shouldUseFallbackRateWhenApiFails() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(once(), requestTo("https://example.com/failing-rates"))
                .andRespond(withServerError());

        ExchangeRateService service = new ExchangeRateService(restTemplate, "https://example.com/failing-rates", 60, 1300d);

        assertEquals(1300d, service.getUsdToKrwRate());
        server.verify();
    }

    @Test
    void shouldUseCachedRateWhenRefreshFails() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(once(), requestTo("https://example.com/rates"))
                .andRespond(withSuccess("{\"rates\":{\"KRW\":1320.5}}", MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo("https://example.com/rates"))
                .andRespond(withServerError());

        ExchangeRateService service = new ExchangeRateService(restTemplate, "https://example.com/rates", 0, 1300d);

        assertEquals(1320.5d, service.getUsdToKrwRate());
        assertEquals(1320.5d, service.getUsdToKrwRate());
        server.verify();
    }
}
