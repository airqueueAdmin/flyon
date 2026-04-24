package com.airplanehome.flight.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RapidApiClientPriceParsingTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private RapidApiClient rapidApiClient;
    private Method readPriceMethod;

    @BeforeEach
    void setUp() throws Exception {
        rapidApiClient = new RapidApiClient(
                new RestTemplate(),
                "test-key",
                "test-host",
                "https://example.com/auto-complete",
                "https://example.com/search",
                "",
                "KR",
                "ko-KR",
                "KRW",
                50);
        readPriceMethod = RapidApiClient.class.getDeclaredMethod("readPrice", JsonNode.class);
        readPriceMethod.setAccessible(true);
    }

    @Test
    void shouldPreferPriceRawWithoutScaling() throws Exception {
        JsonNode node = objectMapper.readTree("{\"price\":{\"raw\":770000,\"formatted\":\"₩77\"}}");

        BigDecimal price = invokeReadPrice(node);

        assertEquals(new BigDecimal("770000"), price);
    }

    @Test
    void shouldUsePriceAmountWithoutScaling() throws Exception {
        JsonNode node = objectMapper.readTree("{\"price\":{\"amount\":820000,\"formatted\":\"₩82\"}}");

        BigDecimal price = invokeReadPrice(node);

        assertEquals(new BigDecimal("820000"), price);
    }

    @Test
    void shouldIgnoreFormattedStringOnlyPrices() throws Exception {
        JsonNode node = objectMapper.readTree("{\"price\":{\"formatted\":\"₩77\"}}");

        BigDecimal price = invokeReadPrice(node);

        assertNull(price);
    }

    private BigDecimal invokeReadPrice(JsonNode node) throws Exception {
        return (BigDecimal) readPriceMethod.invoke(rapidApiClient, node);
    }
}
