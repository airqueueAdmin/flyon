package com.airplanehome.flight.controller;

import com.airplanehome.flight.model.Restaurant;
import com.airplanehome.flight.service.RestaurantService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class RestaurantController {

    private final RestaurantService restaurantService;

    public RestaurantController(RestaurantService restaurantService) {
        this.restaurantService = restaurantService;
    }

    @GetMapping("/restaurants")
    public List<Restaurant> getRestaurants(@RequestParam String city) {
        return restaurantService.getRestaurants(city);
    }

    @GetMapping("/restaurants/cities")
    public List<RestaurantService.CityDto> getCities() {
        return restaurantService.getSupportedCities();
    }
}
