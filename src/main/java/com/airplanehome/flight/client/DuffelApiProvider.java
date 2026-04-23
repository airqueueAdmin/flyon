package com.airplanehome.flight.client;

import com.airplanehome.flight.model.FlightPrice;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Component("duffelApiProvider")
public class DuffelApiProvider implements FlightProvider {
    private static final Logger log = LoggerFactory.getLogger(DuffelApiProvider.class);
    private static final Duration API_RATE_WINDOW = Duration.ofHours(1);

    private final RestTemplate restTemplate;
    private final boolean enabled;
    private final String apiKey;
    private final String baseUrl;
    private final String apiVersion;
    private final int maxCallsPerHour;
    private final Deque<Instant> apiCallTimes = new ArrayDeque<Instant>();

    public DuffelApiProvider(@Qualifier("duffelRestTemplate") RestTemplate restTemplate,
                             @Value("${duffel.enabled:false}") boolean enabled,
                             @Value("${duffel.api-key:}") String apiKey,
                             @Value("${duffel.base-url:https://api.duffel.com}") String baseUrl,
                             @Value("${duffel.version:v2}") String apiVersion,
                             @Value("${duffel.max-calls-per-hour:10}") int maxCallsPerHour) {
        this.restTemplate = restTemplate;
        this.enabled = enabled;
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.apiVersion = apiVersion;
        this.maxCallsPerHour = maxCallsPerHour;
    }

    @Override
    public List<FlightPrice> search(String origin, String destination, String departureDate) {
        if (!enabled || !StringUtils.hasText(apiKey)) {
            log.info("API_CALL_SKIPPED provider=duffel reason=disabled");
            return Collections.emptyList();
        }

        LocalDate parsedDepartureDate;
        try {
            parsedDepartureDate = LocalDate.parse(departureDate);
        } catch (DateTimeParseException exception) {
            log.warn("Duffel provider rejected invalid departureDate={}", departureDate);
            return Collections.emptyList();
        }

        if (!tryAcquireApiCallSlot()) {
            log.info("API_CALL_SKIPPED provider=duffel reason=rate-limit");
            return Collections.emptyList();
        }

        String normalizedOrigin = origin.trim().toUpperCase();
        String normalizedDestination = destination.trim().toUpperCase();
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(apiKey);
            headers.set("Duffel-Version", apiVersion);
            headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
            headers.setContentType(MediaType.APPLICATION_JSON);

            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    baseUrl + "/air/offer_requests",
                    HttpMethod.POST,
                    new HttpEntity<JsonNode>(DuffelPayloadFactory.offerRequest(
                            normalizedOrigin,
                            normalizedDestination,
                            parsedDepartureDate.toString()), headers),
                    JsonNode.class);

            List<FlightPrice> flights = extractFlightPrices(
                    response.getBody(),
                    normalizedOrigin,
                    normalizedDestination,
                    parsedDepartureDate);
            if (!flights.isEmpty()) {
                log.info("DUFFEL_API_SUCCESS origin={} destination={} departureDate={} size={}",
                        normalizedOrigin, normalizedDestination, parsedDepartureDate, flights.size());
            } else {
                log.warn("DUFFEL_API_FAILED origin={} destination={} departureDate={} reason=empty-response",
                        normalizedOrigin, normalizedDestination, parsedDepartureDate);
            }
            return flights;
        } catch (RestClientException exception) {
            log.error("DUFFEL_API_FAILED origin={} destination={} departureDate={}",
                    normalizedOrigin, normalizedDestination, parsedDepartureDate, exception);
            return Collections.emptyList();
        }
    }

    private List<FlightPrice> extractFlightPrices(JsonNode body,
                                                  String origin,
                                                  String destination,
                                                  LocalDate departureDate) {
        JsonNode offers = body == null ? null : body.path("data").path("offers");
        if (offers == null || !offers.isArray()) {
            return Collections.emptyList();
        }

        List<FlightPrice> flights = new ArrayList<FlightPrice>();
        for (JsonNode offer : offers) {
            BigDecimal price = readDecimal(offer.path("total_amount"));
            JsonNode slices = offer.path("slices");
            JsonNode firstSlice = slices.path(0);
            JsonNode segments = firstSlice.path("segments");
            JsonNode firstSegment = segments.path(0);
            JsonNode lastSegment = segments.path(segments.size() - 1);

            if (price == null || firstSegment.isMissingNode() || lastSegment.isMissingNode()) {
                continue;
            }

            FlightPrice flightPrice = new FlightPrice();
            flightPrice.setOrigin(origin);
            flightPrice.setDestination(destination);
            flightPrice.setDepartureDate(departureDate);
            flightPrice.setPrice(price);
            flightPrice.setCurrency(readText(offer, "total_currency"));
            flightPrice.setProvider("duffel");
            flightPrice.setAirline(readAirlineName(offer, firstSegment));
            flightPrice.setDepartureTime(readDateTime(firstSegment, "departing_at"));
            flightPrice.setArrivalTime(readDateTime(lastSegment, "arriving_at"));
            if (!StringUtils.hasText(flightPrice.getAirline())) {
                continue;
            }
            flights.add(flightPrice);
        }

        flights.sort(Comparator.comparing(FlightPrice::getPrice));
        return flights;
    }

    private String readAirlineName(JsonNode offer, JsonNode firstSegment) {
        String airline = readText(firstSegment, "operating_carrier.name");
        if (StringUtils.hasText(airline)) {
            return airline;
        }

        airline = readText(firstSegment, "marketing_carrier.name");
        if (StringUtils.hasText(airline)) {
            return airline;
        }

        airline = readText(offer, "owner.name");
        if ("Duffel Airways".equalsIgnoreCase(airline)) {
            return null;
        }
        return airline;
    }

    private BigDecimal readDecimal(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            return node.decimalValue();
        }
        if (node.isTextual() && StringUtils.hasText(node.asText())) {
            try {
                return new BigDecimal(node.asText().trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private String readText(JsonNode node, String path) {
        JsonNode current = node;
        for (String token : path.split("\\.")) {
            if (current == null || current.isNull()) {
                return null;
            }
            current = current.path(token);
        }
        if (current == null || current.isMissingNode() || current.isNull()) {
            return null;
        }
        String value = current.asText();
        return StringUtils.hasText(value) ? value : null;
    }

    private LocalDateTime readDateTime(JsonNode node, String path) {
        String value = readText(node, path);
        if (!StringUtils.hasText(value)) {
            return null;
        }

        try {
            return OffsetDateTime.parse(value).toLocalDateTime();
        } catch (Exception ignored) {
        }

        try {
            return LocalDateTime.parse(value);
        } catch (Exception ignored) {
        }
        return null;
    }

    private synchronized boolean tryAcquireApiCallSlot() {
        Instant now = Instant.now();
        while (!apiCallTimes.isEmpty() && apiCallTimes.peekFirst().isBefore(now.minus(API_RATE_WINDOW))) {
            apiCallTimes.removeFirst();
        }
        if (apiCallTimes.size() >= maxCallsPerHour) {
            return false;
        }
        apiCallTimes.addLast(now);
        return true;
    }
}
