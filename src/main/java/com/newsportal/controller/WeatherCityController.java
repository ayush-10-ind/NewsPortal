package com.newsportal.controller;

import com.newsportal.service.WeatherCityService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/weather")
public class WeatherCityController {

    private final WeatherCityService weatherCityService;

    public WeatherCityController(
            WeatherCityService weatherCityService) {

        this.weatherCityService = weatherCityService;
    }

    @GetMapping("/city")
    public ResponseEntity<?> getWeatherByCity(
            @RequestParam("city") String city) {

        try {
            return ResponseEntity.ok(
                    weatherCityService.getWeatherByCity(city)
            );

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));

        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of(
                            "error",
                            e.getMessage() == null
                                    ? "Unable to find weather for that location."
                                    : e.getMessage()
                    ));
        }
    }
}
