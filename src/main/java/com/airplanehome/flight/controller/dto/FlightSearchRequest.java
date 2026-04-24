package com.airplanehome.flight.controller.dto;

import com.airplanehome.flight.model.TripType;
import com.fasterxml.jackson.annotation.JsonAlias;
import java.time.LocalDate;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

public class FlightSearchRequest {
    private TripType tripType;
    @NotBlank
    private String origin;
    @NotBlank
    private String destination;
    @NotNull
    private LocalDate departureDate;
    private LocalDate returnDate;
    private Integer adults;
    @JsonAlias("adults")
    private Integer passengers;

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

    public Integer getAdults() {
        if (adults != null) {
            return adults;
        }
        return passengers;
    }

    public void setAdults(Integer adults) {
        this.adults = adults;
    }

    public Integer getPassengers() {
        return getAdults();
    }

    public void setPassengers(Integer passengers) {
        this.passengers = passengers;
    }
}
