package com.newsportal.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.netty.channel.ChannelOption;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Service
public class WeatherCityService {

    private static final Duration REQUEST_TIMEOUT =
            Duration.ofSeconds(5);

    private static final int CONNECT_TIMEOUT_MILLIS = 4000;

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final WeatherService weatherService;
    private final String apiKey;

    public WeatherCityService(
            WeatherService weatherService,
            @Value("${openweather.api.key}") String apiKey) {

        this.weatherService = weatherService;
        this.apiKey = apiKey;
        this.objectMapper = new ObjectMapper();

        HttpClient httpClient =
                HttpClient.create()
                        .option(
                                ChannelOption.CONNECT_TIMEOUT_MILLIS,
                                CONNECT_TIMEOUT_MILLIS
                        )
                        .responseTimeout(REQUEST_TIMEOUT);

        this.webClient = WebClient.builder()
                .clientConnector(
                        new ReactorClientHttpConnector(httpClient)
                )
                .build();
    }

    public Map<String, Object> getWeatherByCity(String city) {

        String query = city == null ? "" : city.trim();

        if (query.isBlank()) {
            throw new IllegalArgumentException(
                    "Please enter a city or place name."
            );
        }

        if (query.length() > 100) {
            throw new IllegalArgumentException(
                    "City or place name is too long."
            );
        }

        try {
            String response = webClient
                    .get()
                    .uri(uriBuilder -> uriBuilder
                            .scheme("https")
                            .host("api.openweathermap.org")
                            .path("/geo/1.0/direct")
                            .queryParam("q", query)
                            .queryParam("limit", 1)
                            .queryParam("appid", apiKey)
                            .build())
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(REQUEST_TIMEOUT)
                    .block();

            if (response == null || response.isBlank()) {
                throw new IllegalArgumentException(
                        "No location was found for '" + query + "'."
                );
            }

            JsonNode locations = objectMapper.readTree(response);

            if (!locations.isArray() || locations.isEmpty()) {
                throw new IllegalArgumentException(
                        "No location was found for '" + query + "'."
                );
            }

            JsonNode location = locations.get(0);

            double latitude = location.path("lat").asDouble(Double.NaN);
            double longitude = location.path("lon").asDouble(Double.NaN);

            if (Double.isNaN(latitude) || Double.isNaN(longitude)) {
                throw new IllegalArgumentException(
                        "The selected location does not have valid coordinates."
                );
            }

            Map<String, Object> weather =
                    weatherService.getWeather(latitude, longitude);

            Map<String, Object> locationData =
                    new HashMap<>();

            locationData.put("latitude", latitude);
            locationData.put("longitude", longitude);
            locationData.put(
                    "city",
                    location.path("name").asText(query)
            );
            locationData.put(
                    "state",
                    location.path("state").asText("")
            );
            locationData.put(
                    "country",
                    location.path("country").asText("")
            );

            weather.put("location", locationData);

            return weather;

        } catch (IllegalArgumentException e) {
            throw e;

        } catch (Exception e) {
            throw new RuntimeException(
                    "Unable to find weather for '" + query + "'.",
                    e
            );
        }
    }
}
