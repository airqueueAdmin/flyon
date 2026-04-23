package com.airplanehome.flight.repository;

import com.airplanehome.flight.model.Tracking;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TrackingRepository extends JpaRepository<Tracking, Long> {
}
