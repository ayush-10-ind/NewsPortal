package com.newsportal.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class NasaApodImageService {

    private static final Logger logger = LoggerFactory.getLogger(NasaApodImageService.class);

    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final String APOD_API = "https://api.nasa.gov/planetary/apod";
    private static final String DEMO_KEY = "DEMO_KEY";

    private static final Pattern URL_PATTERN = Pattern.compile(
            "\\\"(?:hdurl|url)\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern MEDIA_TYPE_PATTERN = Pattern.compile(
            "\\\"media_type\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"",
            Pattern.CASE_INSENSITIVE
    );

    private final WebClient webClient;

    public NasaApodImageService(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }

    public String resolveImage(String sourceUrl, String title, LocalDate publishedDate) {
        if (!isApodArticle(sourceUrl, title) || publishedDate == null) {
            return null;
        }

        try {
            String response = webClient.get()
                    .uri(APOD_API + "?api_key=" + DEMO_KEY + "&date=" + publishedDate)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(TIMEOUT)
                    .onErrorResume(error -> Mono.empty())
                    .block();

            if (response == null || response.isBlank()) {
                return null;
            }

            Matcher mediaMatcher = MEDIA_TYPE_PATTERN.matcher(response);
            if (!mediaMatcher.find() || !"image".equalsIgnoreCase(mediaMatcher.group(1))) {
                return null;
            }

            Matcher urlMatcher = URL_PATTERN.matcher(response);
            String firstUrl = null;
            String secondUrl = null;
            int matchIndex = 0;

            while (urlMatcher.find()) {
                String candidate = urlMatcher.group(1);
                if (candidate == null || candidate.isBlank()) continue;

                if (matchIndex == 0) firstUrl = candidate;
                if (matchIndex == 1) secondUrl = candidate;
                matchIndex++;
            }

            String resolved = secondUrl != null ? secondUrl : firstUrl;
            if (resolved == null || resolved.isBlank()) {
                return null;
            }

            logger.info("NASA APOD image resolved: date={}, title={}", publishedDate, title);
            return resolved;

        } catch (Exception ex) {
            logger.debug("NASA APOD image lookup failed: date={}, errorType={}, message={}",
                    publishedDate,
                    ex.getClass().getSimpleName(),
                    ex.getMessage());
            return null;
        }
    }

    private boolean isApodArticle(String sourceUrl, String title) {
        if (sourceUrl == null || title == null) return false;

        String normalizedUrl = sourceUrl.toLowerCase();
        String normalizedTitle = title.trim().toLowerCase();

        return normalizedUrl.contains("nasa.gov")
                && normalizedTitle.startsWith("apod:");
    }
}
