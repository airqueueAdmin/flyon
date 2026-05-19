package com.airplanehome.flight.controller.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public class DailyPriceDto {
    private final LocalDate date;
    private final BigDecimal price;
    private final boolean approximate;
    private final String airline;

    public DailyPriceDto(LocalDate date, BigDecimal price, boolean approximate, String airline) {
        this.date = date;
        this.price = price;
        this.approximate = approximate;
        this.airline = airline;
    }

    public LocalDate getDate() { return date; }
    public BigDecimal getPrice() { return price; }
    public boolean isApproximate() { return approximate; }
    public String getAirline() { return airline; }
}