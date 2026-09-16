package com.newsportal.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.newsportal.entity.News;
import com.newsportal.repository.NewsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

/**
 * Repairs existing NASA APOD records that were previously saved with the
 * fallback image. This path intentionally uses NASA's public DEMO_KEY so the
 * AgniPress deployment does not require another user-managed API credential.
 */
@Service
public class NasaApodFallbackRepairService {

    private static final Logger logger = LoggerFactory.getLogger(NasaApodFallbackRepairService.class);
    private static final String FALLBACK_PREFIX = "/images/fallback";
    private static final String APOD_API = "https://api.nasa.gov/planetary/apod";
    private static final String DEMO_KEY = "DEMO_KEY";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(8);
    private static final int BATCH_SIZE = 5;

    private final NewsRepository newsRepository;
    private final TransactionTemplate transactionTemplate;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public NasaApodFallbackRepairService(
            NewsRepository newsRepository,
            TransactionTemplate transactionTemplate,
            WebClient.Builder webClientBuilder,
            ObjectMapper objectMapper) {
        this.newsRepository = newsRepository;
        this.transactionTemplate = transactionTemplate;
        this.webClient = webClientBuilder.build();
        this.objectMapper = objectMapper;
    }

    @Scheduled(initialDelay = 30000, fixedDelay = 1800000)
    public void repairFallbackApodImages() {
        try {
            List<News> candidates = newsRepository.findNasaFallbackImageCandidates(
                    FALLBACK_PREFIX,
                    PageRequest.of(0, BATCH_SIZE)
            );

            if (candidates == null || candidates.isEmpty()) {
                return;
            }

            int attempted = 0;
            int repaired = 0;

            for (News news : candidates) {
                if (!isApod(news.getTitle()) || news.getPublishedDate() == null) {
                    continue;
                }

                attempted++;
                String resolvedImage = resolveApodImage(news.getPublishedDate(), news.getTitle());
                if (!isUsableImage(resolvedImage)) {
                    continue;
                }

                Boolean saved = transactionTemplate.execute(status -> {
                    News current = newsRepository.findById(news.getId()).orElse(null);
                    if (current == null || !isFallback(current.getImageUrl())) {
                        return false;
                    }
                    current.setImageUrl(resolvedImage);
                    newsRepository.save(current);
                    return true;
                });

                if (Boolean.TRUE.equals(saved)) {
                    repaired++;
                    logger.info("NASA APOD fallback repaired: articleId={}, title={}",
                            news.getId(), news.getTitle());
                }
            }

            logger.info("NASA APOD fallback repair completed: attempted={}, repaired={}",
                    attempted, repaired);
        } catch (Exception ex) {
            logger.warn("NASA APOD fallback repair failed: errorType={}, message={}",
                    ex.getClass().getSimpleName(),
                    ex.getMessage());
        }
    }

    private String resolveApodImage(LocalDate date, String title) {
        try {
            String response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .scheme("https")
                            .host("api.nasa.gov")
                            .path("/planetary/apod")
                            .queryParam("api_key", DEMO_KEY)
                            .queryParam("date", date)
                            .build())
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(REQUEST_TIMEOUT)
                    .onErrorResume(error -> Mono.empty())
                    .block();

            if (response == null || response.isBlank()) {
                return null;
            }

            JsonNode root = objectMapper.readTree(response);
            if (!"image".equalsIgnoreCase(root.path("media_type").asText(""))) {
                return null;
            }

            String hdUrl = root.path("hdurl").asText("");
            String standardUrl = root.path("url").asText("");
            String resolved = isUsableImage(hdUrl) ? hdUrl : standardUrl;

            if (isUsableImage(resolved)) {
                logger.info("NASA APOD direct image resolved: date={}, title={}", date, title);
                return resolved;
            }
        } catch (Exception ex) {
            logger.debug("NASA APOD direct lookup failed: date={}, title={}, errorType={}, message={}",
                    date,
                    title,
                    ex.getClass().getSimpleName(),
                    ex.getMessage());
        }

        return null;
    }

    private boolean isApod(String title) {
        return title != null && title.trim().toLowerCase().startsWith("apod:");
    }

    private boolean isFallback(String imageUrl) {
        return imageUrl != null && imageUrl.startsWith(FALLBACK_PREFIX);
    }

    private boolean isUsableImage(String imageUrl) {
        return imageUrl != null
                && !imageUrl.isBlank()
                && (imageUrl.startsWith("https://") || imageUrl.startsWith("http://"));
    }
}
