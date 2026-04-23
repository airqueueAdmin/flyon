package com.airplanehome.flight.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

final class DuffelPayloadFactory {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private DuffelPayloadFactory() {
    }

    static JsonNode offerRequest(String origin, String destination, String departureDate) {
        ObjectNode root = OBJECT_MAPPER.createObjectNode();
        ObjectNode data = root.putObject("data");

        ArrayNode slices = data.putArray("slices");
        ObjectNode slice = slices.addObject();
        slice.put("origin", origin);
        slice.put("destination", destination);
        slice.put("departure_date", departureDate);

        ArrayNode passengers = data.putArray("passengers");
        passengers.addObject().put("type", "adult");
        data.put("cabin_class", "economy");
        return root;
    }
}
