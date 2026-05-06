package com.airplanehome.flight.service;

import java.security.cert.X509Certificate;
import javax.net.ssl.SSLContext;
import org.apache.http.client.HttpClient;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustStrategy;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.ssl.SSLContextBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
    void shouldFetchRealUsdToKrwRate() throws Exception {
        TrustStrategy trustAll = (X509Certificate[] chain, String authType) -> true;
        SSLContext sslContext = SSLContextBuilder.create().loadTrustMaterial(null, trustAll).build();
        SSLConnectionSocketFactory sslSocketFactory = new SSLConnectionSocketFactory(sslContext, NoopHostnameVerifier.INSTANCE);
        Registry<ConnectionSocketFactory> registry = RegistryBuilder.<ConnectionSocketFactory>create()
                .register("http", PlainConnectionSocketFactory.getSocketFactory())
                .register("https", sslSocketFactory)
                .build();
        HttpClient httpClient = HttpClients.custom()
                .setSSLSocketFactory(sslSocketFactory)
                .setConnectionManager(new PoolingHttpClientConnectionManager(registry))
                .build();
        RestTemplate restTemplate = new RestTemplate(new HttpComponentsClientHttpRequestFactory(httpClient));

        ExchangeRateService service = new ExchangeRateService(
                restTemplate,
                "https://api.exchangerate-api.com/v4/latest/USD",
                60,
                1450d);

        double rate = service.getUsdToKrwRate();
        System.out.println(">>> 현재 USD->KRW 환율: " + rate);
        assertTrue(rate > 0, "환율은 0보다 커야 합니다");
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
