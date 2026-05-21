package com.airplanehome.flight.repository;

import com.airplanehome.flight.model.Restaurant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface RestaurantRepository extends JpaRepository<Restaurant, Long> {

    List<Restaurant> findByCityCodeOrderByRatingDescRatingCountDesc(String cityCode);

    Optional<Restaurant> findFirstByCityCodeOrderByCachedAtDesc(String cityCode);

    @Transactional
    void deleteByCityCode(String cityCode);
}
