package com.airplanehome.flight.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@ConfigurationProperties(prefix = "app.kakao")
public class KakaoNotificationProperties {
    private boolean enabled = false;
    private String provider = "kakao-message-api";
    private String restApiKey;
    private String clientSecret;
    private String redirectUri;
    private String apiKey;
    private String apiSecret;
    private String senderNumber;
    private String templateCode;
    private String serviceId;
    private String plusFriendId;
    private BigDecimal minPriceDropKrw = BigDecimal.valueOf(10000);
    private BigDecimal minPriceDropPercent = BigDecimal.valueOf(5);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getRestApiKey() {
        return restApiKey;
    }

    public void setRestApiKey(String restApiKey) {
        this.restApiKey = restApiKey;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    public String getRedirectUri() {
        return redirectUri;
    }

    public void setRedirectUri(String redirectUri) {
        this.redirectUri = redirectUri;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getApiSecret() {
        return apiSecret;
    }

    public void setApiSecret(String apiSecret) {
        this.apiSecret = apiSecret;
    }

    public String getSenderNumber() {
        return senderNumber;
    }

    public void setSenderNumber(String senderNumber) {
        this.senderNumber = senderNumber;
    }

    public String getTemplateCode() {
        return templateCode;
    }

    public void setTemplateCode(String templateCode) {
        this.templateCode = templateCode;
    }

    public String getServiceId() {
        return serviceId;
    }

    public void setServiceId(String serviceId) {
        this.serviceId = serviceId;
    }

    public String getPlusFriendId() {
        return plusFriendId;
    }

    public void setPlusFriendId(String plusFriendId) {
        this.plusFriendId = plusFriendId;
    }

    public BigDecimal getMinPriceDropKrw() {
        return minPriceDropKrw;
    }

    public void setMinPriceDropKrw(BigDecimal minPriceDropKrw) {
        this.minPriceDropKrw = minPriceDropKrw;
    }

    public BigDecimal getMinPriceDropPercent() {
        return minPriceDropPercent;
    }

    public void setMinPriceDropPercent(BigDecimal minPriceDropPercent) {
        this.minPriceDropPercent = minPriceDropPercent;
    }

    public List<String> getMissingRequiredFields() {
        List<String> missingFields = new ArrayList<String>();
        if (!StringUtils.hasText(provider)) {
            missingFields.add("app.kakao.provider");
            return missingFields;
        }
        if (!"kakao-message-api".equalsIgnoreCase(provider)) {
            missingFields.add("app.kakao.provider(unsupported:" + provider + ")");
            return missingFields;
        }
        addIfMissing(missingFields, "app.kakao.rest-api-key", restApiKey);
        addIfMissing(missingFields, "app.kakao.redirect-uri", redirectUri);
        return missingFields;
    }

    public boolean isReady() {
        return enabled && getMissingRequiredFields().isEmpty();
    }

    private void addIfMissing(List<String> missingFields, String key, String value) {
        if (!StringUtils.hasText(value)) {
            missingFields.add(key);
        }
    }
}
