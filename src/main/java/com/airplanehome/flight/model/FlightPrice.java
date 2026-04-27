package com.airplanehome.flight.model;

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
public class FlightPrice {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Enumerated(EnumType.STRING)
    private TripType tripType;
    private String origin;
    private String destination;
    private LocalDate departureDate;
    private LocalDate returnDate;
    private BigDecimal totalPrice;
    private BigDecimal price;
    private String currency;
    private String provider;
    private String bookingUrl;
    private String airline;
    private String outboundAirline;
    private String inboundAirline;
    private LocalDateTime departureTime;
    private LocalDateTime arrivalTime;
    private LocalDateTime returnDepartureTime;
    private LocalDateTime returnArrivalTime;
    private Integer passengers;
    private Boolean approximate;
    private LocalDate sourceDepartureDate;
    private String timeBucket;
    private LocalDateTime prefetchedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public BigDecimal getTotalPrice() {
        return totalPrice;
    }

    public void setTotalPrice(BigDecimal totalPrice) {
        this.totalPrice = totalPrice;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getBookingUrl() {
        return bookingUrl;
    }

    public void setBookingUrl(String bookingUrl) {
        this.bookingUrl = bookingUrl;
    }

    public String getAirline() {
        return airline;
    }

    public void setAirline(String airline) {
        this.airline = airline;
    }

    public String getOutboundAirline() {
        return outboundAirline;
    }

    public void setOutboundAirline(String outboundAirline) {
        this.outboundAirline = outboundAirline;
    }

    public String getInboundAirline() {
        return inboundAirline;
    }

    public void setInboundAirline(String inboundAirline) {
        this.inboundAirline = inboundAirline;
    }

    public LocalDateTime getDepartureTime() {
        return departureTime;
    }

    public void setDepartureTime(LocalDateTime departureTime) {
        this.departureTime = departureTime;
    }

    public LocalDateTime getArrivalTime() {
        return arrivalTime;
    }

    public void setArrivalTime(LocalDateTime arrivalTime) {
        this.arrivalTime = arrivalTime;
    }

    public LocalDateTime getReturnDepartureTime() {
        return returnDepartureTime;
    }

    public void setReturnDepartureTime(LocalDateTime returnDepartureTime) {
        this.returnDepartureTime = returnDepartureTime;
    }

    public LocalDateTime getReturnArrivalTime() {
        return returnArrivalTime;
    }

    public void setReturnArrivalTime(LocalDateTime returnArrivalTime) {
        this.returnArrivalTime = returnArrivalTime;
    }

    public Integer getPassengers() {
        return passengers;
    }

    public void setPassengers(Integer passengers) {
        this.passengers = passengers;
    }

    public Boolean getApproximate() {
        return approximate;
    }

    public void setApproximate(Boolean approximate) {
        this.approximate = approximate;
    }

    public LocalDate getSourceDepartureDate() {
        return sourceDepartureDate;
    }

    public void setSourceDepartureDate(LocalDate sourceDepartureDate) {
        this.sourceDepartureDate = sourceDepartureDate;
    }

    public String getTimeBucket() {
        return timeBucket;
    }

    public void setTimeBucket(String timeBucket) {
        this.timeBucket = timeBucket;
    }

    public LocalDateTime getPrefetchedAt() {
        return prefetchedAt;
    }

    public void setPrefetchedAt(LocalDateTime prefetchedAt) {
        this.prefetchedAt = prefetchedAt;
    }
}
