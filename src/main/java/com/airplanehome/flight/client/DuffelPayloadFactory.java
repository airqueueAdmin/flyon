package com.airplanehome.flight.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

final class DuffelPayloadFactory {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private DuffelPayloadFactory() {
    }

    static JsonNode offerRequest(String origin,
                                 String destination,
                                 String departureDate,
                                 String returnDate,
                                 Integer adults) {
        ObjectNode root = OBJECT_MAPPER.createObjectNode();
        ObjectNode data = root.putObject("data");

        ArrayNode slices = data.putArray("slices");
        addSlice(slices, origin, destination, departureDate);
        if (returnDate != null) {
            addSlice(slices, destination, origin, returnDate);
        }

        ArrayNode passengers = data.putArray("passengers");
        int passengerCount = adults == null || adults.intValue() < 1 ? 1 : adults.intValue();
        for (int index = 0; index < passengerCount; index++) {
            passengers.addObject().put("type", "adult");
        }
        data.put("cabin_class", "economy");
        return root;
    }

    private static void addSlice(ArrayNode slices, String origin, String destination, String departureDate) {
        ObjectNode slice = slices.addObject();
        slice.put("origin", origin);
        slice.put("destination", destination);
        slice.put("departure_date", departureDate);
    }
}
