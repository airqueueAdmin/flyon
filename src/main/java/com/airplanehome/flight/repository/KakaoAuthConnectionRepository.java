package com.airplanehome.flight.repository;

import com.airplanehome.flight.model.KakaoAuthConnection;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KakaoAuthConnectionRepository extends JpaRepository<KakaoAuthConnection, String> {
}
