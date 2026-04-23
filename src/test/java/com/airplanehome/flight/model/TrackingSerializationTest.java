package com.airplanehome.flight.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TrackingSerializationTest {
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void shouldSerializeLastUpdatedAtAsKst() throws Exception {
        Tracking tracking = new Tracking();
        tracking.setLastUpdatedAt(LocalDateTime.of(2026, 4, 21, 15, 22, 46));

        String json = objectMapper.writeValueAsString(tracking);

        assertTrue(json.contains("\"lastUpdatedAt\":\"2026-04-21T15:22:46+09:00\""));
    }
}
