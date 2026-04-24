package com.airplanehome.flight.model;

import com.airplanehome.flight.serialization.KstLocalDateTimeSerializer;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;

@Entity
public class Tracking {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String email;
    @Enumerated(EnumType.STRING)
    private TripType tripType;
    private String origin;
    private String destination;
    private LocalDate departureDate;
    private LocalDate returnDate;
    private Integer passengers;
    private BigDecimal targetPrice;
    private BigDecimal lastCheckedPrice;
    private BigDecimal lastNotifiedPrice;
    private String lastCheckedCurrency;
    @JsonSerialize(using = KstLocalDateTimeSerializer.class)
    private LocalDateTime lastUpdatedAt;
    private Boolean kakaoNotificationEnabled;
    private String phoneNumber;
    private Boolean kakaoOptIn;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public TripType getTripType() {
        return tripType;
    }

    public void setTripType(TripType tripType) {
        this.tripType = tripType;
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

    public BigDecimal getLastCheckedPrice() {
        return lastCheckedPrice;
    }

    public void setLastCheckedPrice(BigDecimal lastCheckedPrice) {
        this.lastCheckedPrice = lastCheckedPrice;
    }

    public BigDecimal getLastNotifiedPrice() {
        return lastNotifiedPrice;
    }

    public void setLastNotifiedPrice(BigDecimal lastNotifiedPrice) {
        this.lastNotifiedPrice = lastNotifiedPrice;
    }

    public String getLastCheckedCurrency() {
        return lastCheckedCurrency;
    }

    public void setLastCheckedCurrency(String lastCheckedCurrency) {
        this.lastCheckedCurrency = lastCheckedCurrency;
    }

    public LocalDateTime getLastUpdatedAt() {
        return lastUpdatedAt;
    }

    public void setLastUpdatedAt(LocalDateTime lastUpdatedAt) {
        this.lastUpdatedAt = lastUpdatedAt;
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
