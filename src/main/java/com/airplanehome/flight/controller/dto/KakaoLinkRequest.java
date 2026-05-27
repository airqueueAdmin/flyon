package com.airplanehome.flight.controller.dto;

public class KakaoLinkRequest {
    private String kakaoConnectionId;
    private Boolean kakaoOptIn;

    public String getKakaoConnectionId() {
        return kakaoConnectionId;
    }

    public void setKakaoConnectionId(String kakaoConnectionId) {
        this.kakaoConnectionId = kakaoConnectionId;
    }

    public Boolean getKakaoOptIn() {
        return kakaoOptIn;
    }

    public void setKakaoOptIn(Boolean kakaoOptIn) {
        this.kakaoOptIn = kakaoOptIn;
    }
}
