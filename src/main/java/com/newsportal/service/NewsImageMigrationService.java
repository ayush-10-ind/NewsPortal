package com.newsportal.service;

import com.newsportal.entity.News;
import com.newsportal.repository.NewsRepository;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class NewsImageMigrationService {

    private static final Logger logger =
            LoggerFactory.getLogger(NewsImageMigrationService.class);

    private static final String OLD_IMAGE_PREFIX =
            "/uploads/news/";

    private static final int BATCH_SIZE = 5;
    private static final long DELAY_SECONDS = 30;

    private final NewsRepository newsRepository;
    private final ArticleImageService articleImageService;
    private final TransactionTemplate transactionTemplate;

    private final AtomicBoolean migrationRunning =
            new AtomicBoolean(false);

    private final AtomicInteger totalMigrated =
            new AtomicInteger(0);

    private final AtomicInteger totalFailed =
            new AtomicInteger(0);

    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(
                    runnable -> {
                        Thread thread = new Thread(
                                runnable,
                                "news-image-migration-scheduler"
                        );
                        thread.setDaemon(true);
                        return thread;
                    }
            );

    public NewsImageMigrationService(
            NewsRepository newsRepository,
            ArticleImageService articleImageService,
            TransactionTemplate transactionTemplate) {

        this.newsRepository = newsRepository;
        this.articleImageService = articleImageService;
        this.transactionTemplate = transactionTemplate;
    }

    @PostConstruct
    public void initializeMigrationMonitor() {
        scheduler.scheduleWithFixedDelay(
                this::automaticMigrationCycle,
                15,
                DELAY_SECONDS,
                TimeUnit.SECONDS
        );

        logger.info("News image migration monitor initialized");
    }

    private void automaticMigrationCycle() {

        if (!migrationRunning.compareAndSet(false, true)) {
            return;
        }

        try {
            long remaining =
                    newsRepository.countByImageUrlStartingWith(
                            OLD_IMAGE_PREFIX
                    );

            if (remaining == 0) {
                return;
            }

            List<News> candidates =
                    newsRepository
                            .findTop5ByImageUrlStartingWithOrderByIdAsc(
                                    OLD_IMAGE_PREFIX
                            );

            if (candidates.isEmpty()) {
                logger.debug("Image migration found no candidates");
                return;
            }

            int migratedThisCycle = 0;
            int failedThisCycle = 0;

            for (News news : candidates) {
                try {
                    boolean migrated =
                            migrateSingleArticle(news.getId());

                    if (migrated) {
                        migratedThisCycle++;
                        totalMigrated.incrementAndGet();
                    } else {
                        failedThisCycle++;
                        totalFailed.incrementAndGet();
                    }

                } catch (Exception ex) {
                    failedThisCycle++;
                    totalFailed.incrementAndGet();

                    logger.warn(
                            "Image migration failed: articleId={}, errorType={}, message={}",
                            news.getId(),
                            ex.getClass().getSimpleName(),
                            ex.getMessage()
                    );

                    try {
                        saveFallbackImage(
                                news.getId(),
                                news.getCategory()
                        );
                    } catch (Exception fallbackException) {
                        logger.error(
                                "Fallback image save failed: articleId={}, errorType={}, message={}",
                                news.getId(),
                                fallbackException.getClass().getSimpleName(),
                                fallbackException.getMessage()
                        );
                    }
                }
            }

            long remainingAfter =
                    newsRepository.countByImageUrlStartingWith(
                            OLD_IMAGE_PREFIX
                    );

            logger.info(
                    "Image migration cycle completed: migrated={}, failed={}, remaining={}",
                    migratedThisCycle,
                    failedThisCycle,
                    remainingAfter
            );

            if (remainingAfter == 0) {
                logger.info(
                        "All old news images migrated: totalMigrated={}, totalFailed={}",
                        totalMigrated.get(),
                        totalFailed.get()
                );
            }

        } catch (Exception ex) {
            logger.error(
                    "Image migration monitor error: errorType={}, message={}",
                    ex.getClass().getSimpleName(),
                    ex.getMessage()
            );

        } finally {
            migrationRunning.set(false);
        }
    }

    public String startMigration(int requestedBatchSize) {

        if (migrationRunning.get()) {
            return "Image migration is already running.";
        }

        scheduler.execute(this::automaticMigrationCycle);

        return "Image migration batch started. "
                + "Automatic migration monitor is active.";
    }

    public String startFullMigration() {

        if (migrationRunning.get()) {
            return "Image migration is already running.";
        }

        scheduler.execute(this::automaticMigrationCycle);

        long remaining =
                newsRepository.countByImageUrlStartingWith(
                        OLD_IMAGE_PREFIX
                );

        return "Full image migration activated. "
                + "Remaining old images: "
                + remaining
                + ". Migration will continue automatically.";
    }

    private boolean migrateSingleArticle(Long newsId) {

        News news =
                newsRepository.findById(newsId).orElse(null);

        if (news == null) {
            return false;
        }

        String currentImageUrl = news.getImageUrl();

        if (!isOldLocalImage(currentImageUrl)) {
            return false;
        }

        String sourceUrl = news.getSourceUrl();
        String newImageUrl;

        if (sourceUrl == null || sourceUrl.isBlank()) {
            newImageUrl = buildFallbackUrl(news.getCategory());
        } else {
            newImageUrl = articleImageService.resolveImage(
                    null,
                    sourceUrl,
                    news.getCategory()
            );
        }

        if (newImageUrl == null || newImageUrl.isBlank()) {
            newImageUrl = buildFallbackUrl(news.getCategory());
        }

        final String resolvedImageUrl = newImageUrl;

        Boolean saved =
                transactionTemplate.execute(status -> {
                    News current =
                            newsRepository.findById(newsId).orElse(null);

                    if (current == null) {
                        return false;
                    }

                    if (!isOldLocalImage(current.getImageUrl())) {
                        return false;
                    }

                    current.setImageUrl(resolvedImageUrl);
                    newsRepository.save(current);
                    return true;
                });

        return Boolean.TRUE.equals(saved);
    }

    private void saveFallbackImage(
            Long newsId,
            String category) {

        String fallbackUrl = buildFallbackUrl(category);

        transactionTemplate.executeWithoutResult(status -> {
            News current =
                    newsRepository.findById(newsId).orElse(null);

            if (current == null) {
                return;
            }

            if (!isOldLocalImage(current.getImageUrl())) {
                return;
            }

            current.setImageUrl(fallbackUrl);
            newsRepository.save(current);
        });
    }

    public String getStatus() {

        long remaining =
                newsRepository.countByImageUrlStartingWith(
                        OLD_IMAGE_PREFIX
                );

        return "Image migration status: "
                + "remaining="
                + remaining
                + ", totalMigrated="
                + totalMigrated.get()
                + ", totalFailed="
                + totalFailed.get()
                + ", running="
                + migrationRunning.get();
    }

    private boolean isOldLocalImage(String imageUrl) {
        return imageUrl != null
                && imageUrl.startsWith(OLD_IMAGE_PREFIX);
    }

    private String buildFallbackUrl(String category) {

        String safeCategory =
                category == null || category.isBlank()
                        ? "general"
                        : category.trim();

        return "/images/fallback?category=" + safeCategory;
    }
}