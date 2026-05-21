package com.airplanehome.flight.service;

import com.airplanehome.flight.model.Restaurant;
import com.airplanehome.flight.repository.RestaurantRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class RestaurantService {

    private static final Logger log = LoggerFactory.getLogger(RestaurantService.class);
    private static final int CACHE_DAYS = 7;
    private static final int MAX_RESULTS = 15;
    private static final String PLACES_URL = "https://places.googleapis.com/v1/places:searchText";
    private static final String FIELD_MASK =
            "places.id,places.displayName,places.rating,places.userRatingCount," +
            "places.formattedAddress,places.primaryTypeDisplayName,places.location";

    private final RestaurantRepository restaurantRepository;
    private final RestTemplate restTemplate;
    private final String googlePlacesApiKey;

    public static final Map<String, CityMeta> CITY_META;

    static {
        Map<String, CityMeta> map = new LinkedHashMap<String, CityMeta>();
        map.put("NRT", new CityMeta("도쿄", "도쿄", 35.6762, 139.6503));
        map.put("TYO", new CityMeta("도쿄", "도쿄", 35.6762, 139.6503));
        map.put("FUK", new CityMeta("후쿠오카", "후쿠오카", 33.5902, 130.4017));
        map.put("KIX", new CityMeta("오사카", "오사카", 34.6937, 135.5023));
        map.put("CTS", new CityMeta("삿포로", "삿포로", 43.0618, 141.3545));
        map.put("OKA", new CityMeta("오키나와", "오키나와", 26.2124, 127.6809));
        map.put("BKK", new CityMeta("방콕", "방콕", 13.7563, 100.5018));
        map.put("HKG", new CityMeta("홍콩", "홍콩", 22.3193, 114.1694));
        map.put("TPE", new CityMeta("타이베이", "타이베이", 25.0330, 121.5654));
        map.put("SIN", new CityMeta("싱가포르", "싱가포르", 1.3521, 103.8198));
        map.put("DAD", new CityMeta("다낭", "다낭", 16.0544, 108.2022));
        map.put("SGN", new CityMeta("호치민", "호치민", 10.8231, 106.6297));
        map.put("MNL", new CityMeta("마닐라", "마닐라", 14.5995, 120.9842));
        map.put("CEB", new CityMeta("세부", "세부", 10.3157, 123.8854));
        map.put("LAX", new CityMeta("로스앤젤레스", "LA", 34.0522, -118.2437));
        map.put("JFK", new CityMeta("뉴욕", "뉴욕", 40.7128, -74.0060));
        map.put("SFO", new CityMeta("샌프란시스코", "샌프란시스코", 37.7749, -122.4194));
        map.put("SEA", new CityMeta("시애틀", "시애틀", 47.6062, -122.3321));
        map.put("YVR", new CityMeta("밴쿠버", "밴쿠버", 49.2827, -123.1207));
        map.put("GUM", new CityMeta("괌", "괌", 13.4443, 144.7937));
        map.put("SYD", new CityMeta("시드니", "시드니", -33.8688, 151.2093));
        map.put("MEL", new CityMeta("멜버른", "멜버른", -37.8136, 144.9631));
        map.put("CDG", new CityMeta("파리", "파리", 48.8566, 2.3522));
        map.put("LHR", new CityMeta("런던", "런던", 51.5074, -0.1278));
        CITY_META = Collections.unmodifiableMap(map);
    }

    public RestaurantService(RestaurantRepository restaurantRepository,
                             @Value("${google.places.api-key:}") String googlePlacesApiKey) {
        this.restaurantRepository = restaurantRepository;
        this.restTemplate = new RestTemplate();
        this.googlePlacesApiKey = googlePlacesApiKey;
    }

    public List<CityDto> getSupportedCities() {
        Set<String> seenKeys = new LinkedHashSet<String>();
        List<CityDto> cities = new ArrayList<CityDto>();
        for (Map.Entry<String, CityMeta> entry : CITY_META.entrySet()) {
            String dedupeKey = entry.getValue().getLat() + "," + entry.getValue().getLng();
            if (seenKeys.add(dedupeKey)) {
                cities.add(new CityDto(
                        entry.getKey(),
                        entry.getValue().getDisplayName(),
                        entry.getValue().getLat(),
                        entry.getValue().getLng()));
            }
        }
        return cities;
    }

    public List<Restaurant> getRestaurants(String cityCode) {
        String code = cityCode.trim().toUpperCase();
        if (!CITY_META.containsKey(code)) {
            return Collections.emptyList();
        }
        Optional<Restaurant> newest = restaurantRepository.findFirstByCityCodeOrderByCachedAtDesc(code);
        if (newest.isPresent() && newest.get().getCachedAt().isAfter(LocalDateTime.now().minusDays(CACHE_DAYS))) {
            log.info("RESTAURANT_CACHE_HIT city={}", code);
            return restaurantRepository.findByCityCodeOrderByRatingDescRatingCountDesc(code);
        }
        return fetchAndCache(code);
    }

    public List<Restaurant> fetchAndCache(String cityCode) {
        CityMeta meta = CITY_META.get(cityCode);
        if (meta == null) {
            return Collections.emptyList();
        }
        if (!StringUtils.hasText(googlePlacesApiKey)) {
            log.warn("RESTAURANT_FETCH_SKIPPED city={} reason=no-api-key", cityCode);
            return restaurantRepository.findByCityCodeOrderByRatingDescRatingCountDesc(cityCode);
        }

        try {
            String query = meta.getKoreanName() + " 맛집";
            log.info("RESTAURANT_FETCH city={} query={}", cityCode, query);

            Map<String, Object> body = new LinkedHashMap<String, Object>();
            body.put("textQuery", query);
            body.put("languageCode", "ko");
            body.put("maxResultCount", Integer.valueOf(MAX_RESULTS));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Goog-Api-Key", googlePlacesApiKey);
            headers.set("X-Goog-FieldMask", FIELD_MASK);

            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    PLACES_URL,
                    HttpMethod.POST,
                    new HttpEntity<Object>(body, headers),
                    JsonNode.class);

            JsonNode responseBody = response.getBody();
            if (responseBody == null || !responseBody.has("places")) {
                log.warn("RESTAURANT_FETCH_EMPTY city={}", cityCode);
                return restaurantRepository.findByCityCodeOrderByRatingDescRatingCountDesc(cityCode);
            }

            List<Restaurant> restaurants = new ArrayList<Restaurant>();
            for (JsonNode place : responseBody.path("places")) {
                String name = place.path("displayName").path("text").asText(null);
                if (!StringUtils.hasText(name)) {
                    continue;
                }
                Restaurant r = new Restaurant();
                r.setCityCode(cityCode);
                r.setPlaceId(place.path("id").asText(null));
                r.setName(name);
                r.setRating(place.hasNonNull("rating") ? Double.valueOf(place.path("rating").asDouble()) : null);
                r.setRatingCount(place.hasNonNull("userRatingCount") ? Integer.valueOf(place.path("userRatingCount").asInt()) : null);
                r.setAddress(place.path("formattedAddress").asText(null));
                r.setCategory(place.path("primaryTypeDisplayName").path("text").asText(null));
                JsonNode location = place.path("location");
                if (!location.isMissingNode()) {
                    r.setLat(Double.valueOf(location.path("latitude").asDouble()));
                    r.setLng(Double.valueOf(location.path("longitude").asDouble()));
                }
                r.setCachedAt(LocalDateTime.now());
                restaurants.add(r);
            }

            if (!restaurants.isEmpty()) {
                restaurantRepository.deleteByCityCode(cityCode);
                List<Restaurant> saved = restaurantRepository.saveAll(restaurants);
                log.info("RESTAURANT_CACHE_UPDATED city={} count={}", cityCode, saved.size());
                return saved;
            }
            return restaurantRepository.findByCityCodeOrderByRatingDescRatingCountDesc(cityCode);

        } catch (Exception e) {
            log.error("RESTAURANT_FETCH_FAILED city={}", cityCode, e);
            return restaurantRepository.findByCityCodeOrderByRatingDescRatingCountDesc(cityCode);
        }
    }

    public static final class CityDto {
        private final String code;
        private final String name;
        private final double lat;
        private final double lng;

        public CityDto(String code, String name, double lat, double lng) {
            this.code = code;
            this.name = name;
            this.lat = lat;
            this.lng = lng;
        }

        public String getCode() { return code; }
        public String getName() { return name; }
        public double getLat() { return lat; }
        public double getLng() { return lng; }
    }

    public static final class CityMeta {
        private final String koreanName;
        private final String displayName;
        private final double lat;
        private final double lng;

        public CityMeta(String koreanName, String displayName, double lat, double lng) {
            this.koreanName = koreanName;
            this.displayName = displayName;
            this.lat = lat;
            this.lng = lng;
        }

        public String getKoreanName() { return koreanName; }
        public String getDisplayName() { return displayName; }
        public double getLat() { return lat; }
        public double getLng() { return lng; }
    }
}
