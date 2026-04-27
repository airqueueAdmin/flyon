package com.airplanehome.flight.service;

import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import com.airplanehome.flight.model.Tracking;
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
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class KakaoNotificationService {
    private static final Logger log = LoggerFactory.getLogger(KakaoNotificationService.class);
    private static final String TEMPLATE_CONTENT =
            "[항공권 가격 변동 안내]\n\n#{route} 항공권 가격이 변경되었습니다.\n\n이전 가격: #{oldPrice}\n현재 가격: #{newPrice}\n\n자세히 보기:\n#{link}";
    private static final String NCP_BASE_URL = "https://sens.apigw.ntruss.com";
    private static final String SKYSCANNER_BASE_URL = "https://www.skyscanner.co.kr";

    private final RestTemplate restTemplate;
    private final boolean enabled;
    private final String provider;
    private final String apiKey;
    private final String apiSecret;
    private final String senderNumber;
    private final String templateCode;
    private final String serviceId;
    private final String plusFriendId;
    private final String appBaseUrl;

    public KakaoNotificationService(RestTemplate restTemplate,
                                    @Value("${app.kakao.enabled:false}") boolean enabled,
                                    @Value("${app.kakao.provider:ncp-sens}") String provider,
                                    @Value("${app.kakao.api-key:}") String apiKey,
                                    @Value("${app.kakao.api-secret:}") String apiSecret,
                                    @Value("${app.kakao.sender-number:}") String senderNumber,
                                    @Value("${app.kakao.template-code:}") String templateCode,
                                    @Value("${app.kakao.service-id:}") String serviceId,
                                    @Value("${app.kakao.plus-friend-id:}") String plusFriendId,
                                    @Value("${app.web.base-url}") String appBaseUrl) {
        this.restTemplate = restTemplate;
        this.enabled = enabled;
        this.provider = provider;
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
        this.senderNumber = senderNumber;
        this.templateCode = templateCode;
        this.serviceId = serviceId;
        this.plusFriendId = plusFriendId;
        this.appBaseUrl = appBaseUrl;
    }

    public boolean sendAlimTalk(Tracking tracking, int oldPrice, int newPrice) {
        String route = tracking.getOrigin() + "→" + tracking.getDestination();
        if (!enabled) {
            log.error("KAKAO_FAILED: disabled {}", route);
            return false;
        }
        if (!"ncp-sens".equalsIgnoreCase(provider)) {
            log.error("KAKAO_FAILED: unsupported provider {}", provider);
            return false;
        }
        String normalizedPhoneNumber = normalizePhoneNumber(tracking.getPhoneNumber());
        if (!hasRequiredConfiguration() || !StringUtils.hasText(normalizedPhoneNumber)) {
            log.error("KAKAO_FAILED: missing configuration or recipient {}", route);
            return false;
        }

        String path = "/alimtalk/v2/services/" + serviceId + "/messages";
        Map<String, Object> payload = buildPayload(tracking, normalizedPhoneNumber, oldPrice, newPrice);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        addNcpSignatureHeaders(headers, path);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    NCP_BASE_URL + path,
                    HttpMethod.POST,
                    new HttpEntity<Map<String, Object>>(payload, headers),
                    Map.class);
            boolean accepted = isAccepted(response);
            if (accepted) {
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
        tracking.setPhoneNumber("01012345678");
        tracking.setDepartureDate(java.time.LocalDate.of(2026, 5, 1));
        return buildPayload(tracking, normalizePhoneNumber(tracking.getPhoneNumber()), 320000, 270000);
    }

    private Map<String, Object> buildPayload(Tracking tracking, String normalizedPhoneNumber, int oldPrice, int newPrice) {
        Map<String, String> variables = buildTemplateVariables(tracking, oldPrice, newPrice);
        String content = applyTemplate(variables);

        Map<String, Object> message = new LinkedHashMap<String, Object>();
        message.put("to", normalizedPhoneNumber);
        message.put("content", content);
        message.put("buttons", buildButtons(tracking));

        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("plusFriendId", plusFriendId);
        payload.put("templateCode", templateCode);
        payload.put("messages", java.util.Collections.singletonList(message));
        payload.put("templateVariables", variables);
        return payload;
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

    private List<Map<String, String>> buildButtons(Tracking tracking) {
        List<Map<String, String>> buttons = new java.util.ArrayList<Map<String, String>>();
        buttons.add(buildWebLinkButton("추적 목록 보기", buildTrackingPageLink(tracking)));
        buttons.add(buildWebLinkButton("스카이스캐너 이동", buildSkyscannerLink(tracking)));
        return buttons;
    }

    private Map<String, String> buildWebLinkButton(String name, String link) {
        Map<String, String> button = new LinkedHashMap<String, String>();
        button.put("type", "WL");
        button.put("name", name);
        button.put("linkMobile", link);
        button.put("linkPc", link);
        return button;
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

    private boolean hasRequiredConfiguration() {
        return StringUtils.hasText(apiKey)
                && StringUtils.hasText(apiSecret)
                && StringUtils.hasText(senderNumber)
                && StringUtils.hasText(templateCode)
                && StringUtils.hasText(serviceId)
                && StringUtils.hasText(plusFriendId);
    }

    private boolean isAccepted(ResponseEntity<Map> response) {
        if (response.getStatusCodeValue() != 202) {
            return false;
        }
        Map body = response.getBody();
        if (body == null) {
            return true;
        }
        Object messagesObject = body.get("messages");
        if (!(messagesObject instanceof List) || ((List) messagesObject).isEmpty()) {
            return true;
        }
        Object firstMessage = ((List) messagesObject).get(0);
        if (!(firstMessage instanceof Map)) {
            return true;
        }
        Object requestStatusCode = ((Map) firstMessage).get("requestStatusCode");
        return requestStatusCode == null || "A000".equals(String.valueOf(requestStatusCode));
    }

    private void addNcpSignatureHeaders(HttpHeaders headers, String path) {
        String timestamp = String.valueOf(System.currentTimeMillis());
        headers.set("x-ncp-apigw-timestamp", timestamp);
        headers.set("x-ncp-iam-access-key", apiKey);
        headers.set("x-ncp-apigw-signature-v2", createSignature("POST", path, timestamp));
    }

    private String createSignature(String method, String path, String timestamp) {
        String message = method + " " + path + "\n" + timestamp + "\n" + apiKey;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(apiSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] rawHmac = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(rawHmac);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to create NCP signature", ex);
        }
    }

    private String formatWon(int value) {
        return "\u20a9" + NumberFormat.getNumberInstance(Locale.KOREA).format(value);
    }

    private String normalizePhoneNumber(String phoneNumber) {
        if (!StringUtils.hasText(phoneNumber)) {
            return null;
        }

        String digits = phoneNumber.replaceAll("[^0-9]", "");
        if (digits.startsWith("82")) {
            return digits;
        }
        if (digits.startsWith("0")) {
            return "82" + digits.substring(1);
        }
        return digits;
    }
}
