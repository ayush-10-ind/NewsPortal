package com.newsportal.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;

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
import java.util.concurrent.TimeUnit;

@Service
public class WeatherService {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;

    /*
     * ============================================================
     * WEATHER CACHE
     * ============================================================
     *
     * Weather does NOT need to be fetched every time the user
     * opens /weather.
     *
     * Cache is kept for 10 minutes.
     *
     * Key:
     * latitude + longitude
     *
     * Example:
     * 25.4358,81.8463
     *
     * This means refreshing the weather page will normally be
     * almost instant after the first successful request.
     */
    private static final long CACHE_DURATION_MS =
            10 * 60 * 1000L;

    private final Map<String, CachedWeather> weatherCache =
            new ConcurrentHashMap<>();


    // ============================================================
    // CONSTRUCTOR
    // ============================================================

    public WeatherService(
            @Value("${openweather.api.key}") String apiKey) {

        this.apiKey = apiKey;

        /*
         * ========================================================
         * HTTP CLIENT
         * ========================================================
         *
         * Keep network timeouts reasonably short.
         *
         * We don't want the browser to sit there for 30+ seconds
         * waiting for OpenWeather.
         */

        HttpClient httpClient = HttpClient.create()
                .option(
                        ChannelOption.CONNECT_TIMEOUT_MILLIS,
                        5000
                )
                .responseTimeout(
                        Duration.ofSeconds(8)
                )
                .doOnConnected(connection ->
                        connection
                                .addHandlerLast(
                                        new ReadTimeoutHandler(
                                                8,
                                                TimeUnit.SECONDS
                                        )
                                )
                                .addHandlerLast(
                                        new WriteTimeoutHandler(
                                                8,
                                                TimeUnit.SECONDS
                                        )
                                )
                );

        this.webClient = WebClient.builder()
                .clientConnector(
                        new ReactorClientHttpConnector(
                                httpClient
                        )
                )
                .build();

        this.objectMapper = new ObjectMapper();
    }


    // ============================================================
    // GET WEATHER
    // ============================================================

