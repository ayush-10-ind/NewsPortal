package com.newsportal.service;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;
import java.util.HashMap;
import java.util.Map;

@Service
public class ExternalAPIService {

private final RestTemplate restTemplate;

public ExternalAPIService() {
        this.restTemplate = new RestTemplate();
    }

// Example 1: Simple GET request
    public String callGetAPI(String url) {
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            return response.getBody();
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

// Example 2: POST request with JSON body
    public String callPostAPI(String url, Map<String, String> requestBody) {
        try {
            // Set headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

// Create request entity
            HttpEntity<Map<String, String>> request = new HttpEntity<>(requestBody, headers);

// Make POST request
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
            
            return response.getBody();
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

// Example 3: API with Authentication (API Key)
    public String callAPIWithAuth(String url, String apiKey) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + apiKey);
            headers.setContentType(MediaType.APPLICATION_JSON);

HttpEntity<String> entity = new HttpEntity<>(headers);

ResponseEntity<String> response = restTemplate.exchange(
                url, 
                HttpMethod.GET, 
                entity, 
                String.class
            );

return response.getBody();
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }
}
