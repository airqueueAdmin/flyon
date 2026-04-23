package com.airplanehome.flight.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Service
public class ExchangeRateService {
    private static final Logger log = LoggerFactory.getLogger(ExchangeRateService.class);

    private final RestTemplate restTemplate;
    private final String apiUrl;
    private final Duration cacheDuration;
    private final double defaultRate;

    private volatile Double cachedRate;
    private volatile Instant lastFetchedTime;

    @Autowired
    public ExchangeRateService(@Qualifier("exchangeRateRestTemplate") RestTemplate restTemplate,
                               @Value("${app.exchange-rate.api-url}") String apiUrl,
                               @Value("${app.exchange-rate.cache-duration-minutes}") long cacheDurationMinutes,
                               @Value("${app.exchange-rate.default-usd-to-krw-rate}") double defaultRate) {
        this.restTemplate = restTemplate;
        this.apiUrl = apiUrl;
        this.cacheDuration = Duration.ofMinutes(cacheDurationMinutes);
        this.defaultRate = defaultRate;
    }

    public synchronized double getUsdToKrwRate() {
        if (cachedRate != null && lastFetchedTime != null
                && Instant.now().isBefore(lastFetchedTime.plus(cacheDuration))) {
            return cachedRate;
        }

        try {
            ResponseEntity<JsonNode> response = restTemplate.getForEntity(apiUrl, JsonNode.class);
            JsonNode body = response.getBody();
            double fetchedRate = readRate(body);
            cachedRate = fetchedRate;
            lastFetchedTime = Instant.now();
            log.info("Fetched USD->KRW rate: {}", fetchedRate);
            return fetchedRate;
        } catch (Exception exception) {
            if (cachedRate != null) {
                log.warn("Exchange rate fallback used cachedRate={} apiUrl={} error={}",
                        cachedRate,
                        apiUrl,
                        exception.getMessage());
                return cachedRate;
            }

            log.warn("Exchange rate fallback used defaultRate={} apiUrl={} error={}",
                    defaultRate,
                    apiUrl,
                    exception.getMessage());
            return defaultRate;
        }
    }

    private double readRate(JsonNode body) {
        if (body == null) {
            throw new RestClientException("Exchange rate response body is empty");
        }

        JsonNode ratesNode = body.path("rates").path("KRW");
        if (ratesNode.isNumber()) {
            return ratesNode.asDouble();
        }

        JsonNode conversionRatesNode = body.path("conversion_rates").path("KRW");
        if (conversionRatesNode.isNumber()) {
            return conversionRatesNode.asDouble();
        }

        throw new RestClientException("KRW rate missing in exchange rate response");
    }
}
