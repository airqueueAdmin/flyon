package com.airplanehome.flight.controller.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import javax.validation.constraints.NotNull;

public class TrackingRequest {
    private String email;
    @NotNull
    private String origin;
    @NotNull
    private String destination;
    @NotNull
    private LocalDate departureDate;
    private LocalDate returnDate;
    private Integer passengers;
    private BigDecimal targetPrice;
    private Boolean kakaoNotificationEnabled;
    private String phoneNumber;
    private Boolean kakaoOptIn;

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getOrigin() {
        return origin;
    }

    public void setOrigin(String origin) {
        this.origin = origin;
    }

    public String getDestination() {
        return destination;
    }

    public void setDestination(String destination) {
        this.destination = destination;
    }

    public LocalDate getDepartureDate() {
        return departureDate;
    }

    public void setDepartureDate(LocalDate departureDate) {
        this.departureDate = departureDate;
    }

    public LocalDate getReturnDate() {
        return returnDate;
    }

    public void setReturnDate(LocalDate returnDate) {
        this.returnDate = returnDate;
    }

    public Integer getPassengers() {
        return passengers;
    }

    public void setPassengers(Integer passengers) {
        this.passengers = passengers;
    }

    public BigDecimal getTargetPrice() {
        return targetPrice;
    }

    public void setTargetPrice(BigDecimal targetPrice) {
        this.targetPrice = targetPrice;
    }

    public Boolean getKakaoNotificationEnabled() {
        return kakaoNotificationEnabled;
    }

    public void setKakaoNotificationEnabled(Boolean kakaoNotificationEnabled) {
        this.kakaoNotificationEnabled = kakaoNotificationEnabled;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public Boolean getKakaoOptIn() {
        return kakaoOptIn;
    }

    public void setKakaoOptIn(Boolean kakaoOptIn) {
        this.kakaoOptIn = kakaoOptIn;
    }
}
