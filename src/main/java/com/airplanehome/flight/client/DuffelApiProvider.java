package com.airplanehome.flight.client;

import com.airplanehome.flight.model.FlightPrice;
import com.airplanehome.flight.model.TripType;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
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
    public List<FlightPrice> search(TripType tripType,
                                    String origin,
                                    String destination,
                                    LocalDate departureDate,
                                    LocalDate returnDate,
                                    Integer adults) {
        if (!enabled || !StringUtils.hasText(apiKey)) {
            log.info("API_CALL_SKIPPED provider=duffel reason=disabled");
            return Collections.emptyList();
        }

        TripType resolvedTripType = tripType == null ? (returnDate == null ? TripType.ONE_WAY : TripType.ROUND_TRIP) : tripType;
        if (resolvedTripType.isRoundTrip() && returnDate == null) {
            log.warn("Duffel provider rejected missing returnDate for round trip");
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
                            departureDate.toString(),
                            returnDate == null ? null : returnDate.toString(),
                            adults), headers),
                    JsonNode.class);

            List<FlightPrice> flights = extractFlightPrices(
                    response.getBody(),
                    resolvedTripType,
                    normalizedOrigin,
                    normalizedDestination,
                    departureDate,
                    returnDate,
                    adults);
            if (!flights.isEmpty()) {
                log.info("DUFFEL_API_SUCCESS tripType={} origin={} destination={} departureDate={} returnDate={} size={}",
                        resolvedTripType, normalizedOrigin, normalizedDestination, departureDate, returnDate, flights.size());
            } else {
                log.warn("DUFFEL_API_FAILED tripType={} origin={} destination={} departureDate={} returnDate={} reason=empty-response",
                        resolvedTripType, normalizedOrigin, normalizedDestination, departureDate, returnDate);
            }
            return flights;
        } catch (RestClientException exception) {
            log.error("DUFFEL_API_FAILED tripType={} origin={} destination={} departureDate={} returnDate={}",
                    resolvedTripType, normalizedOrigin, normalizedDestination, departureDate, returnDate, exception);
            return Collections.emptyList();
        }
    }

    private List<FlightPrice> extractFlightPrices(JsonNode body,
                                                  TripType tripType,
                                                  String origin,
                                                  String destination,
                                                  LocalDate departureDate,
                                                  LocalDate returnDate,
                                                  Integer adults) {
        JsonNode offers = body == null ? null : body.path("data").path("offers");
        if (offers == null || !offers.isArray()) {
            return Collections.emptyList();
        }

        List<FlightPrice> flights = new ArrayList<FlightPrice>();
        for (JsonNode offer : offers) {
            BigDecimal totalPrice = readDecimal(offer.path("total_amount"));
            JsonNode slices = offer.path("slices");
            JsonNode outboundSlice = slices.path(0);
            JsonNode inboundSlice = tripType.isRoundTrip() ? slices.path(1) : null;
            FlightSegment outbound = extractSegment(outboundSlice);
            FlightSegment inbound = inboundSlice == null || inboundSlice.isMissingNode() ? null : extractSegment(inboundSlice);

            if (totalPrice == null || outbound == null) {
                continue;
            }
            if (tripType.isRoundTrip() && inbound == null) {
                continue;
            }

            FlightPrice flightPrice = new FlightPrice();
            flightPrice.setTripType(tripType);
            flightPrice.setOrigin(origin);
            flightPrice.setDestination(destination);
            flightPrice.setDepartureDate(departureDate);
            flightPrice.setReturnDate(returnDate);
            flightPrice.setTotalPrice(totalPrice);
            flightPrice.setPrice(totalPrice);
            flightPrice.setCurrency(readText(offer, "total_currency"));
            flightPrice.setProvider("duffel");
            flightPrice.setAirline(outbound.airline);
            flightPrice.setOutboundAirline(outbound.airline);
            flightPrice.setInboundAirline(inbound == null ? null : inbound.airline);
            flightPrice.setDepartureTime(outbound.departureTime);
            flightPrice.setArrivalTime(outbound.arrivalTime);
            flightPrice.setReturnDepartureTime(inbound == null ? null : inbound.departureTime);
            flightPrice.setReturnArrivalTime(inbound == null ? null : inbound.arrivalTime);
            flightPrice.setPassengers(adults == null ? 1 : adults);
            flights.add(flightPrice);
        }

        flights.sort(Comparator.comparing(FlightPrice::getPrice));
        return flights;
    }

    private FlightSegment extractSegment(JsonNode slice) {
        if (slice == null || slice.isMissingNode() || slice.isNull()) {
            return null;
        }

        JsonNode segments = slice.path("segments");
        JsonNode firstSegment = segments.path(0);
        JsonNode lastSegment = segments.path(Math.max(0, segments.size() - 1));
        if (firstSegment.isMissingNode() || lastSegment.isMissingNode()) {
            return null;
        }

        String airline = readAirlineName(slice, firstSegment);
        LocalDateTime departureTime = readDateTime(firstSegment, "departing_at");
        LocalDateTime arrivalTime = readDateTime(lastSegment, "arriving_at");
        if (!StringUtils.hasText(airline) || departureTime == null || arrivalTime == null) {
            return null;
        }

        return new FlightSegment(airline, departureTime, arrivalTime);
    }

    private String readAirlineName(JsonNode offerOrSlice, JsonNode firstSegment) {
        String airline = readText(firstSegment, "operating_carrier.name");
        if (StringUtils.hasText(airline)) {
            return airline;
        }

        airline = readText(firstSegment, "marketing_carrier.name");
        if (StringUtils.hasText(airline)) {
            return airline;
        }

        airline = readText(offerOrSlice, "owner.name");
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

    private static final class FlightSegment {
        private final String airline;
        private final LocalDateTime departureTime;
        private final LocalDateTime arrivalTime;

        private FlightSegment(String airline, LocalDateTime departureTime, LocalDateTime arrivalTime) {
            this.airline = airline;
            this.departureTime = departureTime;
            this.arrivalTime = arrivalTime;
        }
    }
}
