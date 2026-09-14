package com.newsportal.service;

import com.fasterxml.jackson.databind.JsonNode;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.Map;

@Service
public class WebClientAPIService {

    private static final Duration ASHNA_TIMEOUT = Duration.ofSeconds(45);

    private final WebClient webClient;

    @Value("${ashna.api.key}")
    private String ashnaApiKey;

    public WebClientAPIService(
            @Value("${ashna.api.base-url}") String baseUrl) {
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    public String getDataFromAPI(String endpoint) {
        return webClient.get()
                .uri(endpoint)
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }

    public String postDataToAPI(
            String endpoint,
            Map<String, String> requestBody) {
        return webClient.post()
                .uri(endpoint)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }

    public String askAshna(String prompt) {

        Map<String, Object> requestBody = Map.of(
                "model", "ashna-x1",
                "messages", new Object[]{
                        Map.of(
                                "role", "user",
                                "content", prompt
                        )
                }
        );

        try {
            JsonNode ashnaResponse = webClient
                    .post()
                    .uri("/chat/completions")
                    .header("Authorization", "Bearer " + ashnaApiKey)
                    .header("Content-Type", "application/json")
                    .bodyValue(requestBody)
                    .retrieve()
                    .onStatus(
                            status -> status.isError(),
                            response -> response
                                    .bodyToMono(String.class)
                                    .defaultIfEmpty("")
                                    .map(body -> new RuntimeException(
                                            "Ashna API error: HTTP "
                                                    + response.statusCode().value()
                                                    + (body.isBlank() ? "" : " - " + truncate(body, 500))
                                    ))
                    )
                    .bodyToMono(JsonNode.class)
                    .timeout(ASHNA_TIMEOUT)
                    .block();

            if (ashnaResponse == null) {
                throw new RuntimeException("Ashna returned an empty response.");
            }

            JsonNode contentNode = ashnaResponse
                    .path("choices")
                    .path(0)
                    .path("message")
                    .path("content");

            if (contentNode.isMissingNode() ||
                    contentNode.isNull() ||
                    contentNode.asText().isBlank()) {
                throw new RuntimeException(
                        "Ashna response does not contain message content."
                );
            }

            return contentNode.asText().trim();

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(
                    "Ashna request failed: "
                            + e.getClass().getSimpleName()
                            + " - " + e.getMessage(),
                    e
            );
        }
    }

    private String truncate(String value, int maxLength) {
        if (value == null) return "";
        String cleaned = value.replaceAll("\\s+", " ").trim();
        return cleaned.length() <= maxLength
                ? cleaned
                : cleaned.substring(0, maxLength) + "...";
    }

    public String getAshnaModels() {
        return webClient
                .get()
                .uri("/models")
                .header("Authorization", "Bearer " + ashnaApiKey)
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }
}
