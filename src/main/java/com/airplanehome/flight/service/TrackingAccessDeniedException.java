package com.airplanehome.flight.service;

public class TrackingAccessDeniedException extends RuntimeException {
    public TrackingAccessDeniedException(String message) {
        super(message);
    }
}
