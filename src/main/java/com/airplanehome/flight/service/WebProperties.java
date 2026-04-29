package com.airplanehome.flight.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.web")
public class WebProperties {
    private String baseUrl = "https://flight-platform.onrender.com";
    private String gaId = "";
    private String naverAnalyticsId = "";

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getGaId() {
        return gaId;
    }

    public void setGaId(String gaId) {
        this.gaId = gaId;
    }

    public String getNaverAnalyticsId() {
        return naverAnalyticsId;
    }

    public void setNaverAnalyticsId(String naverAnalyticsId) {
        this.naverAnalyticsId = naverAnalyticsId;
    }
}
