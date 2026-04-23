package com.airplanehome.flight.service;

import java.math.BigDecimal;
import java.time.LocalDate;

public class PriceDropNotification {
    private final Long trackingId;
    private final String origin;
    private final String destination;
    private final LocalDate departureDate;
    private final BigDecimal previousPrice;
    private final BigDecimal currentPrice;
    private final String currency;

    public PriceDropNotification(Long trackingId,
                                 String origin,
                                 String destination,
                                 LocalDate departureDate,
                                 BigDecimal previousPrice,
                                 BigDecimal currentPrice,
                                 String currency) {
        this.trackingId = trackingId;
        this.origin = origin;
        this.destination = destination;
        this.departureDate = departureDate;
        this.previousPrice = previousPrice;
        this.currentPrice = currentPrice;
        this.currency = currency;
    }

    public Long getTrackingId() {
        return trackingId;
    }

    public String getOrigin() {
        return origin;
    }

    public String getDestination() {
        return destination;
    }

    public LocalDate getDepartureDate() {
        return departureDate;
    }

    public BigDecimal getPreviousPrice() {
        return previousPrice;
    }

    public BigDecimal getCurrentPrice() {
        return currentPrice;
    }

    public String getCurrency() {
        return currency;
    }
}
