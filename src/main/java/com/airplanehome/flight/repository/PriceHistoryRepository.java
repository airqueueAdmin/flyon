package com.airplanehome.flight.repository;

import com.airplanehome.flight.model.PriceHistory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PriceHistoryRepository extends JpaRepository<PriceHistory, Long> {
    void deleteByTrackingId(Long trackingId);
    List<PriceHistory> findByTrackingIdOrderByCheckedAtAsc(Long trackingId);
}
