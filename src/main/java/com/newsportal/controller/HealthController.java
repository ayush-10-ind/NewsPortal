package com.newsportal.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lightweight production health endpoint for Railway and uptime checks.
 *
 * This endpoint intentionally does not call the database, RSS feeds, Ashna,
 * weather APIs, or any other external dependency. A 200 response means the
 * Spring Boot application itself is alive and able to serve requests.
 */
@RestController
public class HealthController {

    @GetMapping(value = "/health", produces = MediaType.TEXT_PLAIN_VALUE)
    public String health() {
        return "OK";
    }
}
