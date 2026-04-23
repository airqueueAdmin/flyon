package com.airplanehome.flight.client;

import com.airplanehome.flight.model.FlightPrice;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class RapidApiClient {
    private static final Logger log = LoggerFactory.getLogger(RapidApiClient.class);
    private static final Duration LOCATION_CACHE_DURATION = Duration.ofHours(24);
    private static final Duration API_RATE_WINDOW = Duration.ofHours(1);
    private static final Duration CIRCUIT_BREAKER_DURATION = Duration.ofMinutes(10);

    private final RestTemplate restTemplate;
    private final String rapidApiKey;
    private final String rapidApiHost;
    private final String autoCompleteUrl;
    private final String baseUrl;
    private final String market;
    private final String locale;
    private final String currency;
    private final Map<String, CacheEntry<LocationIds>> locationCache = new ConcurrentHashMap<String, CacheEntry<LocationIds>>();
    private final int maxCallsPerHour;
    private final Deque<Instant> apiCallTimes = new ArrayDeque<Instant>();
    private volatile Instant circuitOpenUntil;

    public RapidApiClient(@Qualifier("rapidApiRestTemplate") RestTemplate restTemplate,
                          @Value("${rapidapi.key}") String rapidApiKey,
                          @Value("${rapidapi.host}") String rapidApiHost,
                          @Value("${rapidapi.auto-complete-url}") String autoCompleteUrl,
                          @Value("${rapidapi.base-url}") String baseUrl,
                          @Value("${rapidapi.market:US}") String market,
                          @Value("${rapidapi.locale:en-US}") String locale,
                          @Value("${rapidapi.currency:USD}") String currency,
                          @Value("${rapidapi.max-calls-per-hour:50}") int maxCallsPerHour) {
        this.restTemplate = restTemplate;
        this.rapidApiKey = rapidApiKey;
        this.rapidApiHost = rapidApiHost;
        this.autoCompleteUrl = autoCompleteUrl;
        this.baseUrl = baseUrl;
        this.market = market;
        this.locale = locale;
        this.currency = currency;
        this.maxCallsPerHour = maxCallsPerHour;
    }

    public List<FlightPrice> searchFlights(String origin, String destination, LocalDate departureDate,
                                           LocalDate returnDate, Integer passengers) {
        if (isCircuitOpen()) {
            log.warn("RapidAPI circuit open until={} - skipping primary call", circuitOpenUntil);
            return Collections.emptyList();
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-RapidAPI-Key", rapidApiKey);
            headers.set("X-RapidAPI-Host", rapidApiHost);
            headers.setContentType(MediaType.APPLICATION_JSON);

            LocationIds originIds = resolveLocation(origin, headers);
            LocationIds destinationIds = resolveLocation(destination, headers);
            if (originIds == null || destinationIds == null) {
                log.warn("RapidAPI location lookup failed origin={} destination={}", origin, destination);
                return Collections.emptyList();
            }

            String url = UriComponentsBuilder.fromHttpUrl(baseUrl)
                    .queryParam("placeIdFrom", originIds.getSkyId())
                    .queryParam("placeIdTo", destinationIds.getSkyId())
                    .queryParam("departDate", departureDate)
                    .queryParam("adults", passengers == null ? 1 : passengers)
                    .queryParam("market", market)
                    .queryParam("locale", locale)
                    .queryParam("currency", currency)
                    .build(true)
                    .toUriString();

            if (!tryAcquireApiCallSlot()) {
                log.info("API_CALL_SKIPPED url={} reason=rate-limit", url);
                return Collections.emptyList();
            }
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    new HttpEntity<Void>(headers),
                    JsonNode.class);

            List<FlightPrice> prices = extractFlightPrices(
                    response.getBody(),
                    origin,
                    destination,
                    departureDate,
                    returnDate,
                    passengers);
            if (prices.isEmpty()) {
                log.info("RapidAPI flight search call succeeded but returned no flight prices url={}", url);
            }
            return prices;
        } catch (HttpClientErrorException.TooManyRequests exception) {
            openCircuit();
            log.warn("RapidAPI quota exceeded (429) - using fallback");
            return Collections.emptyList();
        } catch (RestClientException exception) {
            log.error("RapidAPI flight search failed origin={} destination={} departureDate={} returnDate={}",
                    origin, destination, departureDate, returnDate, exception);
            return Collections.emptyList();
        }
    }

    private LocationIds resolveLocation(String userInput, HttpHeaders headers) {
        String cacheKey = normalize(userInput);
        LocationIds cachedLocation = getCachedLocation(cacheKey);
        if (cachedLocation != null) {
            log.info("Skipping API call due to cache hit location={}", userInput);
            return cachedLocation;
        }

        try {
            String url = UriComponentsBuilder.fromHttpUrl(autoCompleteUrl)
                    .queryParam("query", userInput)
                    .build(true)
                    .toUriString();
            if (!tryAcquireApiCallSlot()) {
                log.info("API_CALL_SKIPPED url={} reason=rate-limit", url);
                return getStaleLocation(cacheKey);
            }
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    new HttpEntity<Void>(headers),
                    JsonNode.class);
            JsonNode matches = response.getBody().path("data");
            if (!matches.isArray() || matches.size() == 0) {
                return null;
            }

            JsonNode bestMatch = chooseBestLocationMatch(matches, userInput);
            String skyId = readText(bestMatch, "skyId", "SkyId", "PlaceId", "placeId");
            String entityId = readText(bestMatch, "entityId", "EntityId", "GeoId", "geoId");
            if (!StringUtils.hasText(skyId)) {
                log.warn("RapidAPI location lookup returned no usable skyId input={}", userInput);
                return null;
            }

            LocationIds locationIds = new LocationIds(skyId, entityId);
            putLocationCache(cacheKey, locationIds);
            return locationIds;
        } catch (HttpClientErrorException.TooManyRequests exception) {
            openCircuit();
            log.warn("RapidAPI quota exceeded (429) - using fallback");
            return getStaleLocation(cacheKey);
        } catch (RestClientException exception) {
            log.error("RapidAPI location lookup failed input={}", userInput, exception);
            return getStaleLocation(cacheKey);
        }
    }

    private JsonNode chooseBestLocationMatch(JsonNode matches, String userInput) {
        String normalizedInput = normalize(userInput);
        JsonNode fallback = matches.path(0);

        for (JsonNode match : matches) {
            if (normalizedInput.equals(normalize(readText(
                    match,
                    "skyId",
                    "SkyId",
                    "PlaceId",
                    "placeId",
                    "displayCode",
                    "iataCode")))) {
                return match;
            }
        }

        for (JsonNode match : matches) {
            if (normalizedInput.equals(normalize(readText(
                    match,
                    "presentation.title",
                    "presentation.suggestionTitle",
                    "navigation.entityName",
                    "name",
                    "city",
                    "cityName")))) {
                return match;
            }
        }

        return fallback;
    }

    private List<FlightPrice> extractFlightPrices(JsonNode body, String origin, String destination,
                                                  LocalDate departureDate, LocalDate returnDate, Integer passengers) {
        if (body == null) {
            return Collections.emptyList();
        }

        List<FlightPrice> itineraryFlights = extractItineraryFlightPrices(
                body.path("data").path("itinerary"),
                origin,
                destination,
                departureDate,
                returnDate,
                passengers);
        if (!itineraryFlights.isEmpty()) {
            itineraryFlights.sort(Comparator.comparing(FlightPrice::getPrice));
            return itineraryFlights;
        }

        List<FlightPrice> bucketFlights = extractBucketFlights(
                body.path("data").path("itineraries").path("buckets"),
                origin,
                destination,
                departureDate,
                returnDate,
                passengers);
        if (!bucketFlights.isEmpty()) {
            bucketFlights.sort(Comparator.comparing(FlightPrice::getPrice));
            return bucketFlights;
        }

        List<FlightPrice> searchFlights = extractSearchItineraries(
                body.path("data").path("itineraries"),
                origin,
                destination,
                departureDate,
                returnDate,
                passengers);
        if (!searchFlights.isEmpty()) {
            searchFlights.sort(Comparator.comparing(FlightPrice::getPrice));
            return searchFlights;
        }

        List<FlightPrice> fallbackSearchFlights = extractSearchItineraries(
                body.path("itineraries"),
                origin,
                destination,
                departureDate,
                returnDate,
                passengers);
        if (!fallbackSearchFlights.isEmpty()) {
            fallbackSearchFlights.sort(Comparator.comparing(FlightPrice::getPrice));
            return fallbackSearchFlights;
        }

        List<JsonNode> offerNodes = new ArrayList<JsonNode>();
        collectOfferNodes(body, offerNodes);

        List<FlightPrice> flights = new ArrayList<FlightPrice>();
        for (JsonNode offerNode : offerNodes) {
            BigDecimal price = readPrice(offerNode);
            if (price == null) {
                continue;
            }

            FlightPrice flightPrice = new FlightPrice();
            flightPrice.setOrigin(origin);
            flightPrice.setDestination(destination);
            flightPrice.setDepartureDate(departureDate);
            flightPrice.setReturnDate(returnDate);
            flightPrice.setPassengers(passengers);
            flightPrice.setPrice(price);
            flightPrice.setCurrency(readCurrency(offerNode,
                    "currency",
                    "price.currency",
                    "purchaseLinks.0.currency"));
            flightPrice.setProvider(rapidApiHost);
            flightPrice.setAirline(readText(offerNode, "airline", "legs.0.carriers.marketing.0.name", "carriers.0.name"));
            flightPrice.setDepartureTime(readDateTime(offerNode,
                    "departureTime",
                    "legs.0.departure",
                    "segments.0.departure"));
            flightPrice.setArrivalTime(readDateTime(offerNode,
                    "arrivalTime",
                    "legs.0.arrival",
                    "segments.0.arrival"));
            flights.add(flightPrice);
        }

        flights.sort(Comparator.comparing(FlightPrice::getPrice));
        return flights;
    }

    private List<FlightPrice> extractBucketFlights(JsonNode bucketsNode, String origin, String destination,
                                                   LocalDate departureDate, LocalDate returnDate, Integer passengers) {
        if (bucketsNode == null || !bucketsNode.isArray()) {
            return Collections.emptyList();
        }

        List<FlightPrice> flights = new ArrayList<FlightPrice>();
        Set<String> seenItineraryIds = new HashSet<String>();
        for (JsonNode bucket : bucketsNode) {
            JsonNode items = bucket.path("items");
            if (!items.isArray()) {
                continue;
            }
            for (JsonNode item : items) {
                String itineraryId = readText(item, "id");
                if (!StringUtils.hasText(itineraryId) || !seenItineraryIds.add(itineraryId)) {
                    continue;
                }

                JsonNode firstLeg = item.path("legs").path(0);
                JsonNode firstSegment = firstLeg.path("segments").path(0);
                if (firstLeg.isMissingNode() || firstLeg.isNull()
                        || firstSegment.isMissingNode() || firstSegment.isNull()) {
                    continue;
                }

                BigDecimal price = readPrice(item.path("price"));
                if (price == null) {
                    continue;
                }

                FlightPrice flightPrice = new FlightPrice();
                flightPrice.setOrigin(origin);
                flightPrice.setDestination(destination);
                flightPrice.setDepartureDate(departureDate);
                flightPrice.setReturnDate(returnDate);
                flightPrice.setPassengers(passengers);
                flightPrice.setPrice(price);
                flightPrice.setCurrency(readCurrency(item, "price.currency", "currency"));
                flightPrice.setProvider(rapidApiHost);
                String airline = readText(
                        firstSegment,
                        "marketingCarrier.name",
                        "operatingCarrier.name");
                LocalDateTime departureTime = readDateTime(firstLeg, "departure");
                LocalDateTime arrivalTime = readDateTime(firstLeg, "arrival");
                if (!StringUtils.hasText(airline) || departureTime == null || arrivalTime == null) {
                    continue;
                }

                flightPrice.setAirline(airline);
                flightPrice.setDepartureTime(departureTime);
                flightPrice.setArrivalTime(arrivalTime);
                flights.add(flightPrice);
            }
        }

        return flights;
    }

    private List<FlightPrice> extractSearchItineraries(JsonNode itinerariesNode, String origin, String destination,
                                                       LocalDate departureDate, LocalDate returnDate, Integer passengers) {
        if (itinerariesNode == null || !itinerariesNode.isArray()) {
            return Collections.emptyList();
        }

        List<FlightPrice> flights = new ArrayList<FlightPrice>();
        for (JsonNode itineraryNode : itinerariesNode) {
            JsonNode firstLeg = itineraryNode.path("legs").path(0);
            JsonNode firstSegment = firstLeg.path("segments").path(0);
            JsonNode pricingOption = itineraryNode.path("pricingOptions").path(0);

            BigDecimal price = readPrice(pricingOption);
            if (price == null) {
                price = readPrice(itineraryNode);
            }
            if (price == null) {
                continue;
            }

            FlightPrice flightPrice = new FlightPrice();
            flightPrice.setOrigin(origin);
            flightPrice.setDestination(destination);
            flightPrice.setDepartureDate(departureDate);
            flightPrice.setReturnDate(returnDate);
            flightPrice.setPassengers(passengers);
            flightPrice.setPrice(price);
            flightPrice.setCurrency(readCurrency(
                    pricingOption,
                    "currency",
                    "price.currency",
                    "agentItems.0.price.currency"));
            if (!StringUtils.hasText(flightPrice.getCurrency())) {
                flightPrice.setCurrency(readCurrency(itineraryNode, "currency", "price.currency"));
            }
            flightPrice.setProvider(readText(pricingOption, "agents.0.name", "agentItems.0.agent.name"));
            if (!StringUtils.hasText(flightPrice.getProvider())) {
                flightPrice.setProvider(rapidApiHost);
            }
            flightPrice.setAirline(readText(
                    firstSegment,
                    "marketingCarrier.name",
                    "operatingCarrier.name",
                    "carriers.marketing.0.name"));
            flightPrice.setDepartureTime(readDateTime(firstLeg, "departure"));
            flightPrice.setArrivalTime(readDateTime(firstLeg, "arrival"));
            flights.add(flightPrice);
        }

        return flights;
    }

    private List<FlightPrice> extractItineraryFlightPrices(JsonNode itineraryNode, String origin, String destination,
                                                           LocalDate departureDate, LocalDate returnDate, Integer passengers) {
        if (itineraryNode == null || itineraryNode.isMissingNode() || itineraryNode.isNull()) {
            return Collections.emptyList();
        }

        JsonNode pricingOptions = itineraryNode.path("pricingOptions");
        JsonNode legs = itineraryNode.path("legs");
        if (!pricingOptions.isArray() || pricingOptions.size() == 0 || !legs.isArray() || legs.size() == 0) {
            return Collections.emptyList();
        }

        JsonNode firstLeg = legs.path(0);
        JsonNode firstSegment = firstLeg.path("segments").path(0);

        List<FlightPrice> flights = new ArrayList<FlightPrice>();
        for (JsonNode pricingOption : pricingOptions) {
            BigDecimal totalPrice = readPrice(pricingOption);
            if (totalPrice == null) {
                continue;
            }

            FlightPrice flightPrice = new FlightPrice();
            flightPrice.setOrigin(origin);
            flightPrice.setDestination(destination);
            flightPrice.setDepartureDate(departureDate);
            flightPrice.setReturnDate(returnDate);
            flightPrice.setPassengers(passengers);
            flightPrice.setPrice(totalPrice);
            flightPrice.setCurrency(readCurrency(pricingOption, "currency", "price.currency", "fare.currency"));
            flightPrice.setProvider(readText(pricingOption, "agents.0.name"));
            if (!StringUtils.hasText(flightPrice.getProvider())) {
                flightPrice.setProvider(rapidApiHost);
            }
            flightPrice.setAirline(readText(
                    firstSegment,
                    "marketingCarrier.name",
                    "operatingCarrier.name"));
            flightPrice.setDepartureTime(readDateTime(firstLeg, "departure"));
            flightPrice.setArrivalTime(readDateTime(firstLeg, "arrival"));
            flights.add(flightPrice);
        }

        return flights;
    }

    private void collectOfferNodes(JsonNode node, List<JsonNode> offers) {
        if (node == null || node.isNull()) {
            return;
        }

        if (node.isArray()) {
            for (JsonNode child : node) {
                collectOfferNodes(child, offers);
            }
            return;
        }

        BigDecimal price = readPrice(node);
        if (price != null) {
            offers.add(node);
            return;
        }

        Iterator<JsonNode> elements = node.elements();
        while (elements.hasNext()) {
            collectOfferNodes(elements.next(), offers);
        }
    }

    private BigDecimal readPrice(JsonNode node) {
        BigDecimal price = readNumericPrice(node,
                "price.raw",
                "price.amount",
                "raw",
                "amount",
                "purchaseLinks.0.price.raw",
                "purchaseLinks.0.price.amount");
        if (price != null) {
            log.info("Parsed price from API: raw={}", price.stripTrailingZeros().toPlainString());
            return price;
        }

        price = readNumericPrice(node,
                "totalPrice",
                "purchaseLinks.0.price",
                "purchaseLinks.0.totalPrice");
        if (price != null) {
            log.info("Parsed price from API: raw={}", price.stripTrailingZeros().toPlainString());
        }
        return price;
    }

    private String readCurrency(JsonNode node, String... paths) {
        String parsedCurrency = readText(node, paths);
        if (StringUtils.hasText(parsedCurrency)) {
            return parsedCurrency.trim().toUpperCase();
        }
        if (StringUtils.hasText(currency)) {
            return currency.trim().toUpperCase();
        }
        return null;
    }

    private BigDecimal readNumericPrice(JsonNode node, String... paths) {
        for (String path : paths) {
            JsonNode current = readNode(node, path);
            if (current == null || current.isMissingNode() || current.isNull()) {
                continue;
            }

            if (current.isNumber()) {
                return current.decimalValue();
            }

            if (current.isTextual()) {
                String value = current.asText();
                if (!StringUtils.hasText(value)) {
                    continue;
                }
                try {
                    return new BigDecimal(value.trim());
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return null;
    }

    private JsonNode readNode(JsonNode node, String path) {
        JsonNode current = node;
        for (String token : path.split("\\.")) {
            if (current == null || current.isNull()) {
                return null;
            }
            if (token.matches("\\d+")) {
                current = current.path(Integer.parseInt(token));
            } else {
                current = current.path(token);
            }
        }
        return current;
    }

    private String readText(JsonNode node, String... paths) {
        for (String path : paths) {
            JsonNode current = readNode(node, path);

            if (current != null && !current.isMissingNode() && !current.isNull()) {
                String value = current.asText();
                if (StringUtils.hasText(value)) {
                    return value;
                }
            }
        }
        return null;
    }

    private LocalDateTime readDateTime(JsonNode node, String... paths) {
        String value = readText(node, paths);
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

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.replaceAll("[^A-Za-z0-9]", "").trim().toUpperCase();
    }

    private LocationIds getCachedLocation(String cacheKey) {
        CacheEntry<LocationIds> entry = locationCache.get(cacheKey);
        if (entry == null || entry.isExpired()) {
            return null;
        }
        return entry.getValue();
    }

    private LocationIds getStaleLocation(String cacheKey) {
        CacheEntry<LocationIds> entry = locationCache.get(cacheKey);
        if (entry != null) {
            log.info("Using cached flight data for location={}", cacheKey);
            return entry.getValue();
        }
        return null;
    }

    private void putLocationCache(String cacheKey, LocationIds locationIds) {
        locationCache.put(cacheKey, new CacheEntry<LocationIds>(locationIds, Instant.now().plus(LOCATION_CACHE_DURATION)));
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

    private void openCircuit() {
        circuitOpenUntil = Instant.now().plus(CIRCUIT_BREAKER_DURATION);
    }

    public boolean isCircuitOpen() {
        Instant openUntil = circuitOpenUntil;
        return openUntil != null && Instant.now().isBefore(openUntil);
    }

    private static final class LocationIds {
        private final String skyId;
        private final String entityId;

        private LocationIds(String skyId, String entityId) {
            this.skyId = skyId;
            this.entityId = entityId;
        }

        private String getSkyId() {
            return skyId;
        }

        @SuppressWarnings("unused")
        private String getEntityId() {
            return entityId;
        }
    }

    private static final class CacheEntry<T> {
        private final T value;
        private final Instant expiresAt;

        private CacheEntry(T value, Instant expiresAt) {
            this.value = value;
            this.expiresAt = expiresAt;
        }

        private T getValue() {
            return value;
        }

        private boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }

}
