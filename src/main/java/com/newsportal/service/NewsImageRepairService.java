package com.newsportal.service;

import com.newsportal.entity.News;
import com.newsportal.repository.NewsRepository;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class NewsImageRepairService {

    private static final Logger logger = LoggerFactory.getLogger(NewsImageRepairService.class);
    private static final String FALLBACK_PREFIX = "/images/fallback";

    private static final int BATCH_SIZE = 5;
    private static final int INITIAL_NASA_BATCH_SIZE = 10;
    private static final int MAX_MANUAL_BATCH_SIZE = 10;
    private static final long INITIAL_NASA_REPAIR_DELAY_SECONDS = 20;
    private static final long INITIAL_DELAY_SECONDS = 300;
    private static final long DELAY_SECONDS = 1800;
    private static final long NASA_FULL_REPAIR_DELAY_SECONDS = 2;

    private final NewsRepository newsRepository;
    private final ArticleImageService articleImageService;
    private final NasaApodImageService nasaApodImageService;
    private final TransactionTemplate transactionTemplate;

    private final AtomicBoolean nasaRepairRunning = new AtomicBoolean(false);

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
        scheduler.schedule(this::runInitialNasaRepair,
                INITIAL_NASA_REPAIR_DELAY_SECONDS,
                TimeUnit.SECONDS);

        scheduler.scheduleWithFixedDelay(this::repairCycle,
                INITIAL_DELAY_SECONDS, DELAY_SECONDS, TimeUnit.SECONDS);

        logger.info("News image repair monitor initialized: nasaFirstRun={}s, generalFirstRun={}s, interval={}s, batch={}",
                INITIAL_NASA_REPAIR_DELAY_SECONDS,
                INITIAL_DELAY_SECONDS,
                DELAY_SECONDS,
                BATCH_SIZE);
    }

    @PreDestroy
    public void shutdownRepairMonitor() {
        scheduler.shutdownNow();
    }

    private void runInitialNasaRepair() {
        if (!nasaRepairRunning.compareAndSet(false, true)) {
            return;
        }

        try {
            repairNasaBatch(INITIAL_NASA_BATCH_SIZE);
        } catch (Exception ex) {
            logger.warn("Initial NASA image repair failed: errorType={}, message={}",
                    ex.getClass().getSimpleName(),
                    ex.getMessage());
        } finally {
            nasaRepairRunning.set(false);
        }
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

                    String resolvedImage = null;

                    if (isNasaSource(sourceUrl)) {
                        resolvedImage = nasaApodImageService.resolveImage(
                                sourceUrl,
                                news.getTitle(),
                                news.getPublishedDate());
                    }

                    if (!isRealImage(resolvedImage)) {
                        resolvedImage = articleImageService.resolveImage(
                                null,
                                sourceUrl,
                                news.getCategory());
                    }

                    if (!isRealImage(resolvedImage) && isNasaSource(sourceUrl)) {
                        resolvedImage = nasaApodImageService.resolveImage(
                                sourceUrl,
                                news.getTitle(),
                                news.getPublishedDate());
                    }

                    if (!isRealImage(resolvedImage)) continue;

                    final String finalResolvedImage = resolvedImage;

                    Boolean saved = transactionTemplate.execute(status -> {
                        News current = newsRepository.findById(news.getId()).orElse(null);
                        if (current == null || !isFallbackImage(current.getImageUrl())) return false;
                        current.setImageUrl(finalResolvedImage);
                        newsRepository.save(current);
                        return true;
                    });

                    if (Boolean.TRUE.equals(saved)) {
                        repaired++;
                        logger.info("Recovered news image: articleId={}, source={}",
                                news.getId(),
                                isNasaSource(sourceUrl) ? "nasa" : "article-page");
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

    /**
     * Starts one targeted NASA repair batch asynchronously so the HTTP request
     * does not wait on external NASA/publisher requests.
     */
    public String startNasaRepair(int requestedBatchSize) {
        int batchSize = Math.max(1, Math.min(requestedBatchSize, MAX_MANUAL_BATCH_SIZE));

        if (!nasaRepairRunning.compareAndSet(false, true)) {
            return "NASA image repair is already running.";
        }

        scheduler.execute(() -> {
            try {
                repairNasaBatch(batchSize);
            } finally {
                nasaRepairRunning.set(false);
            }
        });

        return "NASA image repair started. Batch size=" + batchSize + ".";
    }

    /**
     * Repairs all existing NASA fallback records in small asynchronous batches.
     */
    public String startFullNasaRepair() {
        if (!nasaRepairRunning.compareAndSet(false, true)) {
            return "NASA image repair is already running.";
        }

        scheduler.execute(this::repairNasaFullCycle);
        return "Full NASA image repair started. Existing NASA fallback records will be processed in small batches.";
    }

    private void repairNasaFullCycle() {
        try {
            List<News> candidates = newsRepository.findNasaFallbackImageCandidates(
                    FALLBACK_PREFIX,
                    PageRequest.of(0, BATCH_SIZE));

            if (candidates == null || candidates.isEmpty()) {
                logger.info("NASA image repair completed: no fallback candidates remain.");
                nasaRepairRunning.set(false);
                return;
            }

            repairNasaCandidates(candidates);

            List<News> remaining = newsRepository.findNasaFallbackImageCandidates(
                    FALLBACK_PREFIX,
                    PageRequest.of(0, 1));

            if (remaining.isEmpty()) {
                logger.info("NASA image repair completed: no fallback candidates remain.");
                nasaRepairRunning.set(false);
                return;
            }

            scheduler.schedule(this::repairNasaFullCycle,
                    NASA_FULL_REPAIR_DELAY_SECONDS,
                    TimeUnit.SECONDS);

        } catch (Exception ex) {
            logger.warn("NASA full image repair cycle failed: errorType={}, message={}",
                    ex.getClass().getSimpleName(),
                    ex.getMessage());
            nasaRepairRunning.set(false);
        }
    }

    private void repairNasaBatch(int batchSize) {
        List<News> candidates = newsRepository.findNasaFallbackImageCandidates(
                FALLBACK_PREFIX,
                PageRequest.of(0, batchSize));

        if (candidates == null || candidates.isEmpty()) {
            logger.info("NASA image repair batch found no fallback candidates.");
            return;
        }

        repairNasaCandidates(candidates);
    }

    private void repairNasaCandidates(List<News> candidates) {
        int attempted = 0;
        int repaired = 0;

        for (News news : candidates) {
            try {
                String sourceUrl = clean(news.getSourceUrl());
                if (sourceUrl == null) continue;

                attempted++;

                String resolvedImage = nasaApodImageService.resolveImage(
                        sourceUrl,
                        news.getTitle(),
                        news.getPublishedDate());

                if (!isRealImage(resolvedImage)) continue;

                final String finalResolvedImage = resolvedImage;

                Boolean saved = transactionTemplate.execute(status -> {
                    News current = newsRepository.findById(news.getId()).orElse(null);
                    if (current == null || !isFallbackImage(current.getImageUrl())) return false;
                    current.setImageUrl(finalResolvedImage);
                    newsRepository.save(current);
                    return true;
                });

                if (Boolean.TRUE.equals(saved)) {
                    repaired++;
                    logger.info("Repaired NASA image: articleId={}, title={}",
                            news.getId(), news.getTitle());
                }
            } catch (Exception ex) {
                logger.debug("NASA image repair skipped: articleId={}, errorType={}, message={}",
                        news.getId(), ex.getClass().getSimpleName(), ex.getMessage());
            }
        }

        logger.info("NASA image repair batch completed: attempted={}, repaired={}, candidates={}",
                attempted, repaired, candidates.size());
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

    private boolean isNasaSource(String sourceUrl) {
        return sourceUrl != null
                && sourceUrl.toLowerCase(Locale.ROOT).contains("nasa.gov");
    }
}
