package com.airplanehome.flight.service;

import com.airplanehome.flight.model.KakaoAuthConnection;
import com.airplanehome.flight.repository.KakaoAuthConnectionRepository;
import com.airplanehome.flight.time.TimeSupport;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import javax.persistence.EntityNotFoundException;
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

@Service
public class KakaoAuthService {
    private static final String AUTHORIZE_URL = "https://kauth.kakao.com/oauth/authorize";
    private static final String TOKEN_URL = "https://kauth.kakao.com/oauth/token";
    private static final String USER_INFO_URL = "https://kapi.kakao.com/v2/user/me";

    private final RestTemplate restTemplate;
    private final KakaoNotificationProperties properties;
    private final KakaoAuthConnectionRepository connectionRepository;

    public KakaoAuthService(RestTemplate restTemplate,
                            KakaoNotificationProperties properties,
                            KakaoAuthConnectionRepository connectionRepository) {
        this.restTemplate = restTemplate;
        this.properties = properties;
        this.connectionRepository = connectionRepository;
    }

    public Map<String, Object> authStart() {
        if (!properties.isReady()) {
            throw new IllegalStateException("카카오 로그인을 시작할 수 없습니다. 앱 설정을 먼저 확인해 주세요.");
        }

        String state = UUID.randomUUID().toString();
        Map<String, Object> response = new LinkedHashMap<String, Object>();
        response.put("state", state);
        response.put("authorizationUrl", buildAuthorizationUrl(state));
        return response;
    }

    public KakaoAuthConnection createConnection(String code) {
        Map tokenResponse = exchangeAuthorizationCode(code);
        String accessToken = stringValue(tokenResponse.get("access_token"));
        if (!StringUtils.hasText(accessToken)) {
            throw new IllegalStateException("카카오 액세스 토큰을 받지 못했습니다.");
        }

        Map userResponse = getUserInfo(accessToken);
        Long kakaoUserId = longValue(userResponse.get("id"));
        if (kakaoUserId == null) {
            throw new IllegalStateException("카카오 사용자 정보를 확인하지 못했습니다.");
        }

        KakaoAuthConnection connection = new KakaoAuthConnection();
        connection.setId(UUID.randomUUID().toString());
        connection.setKakaoUserId(kakaoUserId);
        connection.setNickname(resolveNickname(userResponse));
        connection.setAccessToken(accessToken);
        connection.setRefreshToken(stringValue(tokenResponse.get("refresh_token")));
        connection.setAccessTokenExpiresAt(resolveExpiry(tokenResponse.get("expires_in")));
        connection.setRefreshTokenExpiresAt(resolveExpiry(tokenResponse.get("refresh_token_expires_in")));
        connection.setCreatedAt(TimeSupport.nowKst());
        return connectionRepository.save(connection);
    }

    public KakaoAuthConnection getConnection(String connectionId) {
        return connectionRepository.findById(connectionId)
                .orElseThrow(() -> new EntityNotFoundException("카카오 연결 정보를 찾을 수 없습니다. 다시 로그인해 주세요."));
    }

    public Map<String, Object> status() {
        Map<String, Object> status = new LinkedHashMap<String, Object>();
        status.put("enabled", Boolean.valueOf(properties.isEnabled()));
        status.put("ready", Boolean.valueOf(properties.isReady()));
        status.put("provider", properties.getProvider());
        status.put("redirectUri", properties.getRedirectUri());
        status.put("missingConfiguration", properties.getMissingRequiredFields());
        return status;
    }

    public void refreshTrackingTokensIfNeeded(com.airplanehome.flight.model.Tracking tracking) {
        if (!StringUtils.hasText(tracking.getKakaoRefreshToken())) {
            return;
        }
        if (tracking.getKakaoAccessTokenExpiresAt() != null
                && tracking.getKakaoAccessTokenExpiresAt().isAfter(TimeSupport.nowKst().plusMinutes(1))) {
            return;
        }

        Map refreshed = refreshAccessToken(tracking.getKakaoRefreshToken());
        tracking.setKakaoAccessToken(stringValue(refreshed.get("access_token")));
        tracking.setKakaoAccessTokenExpiresAt(resolveExpiry(refreshed.get("expires_in")));

        String refreshedRefreshToken = stringValue(refreshed.get("refresh_token"));
        if (StringUtils.hasText(refreshedRefreshToken)) {
            tracking.setKakaoRefreshToken(refreshedRefreshToken);
            tracking.setKakaoRefreshTokenExpiresAt(resolveExpiry(refreshed.get("refresh_token_expires_in")));
        }
    }

    private String buildAuthorizationUrl(String state) {
        return AUTHORIZE_URL
                + "?response_type=code"
                + "&client_id=" + encode(properties.getRestApiKey())
                + "&redirect_uri=" + encode(properties.getRedirectUri())
                + "&scope=" + encode("talk_message")
                + "&state=" + encode(state);
    }

    private Map exchangeAuthorizationCode(String code) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<String, String>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", properties.getRestApiKey());
        form.add("redirect_uri", properties.getRedirectUri());
        form.add("code", code);
        addClientSecret(form);
        return postForm(TOKEN_URL, form, null);
    }

    private Map refreshAccessToken(String refreshToken) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<String, String>();
        form.add("grant_type", "refresh_token");
        form.add("client_id", properties.getRestApiKey());
        form.add("refresh_token", refreshToken);
        addClientSecret(form);
        return postForm(TOKEN_URL, form, null);
    }

    private Map getUserInfo(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        ResponseEntity<Map> response = restTemplate.exchange(
                USER_INFO_URL,
                HttpMethod.GET,
                new HttpEntity<Void>(headers),
                Map.class);
        return response.getBody();
    }

    private Map postForm(String url, MultiValueMap<String, String> form, HttpHeaders headers) {
        HttpHeaders requestHeaders = headers == null ? new HttpHeaders() : headers;
        requestHeaders.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    new HttpEntity<MultiValueMap<String, String>>(form, requestHeaders),
                    Map.class);
            Map body = response.getBody();
            if (body == null) {
                throw new IllegalStateException("카카오 응답 본문이 비어 있습니다.");
            }
            return body;
        } catch (RestClientException ex) {
            throw new IllegalStateException("카카오 API 호출에 실패했습니다. " + ex.getMessage(), ex);
        }
    }

    private void addClientSecret(MultiValueMap<String, String> form) {
        if (StringUtils.hasText(properties.getClientSecret())) {
            form.add("client_secret", properties.getClientSecret());
        }
    }

    private LocalDateTime resolveExpiry(Object expiresInSeconds) {
        Long seconds = longValue(expiresInSeconds);
        if (seconds == null) {
            return null;
        }
        return TimeSupport.nowKst().plusSeconds(seconds.longValue());
    }

    private String resolveNickname(Map userResponse) {
        Object propertiesObject = userResponse.get("properties");
        if (propertiesObject instanceof Map) {
            Object nickname = ((Map) propertiesObject).get("nickname");
            if (nickname != null) {
                return String.valueOf(nickname);
            }
        }
        return "카카오 사용자";
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Long longValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return Long.valueOf(((Number) value).longValue());
        }
        try {
            return Long.valueOf(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
