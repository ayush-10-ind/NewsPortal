package com.newsportal.controller;

import com.newsportal.service.WeatherService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/weather")
public class WeatherController {

    private final WeatherService weatherService;


    // =====================================================
    // CONSTRUCTOR
    // =====================================================

    public WeatherController(
            WeatherService weatherService) {

        this.weatherService =
                weatherService;
    }


    // =====================================================
    // GET WEATHER
    // =====================================================

    @GetMapping
    public ResponseEntity<?> getWeather(

            @RequestParam("lat")
            double latitude,

            @RequestParam("lon")
            double longitude) {

        try {

            Map<String, Object> weather =
                    weatherService.getWeather(
                            latitude,
                            longitude
                    );


            return ResponseEntity.ok(
                    weather
            );


        } catch (IllegalArgumentException e) {

            return ResponseEntity
                    .badRequest()
                    .body(
                            Map.of(
                                    "error",
                                    e.getMessage()
                            )
                    );


        } catch (Exception e) {

            e.printStackTrace();


            return ResponseEntity
                    .internalServerError()
                    .body(
                            Map.of(
                                    "error",
                                    "Unable to fetch weather data."
                            )
                    );
        }
    }
}