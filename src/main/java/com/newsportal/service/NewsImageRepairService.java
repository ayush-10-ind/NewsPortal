package com.newsportal.service;

import com.newsportal.entity.News;
import com.newsportal.repository.NewsRepository;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
public class NewsImageRepairService {

    private static final Logger logger = LoggerFactory.getLogger(NewsImageRepairService.class);
    private static final String FALLBACK_PREFIX = "/images/fallback";

    private static final int BATCH_SIZE = 5;
    private static final long INITIAL_DELAY_SECONDS = 300;
    private static final long DELAY_SECONDS = 1800;

    private final NewsRepository newsRepository;
    private final ArticleImageService articleImageService;
    private final NasaApodImageService nasaApodImageService;
    private final TransactionTemplate transactionTemplate;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "news-image-repair-scheduler");
        thread.setDaemon(true);
        return thread;
    });

    public NewsImageRepairService(NewsRepository newsRepository,
                                  ArticleImageService articleImageService,
                                  NasaApodImageService nasaApodImageService,
                                  TransactionTemplate transactionTemplate) {
        this.newsRepository = newsRepository;
        this.articleImageService = articleImageService;
        this.nasaApodImageService = nasaApodImageService;
        this.transactionTemplate = transactionTemplate;
    }

    @PostConstruct
    public void initializeRepairMonitor() {
        scheduler.scheduleWithFixedDelay(this::repairCycle,
                INITIAL_DELAY_SECONDS, DELAY_SECONDS, TimeUnit.SECONDS);
        logger.info("News image repair monitor initialized: firstRun={}s, interval={}s, batch={}",
                INITIAL_DELAY_SECONDS, DELAY_SECONDS, BATCH_SIZE);
    }

    @PreDestroy
    public void shutdownRepairMonitor() {
        scheduler.shutdownNow();
    }

    private void repairCycle() {
        try {
            List<News> candidates = newsRepository
                    .findTop25ByImageUrlStartingWithAndSourceUrlIsNotNullOrderByPublishedDateDesc(FALLBACK_PREFIX);

            if (candidates == null || candidates.isEmpty()) return;

            int repaired = 0;
            int attempted = 0;

            for (News news : candidates) {
                if (attempted >= BATCH_SIZE) break;

                try {
                    String sourceUrl = clean(news.getSourceUrl());
                    if (sourceUrl == null) continue;

                    attempted++;

                    String resolvedImage = articleImageService.resolveImage(
                            null,
                            sourceUrl,
                            news.getCategory());

                    if (isFallbackImage(resolvedImage)) {
                        String nasaImage = nasaApodImageService.resolveImage(
                                sourceUrl,
                                news.getTitle(),
                                news.getPublishedDate());
                        if (nasaImage != null && !nasaImage.isBlank()) {
                            resolvedImage = nasaImage;
                        }
                    }

                    if (!isRealImage(resolvedImage)) continue;

                    Boolean saved = transactionTemplate.execute(status -> {
                        News current = newsRepository.findById(news.getId()).orElse(null);
                        if (current == null || !isFallbackImage(current.getImageUrl())) return false;
                        current.setImageUrl(resolvedImage);
                        newsRepository.save(current);
                        return true;
                    });

                    if (Boolean.TRUE.equals(saved)) {
                        repaired++;
                        logger.info("Recovered news image: articleId={}, source={}",
                                news.getId(),
                                isNasaImage(resolvedImage) ? "nasa-apod" : "article-page");
                    }
                } catch (Exception ex) {
                    logger.debug("News image retry skipped: articleId={}, errorType={}, message={}",
                            news.getId(), ex.getClass().getSimpleName(), ex.getMessage());
                }
            }

            logger.info("News image repair cycle completed: attempted={}, repaired={}, candidates={}",
                    attempted, repaired, candidates.size());

        } catch (Exception ex) {
            logger.warn("News image repair cycle failed: errorType={}, message={}",
                    ex.getClass().getSimpleName(), ex.getMessage());
        }
    }

    private String clean(String value) {
        if (value == null) return null;
        String cleaned = value.trim();
        return cleaned.isBlank() ? null : cleaned;
    }

    private boolean isFallbackImage(String imageUrl) {
        return imageUrl != null && imageUrl.startsWith(FALLBACK_PREFIX);
    }

    private boolean isRealImage(String imageUrl) {
        return imageUrl != null && !imageUrl.isBlank() && !isFallbackImage(imageUrl);
    }

    private boolean isNasaImage(String imageUrl) {
        return imageUrl != null && imageUrl.toLowerCase().contains("nasa.gov");
    }
}
