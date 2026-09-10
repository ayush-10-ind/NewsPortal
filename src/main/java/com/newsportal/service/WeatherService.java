package com.newsportal.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.netty.channel.ChannelOption;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class WeatherService {

    private static final Logger logger =
            LoggerFactory.getLogger(WeatherService.class);

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;

    private static final long CACHE_DURATION_MS =
            10 * 60 * 1000L;

    private final Map<String, CachedWeather> weatherCache =
            new ConcurrentHashMap<>();

    private static final Duration REQUEST_TIMEOUT =
            Duration.ofSeconds(5);

    private static final int CONNECT_TIMEOUT_MILLIS =
            4000;

    public WeatherService(
            @Value("${openweather.api.key}") String apiKey) {

        this.apiKey = apiKey;

        HttpClient httpClient =
                HttpClient.create()
                        .option(
                                ChannelOption.CONNECT_TIMEOUT_MILLIS,
                                CONNECT_TIMEOUT_MILLIS
                        )
                        .responseTimeout(
                                REQUEST_TIMEOUT
                        );

        this.webClient =
                WebClient.builder()
                        .clientConnector(
                                new ReactorClientHttpConnector(
                                        httpClient
                                )
                        )
                        .build();

        this.objectMapper =
                new ObjectMapper();
    }

    public Map<String, Object> getWeather(
            double latitude,
            double longitude) {

        validateCoordinates(latitude, longitude);

        String cacheKey =
                createCacheKey(latitude, longitude);

        CachedWeather cached =
                weatherCache.get(cacheKey);

        if (cached != null && !cached.isExpired()) {
            logger.debug("Weather cache hit: key={}", cacheKey);
            return cached.data;
        }

        logger.debug("Weather cache miss: key={}", cacheKey);

        try {
            Mono<String> currentRequest =
                    webClient
                            .get()
                            .uri(uriBuilder ->
                                    uriBuilder
                                            .scheme("https")
                                            .host("api.openweathermap.org")
                                            .path("/data/2.5/weather")
                                            .queryParam("lat", latitude)
                                            .queryParam("lon", longitude)
                                            .queryParam("appid", apiKey)
                                            .queryParam("units", "metric")
                                            .queryParam("lang", "en")
                                            .build()
                            )
                            .retrieve()
                            .bodyToMono(String.class)
                            .timeout(REQUEST_TIMEOUT);

            Mono<String> forecastRequest =
                    webClient
                            .get()
                            .uri(uriBuilder ->
                                    uriBuilder
                                            .scheme("https")
                                            .host("api.openweathermap.org")
                                            .path("/data/2.5/forecast")
                                            .queryParam("lat", latitude)
                                            .queryParam("lon", longitude)
                                            .queryParam("appid", apiKey)
                                            .queryParam("units", "metric")
                                            .queryParam("lang", "en")
                                            .build()
                            )
                            .retrieve()
                            .bodyToMono(String.class)
                            .timeout(REQUEST_TIMEOUT)
                            .onErrorReturn(createEmptyForecastResponse());

            Map<String, String> responses =
                    Mono.zip(currentRequest, forecastRequest)
                            .map(tuple -> {
                                Map<String, String> data = new HashMap<>();
                                data.put("current", tuple.getT1());
                                data.put("forecast", tuple.getT2());
                                return data;
                            })
                            .block();

            if (responses == null) {
                throw new RuntimeException("No weather response received.");
            }

            JsonNode current =
                    objectMapper.readTree(responses.get("current"));

            JsonNode forecast =
                    objectMapper.readTree(responses.get("forecast"));

            Map<String, Object> result = new HashMap<>();

            Map<String, Object> locationData = new HashMap<>();
            locationData.put("latitude", latitude);
            locationData.put("longitude", longitude);
            locationData.put(
                    "city",
                    current.path("name").asText("Unknown")
            );
            locationData.put("state", "");
            locationData.put(
                    "country",
                    current.path("sys").path("country").asText("")
            );
            result.put("location", locationData);

            Map<String, Object> currentData = new HashMap<>();
            JsonNode main = current.path("main");
            JsonNode weather = current.path("weather").get(0);
            JsonNode wind = current.path("wind");

            if (weather == null || weather.isMissingNode()) {
                throw new RuntimeException(
                        "Weather information was not returned by OpenWeather."
                );
            }

            currentData.put("temperature", main.path("temp").asDouble());
            currentData.put("feelsLike", main.path("feels_like").asDouble());
            currentData.put("minTemperature", main.path("temp_min").asDouble());
            currentData.put("maxTemperature", main.path("temp_max").asDouble());
            currentData.put("humidity", main.path("humidity").asInt());
            currentData.put("pressure", main.path("pressure").asInt());
            currentData.put("windSpeed", wind.path("speed").asDouble());
            currentData.put("windDirection", wind.path("deg").asInt());
            currentData.put("visibility", current.path("visibility").asInt());
            currentData.put("condition", weather.path("main").asText("Unknown"));
            currentData.put(
                    "description",
                    weather.path("description")
                            .asText("Weather information unavailable")
            );
            currentData.put("icon", weather.path("icon").asText(""));
            currentData.put(
                    "sunrise",
                    current.path("sys").path("sunrise").asLong()
            );
            currentData.put(
                    "sunset",
                    current.path("sys").path("sunset").asLong()
            );
            result.put("current", currentData);

            List<Map<String, Object>> forecastList = new ArrayList<>();
            JsonNode forecastArray = forecast.path("list");

            if (forecastArray.isArray()) {
                for (JsonNode item : forecastArray) {
                    Map<String, Object> itemData = new HashMap<>();
                    JsonNode itemMain = item.path("main");
                    JsonNode itemWeather = item.path("weather").get(0);

                    if (itemWeather == null || itemWeather.isMissingNode()) {
                        continue;
                    }

                    itemData.put("timestamp", item.path("dt").asLong());
                    itemData.put("temperature", itemMain.path("temp").asDouble());
                    itemData.put("feelsLike", itemMain.path("feels_like").asDouble());
                    itemData.put("humidity", itemMain.path("humidity").asInt());
                    itemData.put("condition", itemWeather.path("main").asText("Unknown"));
                    itemData.put("description", itemWeather.path("description").asText(""));
                    itemData.put("icon", itemWeather.path("icon").asText(""));
                    forecastList.add(itemData);
                }
            }

            result.put("forecast", forecastList);

            weatherCache.put(
                    cacheKey,
                    new CachedWeather(result)
            );

            logger.debug(
                    "Weather response cached: key={}, forecastItems={}",
                    cacheKey,
                    forecastList.size()
            );

            return result;

        } catch (Exception e) {
            CachedWeather oldCache = weatherCache.get(cacheKey);

            if (oldCache != null) {
                logger.warn(
                        "OpenWeather request failed; using stale cache: key={}, errorType={}, message={}",
                        cacheKey,
                        e.getClass().getSimpleName(),
                        e.getMessage()
                );
                return oldCache.data;
            }

            logger.error(
                    "OpenWeather request failed: key={}, errorType={}, message={}",
                    cacheKey,
                    e.getClass().getSimpleName(),
                    e.getMessage()
            );

            throw new RuntimeException(
                    "Unable to fetch weather data: "
                            + e.getMessage(),
                    e
            );
        }
    }

    private String createEmptyForecastResponse() {
        return "{\"list\":[]}";
    }

    private String createCacheKey(
            double latitude,
            double longitude) {
        return String.format("%.4f,%.4f", latitude, longitude);
    }

    private void validateCoordinates(
            double latitude,
            double longitude) {

        if (Double.isNaN(latitude)
                || Double.isInfinite(latitude)
                || latitude < -90
                || latitude > 90) {
            throw new IllegalArgumentException("Invalid latitude.");
        }

        if (Double.isNaN(longitude)
                || Double.isInfinite(longitude)
                || longitude < -180
                || longitude > 180) {
            throw new IllegalArgumentException("Invalid longitude.");
        }
    }

    private static class CachedWeather {

        private final Map<String, Object> data;
        private final long createdAt;

        private CachedWeather(Map<String, Object> data) {
            this.data = data;
            this.createdAt = System.currentTimeMillis();
        }

        private boolean isExpired() {
            return System.currentTimeMillis()
                    - createdAt
                    > CACHE_DURATION_MS;
        }
    }
}