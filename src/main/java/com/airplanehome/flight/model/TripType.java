package com.airplanehome.flight.model;

public enum TripType {
    ONE_WAY,
    ROUND_TRIP;

    public boolean isRoundTrip() {
        return this == ROUND_TRIP;
    }
}