    public Map<String, Object> getWeather(
            double latitude,
            double longitude) {

        validateCoordinates(
                latitude,
                longitude
        );

        /*
         * ========================================================
         * CACHE CHECK
         * ========================================================
         */

        String cacheKey = createCacheKey(
                latitude,
                longitude
        );

        CachedWeather cached =
                weatherCache.get(cacheKey);

        if (cached != null &&
                !cached.isExpired()) {

            System.out.println(
                    "WEATHER CACHE HIT: " + cacheKey
            );

            return cached.data;
        }

        System.out.println(
                "WEATHER CACHE MISS: " + cacheKey
        );


        try {

            // ====================================================
            // CURRENT WEATHER REQUEST
            // ====================================================

            Mono<String> currentRequest =
                    webClient
                            .get()
                            .uri(uriBuilder ->
                                    uriBuilder
                                            .scheme("https")
                                            .host(
                                                    "api.openweathermap.org"
                                            )
                                            .path(
                                                    "/data/2.5/weather"
                                            )
                                            .queryParam(
                                                    "lat",
                                                    latitude
                                            )
                                            .queryParam(
                                                    "lon",
                                                    longitude
                                            )
                                            .queryParam(
                                                    "appid",
                                                    apiKey
                                            )
                                            .queryParam(
                                                    "units",
                                                    "metric"
                                            )
                                            .queryParam(
                                                    "lang",
                                                    "en"
                                            )
                                            .build()
                            )
                            .retrieve()
                            .bodyToMono(String.class)
                            .timeout(
                                    Duration.ofSeconds(8)
                            );


            // ====================================================
            // FORECAST REQUEST
            // ====================================================

            Mono<String> forecastRequest =
                    webClient
                            .get()
                            .uri(uriBuilder ->
                                    uriBuilder
                                            .scheme("https")
                                            .host(
                                                    "api.openweathermap.org"
                                            )
                                            .path(
                                                    "/data/2.5/forecast"
                                            )
                                            .queryParam(
                                                    "lat",
                                                    latitude
                                            )
                                            .queryParam(
                                                    "lon",
                                                    longitude
                                            )
                                            .queryParam(
                                                    "appid",
                                                    apiKey
                                            )
                                            .queryParam(
                                                    "units",
                                                    "metric"
                                            )
                                            .queryParam(
                                                    "lang",
                                                    "en"
                                            )
                                            .build()
                            )
                            .retrieve()
                            .bodyToMono(String.class)
                            .timeout(
                                    Duration.ofSeconds(8)
                            );


            // ====================================================
            // RUN BOTH REQUESTS AT THE SAME TIME
            // ====================================================
            //
            // BEFORE:
            //
            // current → wait → forecast → wait
            //
            // NOW:
            //
            // current ───────┐
            //                 ├── wait for both
            // forecast ──────┘
            //
            // This significantly reduces total waiting time.

            Map<String, String> responses =
                    Mono.zip(
                            currentRequest,
                            forecastRequest
                    )
                    .map(tuple -> {

                        Map<String, String> data =
                                new HashMap<>();

                        data.put(
                                "current",
                                tuple.getT1()
                        );

                        data.put(
                                "forecast",
                                tuple.getT2()
                        );

                        return data;

                    })
                    .block();


            if (responses == null) {

                throw new RuntimeException(
                        "No weather response received."
                );
            }


            // ====================================================
            // PARSE RESPONSES
            // ====================================================

            JsonNode current =
                    objectMapper.readTree(
                            responses.get("current")
                    );

            JsonNode forecast =
                    objectMapper.readTree(
                            responses.get("forecast")
                    );


            // ====================================================
            // BUILD RESULT
            // ====================================================

            Map<String, Object> result =
                    new HashMap<>();


            // ====================================================
            // LOCATION
            // ====================================================

            Map<String, Object> locationData =
                    new HashMap<>();

            locationData.put(
                    "latitude",
                    latitude
            );

            locationData.put(
                    "longitude",
                    longitude
            );

            /*
             * We don't make another reverse-geocoding request.
             *
             * OpenWeather already gives us city and country.
             */

            locationData.put(
                    "city",
                    current
                            .path("name")
                            .asText("Unknown")
            );

            locationData.put(
                    "state",
                    ""
            );

            locationData.put(
                    "country",
                    current
                            .path("sys")
                            .path("country")
                            .asText("")
            );

            result.put(
                    "location",
                    locationData
            );


            // ====================================================
            // CURRENT WEATHER
            // ====================================================

            Map<String, Object> currentData =
                    new HashMap<>();

            JsonNode main =
                    current.path("main");

            JsonNode weather =
                    current
                            .path("weather")
                            .get(0);

            JsonNode wind =
                    current.path("wind");


            if (weather == null ||
                    weather.isMissingNode()) {

                throw new RuntimeException(
                        "Weather information was not returned by OpenWeather."
                );
            }


            currentData.put(
                    "temperature",
                    main
                            .path("temp")
                            .asDouble()
            );

            currentData.put(
                    "feelsLike",
                    main
                            .path("feels_like")
                            .asDouble()
            );

            currentData.put(
                    "minTemperature",
                    main
                            .path("temp_min")
                            .asDouble()
            );

            currentData.put(
                    "maxTemperature",
                    main
                            .path("temp_max")
                            .asDouble()
            );

            currentData.put(
                    "humidity",
                    main
                            .path("humidity")
                            .asInt()
            );

            currentData.put(
                    "pressure",
                    main
                            .path("pressure")
                            .asInt()
            );

            currentData.put(
                    "windSpeed",
                    wind
                            .path("speed")
                            .asDouble()
            );

            currentData.put(
                    "windDirection",
                    wind
                            .path("deg")
                            .asInt()
            );

            currentData.put(
                    "visibility",
                    current
                            .path("visibility")
                            .asInt()
            );

            currentData.put(
                    "condition",
                    weather
                            .path("main")
                            .asText("Unknown")
            );

            currentData.put(
                    "description",
                    weather
                            .path("description")
                            .asText(
                                    "Weather information unavailable"
                            )
            );

            currentData.put(
                    "icon",
                    weather
                            .path("icon")
                            .asText("")
            );

            currentData.put(
                    "sunrise",
                    current
                            .path("sys")
                            .path("sunrise")
                            .asLong()
            );

            currentData.put(
                    "sunset",
                    current
                            .path("sys")
                            .path("sunset")
                            .asLong()
            );

            result.put(
                    "current",
                    currentData
            );


            // ====================================================
            // FORECAST
            // ====================================================

            List<Map<String, Object>> forecastList =
                    new ArrayList<>();

            JsonNode forecastArray =
                    forecast.path("list");


            if (forecastArray.isArray()) {

                for (JsonNode item :
                        forecastArray) {

                    Map<String, Object> itemData =
                            new HashMap<>();

                    JsonNode itemMain =
                            item.path("main");

                    JsonNode itemWeather =
                            item
                                    .path("weather")
                                    .get(0);


                    if (itemWeather == null ||
                            itemWeather.isMissingNode()) {

                        continue;
                    }


                    itemData.put(
                            "timestamp",
                            item
                                    .path("dt")
                                    .asLong()
                    );

                    itemData.put(
                            "temperature",
                            itemMain
                                    .path("temp")
                                    .asDouble()
                    );

                    itemData.put(
                            "feelsLike",
                            itemMain
                                    .path("feels_like")
                                    .asDouble()
                    );

                    itemData.put(
                            "humidity",
                            itemMain
                                    .path("humidity")
                                    .asInt()
                    );

                    itemData.put(
                            "condition",
                            itemWeather
                                    .path("main")
                                    .asText(
                                            "Unknown"
                                    )
                    );

                    itemData.put(
                            "description",
                            itemWeather
                                    .path("description")
                                    .asText("")
                    );

                    itemData.put(
                            "icon",
                            itemWeather
                                    .path("icon")
                                    .asText("")
                    );

                    forecastList.add(
                            itemData
                    );
                }
            }


            result.put(
                    "forecast",
                    forecastList
            );


            System.out.println(
                    "WEATHER FORECAST ITEMS RETURNED: "
                            + forecastList.size()
            );


            // ====================================================
            // SAVE TO CACHE
            // ====================================================

            weatherCache.put(
                    cacheKey,
                    new CachedWeather(result)
            );

            System.out.println(
                    "WEATHER CACHE SAVED: "
                            + cacheKey
            );


            return result;


        } catch (Exception e) {

            /*
             * ====================================================
             * FALLBACK TO OLD CACHE
             * ====================================================
             *
             * If OpenWeather temporarily fails but we have
             * previously fetched weather data, return it instead
             * of making the entire weather page fail.
             */

            CachedWeather oldCache =
                    weatherCache.get(cacheKey);

            if (oldCache != null) {

                System.out.println(
                        "OPENWEATHER FAILED - USING OLD WEATHER CACHE"
                );

                return oldCache.data;
            }


            System.err.println(
                    "OPENWEATHER REQUEST FAILED: "
                            + e.getMessage()
            );

            throw new RuntimeException(
                    "Unable to fetch weather data: "
                            + e.getMessage(),
                    e
            );
        }
    }


    // ============================================================
    // CREATE CACHE KEY
    // ============================================================

    private String createCacheKey(
            double latitude,
            double longitude) {

        return String.format(
                "%.4f,%.4f",
                latitude,
                longitude
        );
    }


    // ============================================================
    // VALIDATE COORDINATES
    // ============================================================

    private void validateCoordinates(
            double latitude,
            double longitude) {

        if (Double.isNaN(latitude) ||
                Double.isInfinite(latitude) ||
                latitude < -90 ||
                latitude > 90) {

            throw new IllegalArgumentException(
                    "Invalid latitude."
            );
        }


        if (Double.isNaN(longitude) ||
                Double.isInfinite(longitude) ||
                longitude < -180 ||
                longitude > 180) {

            throw new IllegalArgumentException(
                    "Invalid longitude."
            );
        }
    }


    // ============================================================
    // CACHE OBJECT
    // ============================================================

    private static class CachedWeather {

        private final Map<String, Object> data;

        private final long createdAt;


        private CachedWeather(
                Map<String, Object> data) {

            this.data = data;

            this.createdAt =
                    System.currentTimeMillis();
        }


        private boolean isExpired() {

            return System.currentTimeMillis()
                    - createdAt
                    > CACHE_DURATION_MS;
        }
    }
}