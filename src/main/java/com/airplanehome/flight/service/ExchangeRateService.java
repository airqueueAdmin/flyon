package com.airplanehome.flight.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private static final List<String> DISPLAY_CURRENCIES = List.of("USD", "JPY", "EUR", "THB", "SGD");

    private final RestTemplate restTemplate;
    private final String apiUrl;
    private final Duration cacheDuration;
    private final double defaultRate;

    private volatile Double cachedRate;
    private volatile JsonNode cachedBody;
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
        refreshIfNeeded();
        return cachedRate != null ? cachedRate : defaultRate;
    }

    public synchronized Map<String, Object> getDisplayRates() {
        refreshIfNeeded();

        Map<String, Long> rates = new LinkedHashMap<>();
        double krwPerUsd = cachedRate != null ? cachedRate : defaultRate;

        JsonNode ratesNode = cachedBody != null ? getRatesNode(cachedBody) : null;
        for (String currency : DISPLAY_CURRENCIES) {
            long krwPerUnit = computeKrwPerUnit(currency, krwPerUsd, ratesNode);
            rates.put(currency, krwPerUnit);
        }

        String date = cachedBody != null ? cachedBody.path("date").asText("") : "";

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("date", date);
        result.put("rates", rates);
        return result;
    }

    private void refreshIfNeeded() {
        if (cachedRate != null && lastFetchedTime != null
                && Instant.now().isBefore(lastFetchedTime.plus(cacheDuration))) {
            return;
        }

        try {
            ResponseEntity<JsonNode> response = restTemplate.getForEntity(apiUrl, JsonNode.class);
            JsonNode body = response.getBody();
            double fetchedRate = readKrwRate(body);
            cachedRate = fetchedRate;
            cachedBody = body;
            lastFetchedTime = Instant.now();
            log.info("Fetched USD->KRW rate: {}", fetchedRate);
        } catch (Exception exception) {
            if (cachedRate != null) {
                log.warn("Exchange rate fallback used cachedRate={} apiUrl={} error={}",
                        cachedRate, apiUrl, exception.getMessage());
            } else {
                log.warn("Exchange rate fallback used defaultRate={} apiUrl={} error={}",
                        defaultRate, apiUrl, exception.getMessage());
                cachedRate = defaultRate;
            }
        }
    }

    private long computeKrwPerUnit(String currency, double krwPerUsd, JsonNode ratesNode) {
        if ("USD".equals(currency)) {
            return Math.round(krwPerUsd);
        }
        if (ratesNode != null) {
            JsonNode node = ratesNode.path(currency);
            if (node.isNumber()) {
                // ratesNode is from /latest/USD so node = units of currency per 1 USD
                double unitsPerUsd = node.asDouble();
                if (unitsPerUsd > 0) {
                    double krwPerOneUnit = krwPerUsd / unitsPerUsd;
                    return "JPY".equals(currency) ? Math.round(krwPerOneUnit * 100) : Math.round(krwPerOneUnit);
                }
            }
        }
        return 0L;
    }

    private JsonNode getRatesNode(JsonNode body) {
        JsonNode n = body.path("rates");
        if (!n.isMissingNode()) return n;
        return body.path("conversion_rates");
    }

    private double readKrwRate(JsonNode body) {
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
