package com.airplanehome.flight;

import java.security.cert.X509Certificate;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestTemplate;
import java.time.Duration;
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

@SpringBootApplication
@EnableScheduling
public class FlightPlatformApplication {
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(45))
                .build();
    }

    @Bean
    @Qualifier("rapidApiRestTemplate")
    public RestTemplate rapidApiRestTemplate(RestTemplateBuilder builder,
                                             @Value("${rapidapi.insecure-ssl:false}") boolean insecureSsl) throws Exception {
        return buildRestTemplate(builder, insecureSsl);
    }

    @Bean
    @Qualifier("duffelRestTemplate")
    public RestTemplate duffelRestTemplate(RestTemplateBuilder builder,
                                           @Value("${duffel.insecure-ssl:false}") boolean insecureSsl) throws Exception {
        return buildRestTemplate(builder, insecureSsl);
    }

    @Bean
    @Qualifier("exchangeRateRestTemplate")
    public RestTemplate exchangeRateRestTemplate(RestTemplateBuilder builder,
                                                 @Value("${app.exchange-rate.insecure-ssl:false}") boolean insecureSsl) throws Exception {
        return buildRestTemplate(builder, insecureSsl);
    }

    @Bean
    @Qualifier("googlePlacesRestTemplate")
    public RestTemplate googlePlacesRestTemplate(RestTemplateBuilder builder,
                                                 @Value("${google.places.insecure-ssl:false}") boolean insecureSsl) throws Exception {
        return buildRestTemplate(builder, insecureSsl);
    }

    public static void main(String[] args) {
        SpringApplication.run(FlightPlatformApplication.class, args);
    }

    private RestTemplate buildRestTemplate(RestTemplateBuilder builder, boolean insecureSsl) throws Exception {
        RestTemplateBuilder configuredBuilder = builder
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(45));

        if (!insecureSsl) {
            return configuredBuilder.build();
        }

        TrustStrategy trustAllCertificates = new TrustStrategy() {
            @Override
            public boolean isTrusted(X509Certificate[] chain, String authType) {
                return true;
            }
        };

        SSLContext sslContext = SSLContextBuilder.create()
                .loadTrustMaterial(null, trustAllCertificates)
                .build();
        SSLConnectionSocketFactory sslSocketFactory =
                new SSLConnectionSocketFactory(sslContext, NoopHostnameVerifier.INSTANCE);
        Registry<ConnectionSocketFactory> socketFactoryRegistry = RegistryBuilder.<ConnectionSocketFactory>create()
                .register("http", PlainConnectionSocketFactory.getSocketFactory())
                .register("https", sslSocketFactory)
                .build();
        PoolingHttpClientConnectionManager connectionManager =
                new PoolingHttpClientConnectionManager(socketFactoryRegistry);
        HttpClient httpClient = HttpClients.custom()
                .setSSLSocketFactory(sslSocketFactory)
                .setConnectionManager(connectionManager)
                .build();

        return configuredBuilder
                .requestFactory(() -> new HttpComponentsClientHttpRequestFactory(httpClient))
                .build();
    }
}
