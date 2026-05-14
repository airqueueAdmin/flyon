package com.airplanehome.flight.service;

import com.airplanehome.flight.model.Tracking;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class KakaoNotificationService {
    private static final Logger log = LoggerFactory.getLogger(KakaoNotificationService.class);
    private static final String TEMPLATE_CONTENT =
            "[항공권 가격 변동 안내]\n\n#{route} 항공권 가격이 변경되었습니다.\n\n이전 가격: #{oldPrice}\n현재 가격: #{newPrice}\n\n자세히 보기:\n#{link}";
    private static final String KAKAO_MEMO_SEND_URL = "https://kapi.kakao.com/v2/api/talk/memo/default/send";
    private static final String SKYSCANNER_BASE_URL = "https://www.skyscanner.co.kr";

    private final RestTemplate restTemplate;
    private final KakaoNotificationProperties properties;
    private final KakaoAuthService kakaoAuthService;
    private final ObjectMapper objectMapper;
    private final String appBaseUrl;

    public KakaoNotificationService(RestTemplate restTemplate,
                                    KakaoNotificationProperties properties,
                                    KakaoAuthService kakaoAuthService,
                                    ObjectMapper objectMapper,
                                    @Value("${app.web.base-url}") String appBaseUrl) {
        this.restTemplate = restTemplate;
        this.properties = properties;
        this.kakaoAuthService = kakaoAuthService;
        this.objectMapper = objectMapper;
        this.appBaseUrl = appBaseUrl;
    }

    public boolean sendAlimTalk(Tracking tracking, int oldPrice, int newPrice) {
        String route = tracking.getOrigin() + "→" + tracking.getDestination();
        if (!properties.isEnabled()) {
            log.error("KAKAO_FAILED: disabled {}", route);
            return false;
        }
        if (!"kakao-message-api".equalsIgnoreCase(properties.getProvider())) {
            log.error("KAKAO_FAILED: unsupported provider {}", properties.getProvider());
            return false;
        }
        if (!properties.getMissingRequiredFields().isEmpty()) {
            log.error("KAKAO_FAILED: missing configuration {} missing={}", route, properties.getMissingRequiredFields());
            return false;
        }

        try {
            kakaoAuthService.refreshTrackingTokensIfNeeded(tracking);
            if (!StringUtils.hasText(tracking.getKakaoAccessToken())) {
                log.error("KAKAO_FAILED: missing access token {}", route);
                return false;
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(tracking.getKakaoAccessToken());
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            MultiValueMap<String, String> form = new LinkedMultiValueMap<String, String>();
            form.add("template_object", toJson(buildPayload(tracking, oldPrice, newPrice)));

            ResponseEntity<Map> response = restTemplate.exchange(
                    KAKAO_MEMO_SEND_URL,
                    HttpMethod.POST,
                    new HttpEntity<MultiValueMap<String, String>>(form, headers),
                    Map.class);

            if (isAccepted(response)) {
                log.info("KAKAO_SENT: {}→{} {}→{}",
                        tracking.getOrigin(),
                        tracking.getDestination(),
                        oldPrice,
                        newPrice);
                return true;
            }

            log.error("KAKAO_FAILED: unexpected status {}", response.getStatusCodeValue());
            return false;
        } catch (RestClientException ex) {
            log.error("KAKAO_FAILED: {}", ex.getMessage());
            return false;
        }
    }

    public Map<String, Object> examplePayload() {
        Tracking tracking = new Tracking();
        tracking.setOrigin("ICN");
        tracking.setDestination("NRT");
        tracking.setDepartureDate(java.time.LocalDate.of(2026, 5, 1));
        tracking.setKakaoNickname("카카오 사용자");
        return buildPayload(tracking, 320000, 270000);
    }

    public Map<String, Object> previewPayload(Tracking tracking, int oldPrice, int newPrice) {
        return buildPayload(tracking, oldPrice, newPrice);
    }

    public Map<String, Object> status() {
        Map<String, Object> status = new LinkedHashMap<String, Object>();
        List<String> missingConfiguration = new ArrayList<String>(properties.getMissingRequiredFields());
        status.put("enabled", Boolean.valueOf(properties.isEnabled()));
        status.put("ready", Boolean.valueOf(properties.isReady()));
        status.put("provider", properties.getProvider());
        status.put("redirectUri", properties.getRedirectUri());
        status.put("appBaseUrl", appBaseUrl);
        status.put("minPriceDropKrw", properties.getMinPriceDropKrw());
        status.put("minPriceDropPercent", properties.getMinPriceDropPercent());
        status.put("missingConfiguration", missingConfiguration);
        return status;
    }

    private Map<String, Object> buildPayload(Tracking tracking, int oldPrice, int newPrice) {
        Map<String, String> variables = buildTemplateVariables(tracking, oldPrice, newPrice);
        String content = applyTemplate(variables);

        Map<String, Object> templateObject = new LinkedHashMap<String, Object>();
        templateObject.put("object_type", "text");
        templateObject.put("text", content);
        templateObject.put("link", buildPrimaryLink(tracking));
        templateObject.put("button_title", "추적 목록 보기");
        templateObject.put("buttons", buildButtons(tracking));
        templateObject.put("templateVariables", variables);
        return templateObject;
    }

    private Map<String, String> buildTemplateVariables(Tracking tracking, int oldPrice, int newPrice) {
        Map<String, String> variables = new LinkedHashMap<String, String>();
        variables.put("route", tracking.getOrigin() + " → " + tracking.getDestination());
        variables.put("oldPrice", formatWon(oldPrice));
        variables.put("newPrice", formatWon(newPrice));
        variables.put("link", buildTrackingPageLink(tracking));
        return variables;
    }

    private String applyTemplate(Map<String, String> variables) {
        String content = TEMPLATE_CONTENT;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            content = content.replace("#{" + entry.getKey() + "}", entry.getValue());
        }
        return content;
    }

    private List<Map<String, Object>> buildButtons(Tracking tracking) {
        List<Map<String, Object>> buttons = new ArrayList<Map<String, Object>>();
        buttons.add(buildWebLinkButton("추적 목록 보기", buildTrackingPageLink(tracking)));
        buttons.add(buildWebLinkButton("스카이스캐너 이동", buildSkyscannerLink(tracking)));
        return buttons;
    }

    private Map<String, Object> buildPrimaryLink(Tracking tracking) {
        return buildLinkObject(buildTrackingPageLink(tracking));
    }

    private Map<String, Object> buildWebLinkButton(String name, String link) {
        Map<String, Object> button = new LinkedHashMap<String, Object>();
        button.put("title", name);
        button.put("link", buildLinkObject(link));
        return button;
    }

    private Map<String, Object> buildLinkObject(String link) {
        Map<String, Object> linkObject = new LinkedHashMap<String, Object>();
        linkObject.put("web_url", link);
        linkObject.put("mobile_web_url", link);
        return linkObject;
    }

    private String buildTrackingPageLink(Tracking tracking) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(appBaseUrl)
                .path("/tracking.html");
        if (tracking.getId() != null) {
            builder.queryParam("id", tracking.getId());
        } else {
            builder.queryParam("origin", tracking.getOrigin())
                    .queryParam("destination", tracking.getDestination())
                    .queryParam("date", tracking.getDepartureDate());
        }
        if (StringUtils.hasText(tracking.getKakaoConnectionId())) {
            builder.queryParam("connection", tracking.getKakaoConnectionId());
        }
        return builder.build(true).toUriString();
    }

    private String buildSkyscannerLink(Tracking tracking) {
        boolean roundTrip = tracking.getTripType() != null && tracking.getTripType().isRoundTrip() && tracking.getReturnDate() != null;
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(SKYSCANNER_BASE_URL)
                .path(roundTrip
                        ? String.format("/transport/flights/%s/%s/%s/%s/",
                                normalizeAirportCode(tracking.getOrigin()),
                                normalizeAirportCode(tracking.getDestination()),
                                formatSkyscannerDate(tracking.getDepartureDate()),
                                formatSkyscannerDate(tracking.getReturnDate()))
                        : String.format("/transport/flights/%s/%s/%s/",
                                normalizeAirportCode(tracking.getOrigin()),
                                normalizeAirportCode(tracking.getDestination()),
                                formatSkyscannerDate(tracking.getDepartureDate())))
                .queryParam("adultsv2", tracking.getPassengers() == null ? 1 : tracking.getPassengers())
                .queryParam("cabinclass", "economy")
                .queryParam("childrenv2", "")
                .queryParam("ref", "home")
                .queryParam("rtn", roundTrip ? "1" : "0")
                .queryParam("preferdirects", "false")
                .queryParam("outboundaltsenabled", "false")
                .queryParam("inboundaltsenabled", "false")
                .queryParam("market", "KR")
                .queryParam("locale", "ko-KR")
                .queryParam("currency", "KRW");
        return builder.build(true).toUriString();
    }

    private String normalizeAirportCode(String code) {
        return code == null ? "" : code.trim().toLowerCase(Locale.ROOT);
    }

    private String formatSkyscannerDate(java.time.LocalDate date) {
        if (date == null) {
            return "";
        }
        return date.format(java.time.format.DateTimeFormatter.ofPattern("yyMMdd"));
    }

    private boolean isAccepted(ResponseEntity<Map> response) {
        return response.getStatusCodeValue() == 200;
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("카카오 메시지 템플릿 직렬화에 실패했습니다.", ex);
        }
    }

    private String formatWon(int value) {
        return "\u20a9" + NumberFormat.getNumberInstance(Locale.KOREA).format(value);
    }
}
