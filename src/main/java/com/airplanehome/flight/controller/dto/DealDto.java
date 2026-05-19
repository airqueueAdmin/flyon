package com.airplanehome.flight.controller.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public class DealDto {
    private final String destination;
    private final BigDecimal price;
    private final LocalDate departureDate;
    private final String airline;
    private final boolean approximate;

    public DealDto(String destination, BigDecimal price, LocalDate departureDate, String airline, boolean approximate) {
        this.destination = destination;
        this.price = price;
        this.departureDate = departureDate;
        this.airline = airline;
        this.approximate = approximate;
    }

    public String getDestination() { return destination; }
    public BigDecimal getPrice() { return price; }
    public LocalDate getDepartureDate() { return departureDate; }
    public String getAirline() { return airline; }
    public boolean isApproximate() { return approximate; }
}