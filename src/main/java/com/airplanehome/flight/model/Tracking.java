package com.airplanehome.flight.model;

import com.airplanehome.flight.serialization.KstLocalDateTimeSerializer;
import com.fasterxml.jackson.annotation.JsonIgnore;
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
    @JsonIgnore
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
    private String lastBookingUrl;
    private String lastAirline;
    private String lastInboundAirline;
    @JsonSerialize(using = KstLocalDateTimeSerializer.class)
    private LocalDateTime lastDepartureTime;
    @JsonSerialize(using = KstLocalDateTimeSerializer.class)
    private LocalDateTime lastArrivalTime;
    @JsonSerialize(using = KstLocalDateTimeSerializer.class)
    private LocalDateTime lastReturnDepartureTime;
    @JsonSerialize(using = KstLocalDateTimeSerializer.class)
    private LocalDateTime lastReturnArrivalTime;
    @JsonSerialize(using = KstLocalDateTimeSerializer.class)
    private LocalDateTime lastUpdatedAt;
    private Boolean kakaoNotificationEnabled;
    @JsonIgnore
    private String phoneNumber;
    private Boolean kakaoOptIn;
    private Boolean personalDataConsent;
    @JsonSerialize(using = KstLocalDateTimeSerializer.class)
    private LocalDateTime personalDataConsentAt;
    @JsonSerialize(using = KstLocalDateTimeSerializer.class)
    private LocalDateTime kakaoOptInAt;

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

    public String getLastBookingUrl() {
        return lastBookingUrl;
    }

    public void setLastBookingUrl(String lastBookingUrl) {
        this.lastBookingUrl = lastBookingUrl;
    }

    public String getLastAirline() {
        return lastAirline;
    }

    public void setLastAirline(String lastAirline) {
        this.lastAirline = lastAirline;
    }

    public String getLastInboundAirline() {
        return lastInboundAirline;
    }

    public void setLastInboundAirline(String lastInboundAirline) {
        this.lastInboundAirline = lastInboundAirline;
    }

    public LocalDateTime getLastDepartureTime() {
        return lastDepartureTime;
    }

    public void setLastDepartureTime(LocalDateTime lastDepartureTime) {
        this.lastDepartureTime = lastDepartureTime;
    }

    public LocalDateTime getLastArrivalTime() {
        return lastArrivalTime;
    }

    public void setLastArrivalTime(LocalDateTime lastArrivalTime) {
        this.lastArrivalTime = lastArrivalTime;
    }

    public LocalDateTime getLastReturnDepartureTime() {
        return lastReturnDepartureTime;
    }

    public void setLastReturnDepartureTime(LocalDateTime lastReturnDepartureTime) {
        this.lastReturnDepartureTime = lastReturnDepartureTime;
    }

    public LocalDateTime getLastReturnArrivalTime() {
        return lastReturnArrivalTime;
    }

    public void setLastReturnArrivalTime(LocalDateTime lastReturnArrivalTime) {
        this.lastReturnArrivalTime = lastReturnArrivalTime;
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

    public String getMaskedPhoneNumber() {
        if (phoneNumber == null || phoneNumber.length() < 8) {
            return null;
        }
        return phoneNumber.substring(0, 3) + "****" + phoneNumber.substring(phoneNumber.length() - 4);
    }

    public Boolean getKakaoOptIn() {
        return kakaoOptIn;
    }

    public void setKakaoOptIn(Boolean kakaoOptIn) {
        this.kakaoOptIn = kakaoOptIn;
    }

    public Boolean getPersonalDataConsent() {
        return personalDataConsent;
    }

    public void setPersonalDataConsent(Boolean personalDataConsent) {
        this.personalDataConsent = personalDataConsent;
    }

    public LocalDateTime getPersonalDataConsentAt() {
        return personalDataConsentAt;
    }

    public void setPersonalDataConsentAt(LocalDateTime personalDataConsentAt) {
        this.personalDataConsentAt = personalDataConsentAt;
    }

    public LocalDateTime getKakaoOptInAt() {
        return kakaoOptInAt;
    }

    public void setKakaoOptInAt(LocalDateTime kakaoOptInAt) {
        this.kakaoOptInAt = kakaoOptInAt;
    }
}
