package com.airplanehome.flight.repository;

import com.airplanehome.flight.model.Tracking;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TrackingRepository extends JpaRepository<Tracking, Long> {
    List<Tracking> findByOwnerTokenHashOrderByLastUpdatedAtDesc(String ownerTokenHash);

    List<Tracking> findByKakaoUserIdOrderByLastUpdatedAtDesc(Long kakaoUserId);
}
