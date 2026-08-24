package com.newsportal.service;

import com.fasterxml.jackson.databind.JsonNode;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

@Service
public class WebClientAPIService {

    private final WebClient webClient;

    @Value("${ashna.api.key}")
    private String ashnaApiKey;


    public WebClientAPIService(
            @Value("${ashna.api.base-url}") String baseUrl) {

        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .build();
    }


    // =====================================================
    // TEST GET REQUEST
    // =====================================================

    public String getDataFromAPI(String endpoint) {

        return webClient
                .get()
                .uri(endpoint)
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }


    // =====================================================
    // TEST POST REQUEST
    // =====================================================

    public String postDataToAPI(
            String endpoint,
            Map<String, String> requestBody) {

        return webClient
                .post()
                .uri(endpoint)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }


    // =====================================================
    // ASHNA AI
    // =====================================================

    public String askAshna(String prompt) {

        Map<String, Object> requestBody = Map.of(

                "model",
                "ashna-x1",

                "messages",
                new Object[] {

                        Map.of(
                                "role",
                                "user",

                                "content",
                                prompt
                        )
                }
        );


        // -------------------------------------------------
        // Send request to Ashna
        // -------------------------------------------------

        JsonNode response = webClient

                .post()

                .uri("/chat/completions")

                .header(
                        "Authorization",
                        "Bearer " + ashnaApiKey
                )

                .header(
                        "Content-Type",
                        "application/json"
                )

                .bodyValue(requestBody)

                .retrieve()

                .bodyToMono(JsonNode.class)

                .block();


        // -------------------------------------------------
        // Validate response
        // -------------------------------------------------

        if (response == null) {

            throw new RuntimeException(
                    "Ashna returned an empty response."
            );
        }


        // -------------------------------------------------
        // Extract:
        //
        // choices[0]
        //      ↓
        // message
        //      ↓
        // content
        // -------------------------------------------------

        JsonNode contentNode = response
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


        // -------------------------------------------------
        // Return ONLY generated article
        // -------------------------------------------------

        return contentNode
                .asText()
                .trim();
    }


    // =====================================================
    // ASHNA MODELS
    // =====================================================

    public String getAshnaModels() {

        return webClient

                .get()

                .uri("/models")

                .header(
                        "Authorization",
                        "Bearer " + ashnaApiKey
                )

                .retrieve()

                .bodyToMono(String.class)

                .block();
    }

}