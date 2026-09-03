package com.newsportal.service;

import com.newsportal.entity.News;
import com.newsportal.repository.NewsRepository;

import jakarta.annotation.PostConstruct;

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

    private static final String OLD_IMAGE_PREFIX =
            "/uploads/news/";


    /*
     * We intentionally process only 5 records per cycle.
     *
     * This reduces memory usage and prevents Railway from
     * being overloaded by many external HTTP requests.
     */
    private static final int BATCH_SIZE = 5;


    /*
     * Wait 30 seconds between migration batches.
     *
     * This is deliberately conservative because each article
     * can require external HTTP requests.
     */
    private static final long DELAY_SECONDS = 30;


    private final NewsRepository newsRepository;

    private final ArticleImageService articleImageService;

    private final TransactionTemplate transactionTemplate;


    /*
     * Prevents two migration cycles from running at the
     * same time.
     */
    private final AtomicBoolean migrationRunning =
            new AtomicBoolean(false);


    /*
     * These counters are only runtime statistics.
     *
     * IMPORTANT:
     * They reset if Railway restarts the application.
     *
     * The database "remaining" count is the real source
     * of truth.
     */
    private final AtomicInteger totalMigrated =
            new AtomicInteger(0);

    private final AtomicInteger totalFailed =
            new AtomicInteger(0);


    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(
                    runnable -> {

                        Thread thread =
                                new Thread(
                                        runnable,
                                        "news-image-migration-scheduler"
                                );

                        thread.setDaemon(true);

                        return thread;
                    }
            );


    // =====================================================
    // CONSTRUCTOR
    // =====================================================

    public NewsImageMigrationService(
            NewsRepository newsRepository,
            ArticleImageService articleImageService,
            TransactionTemplate transactionTemplate) {

        this.newsRepository = newsRepository;

        this.articleImageService = articleImageService;

        this.transactionTemplate = transactionTemplate;
    }


    // =====================================================
    // STARTUP
    //
    // After every Railway restart, check the database.
    //
    // If old /uploads/news/ records remain, migration
    // automatically resumes.
    // =====================================================

    @PostConstruct
    public void initializeMigrationMonitor() {

        scheduler.scheduleWithFixedDelay(
                this::automaticMigrationCycle,
                15,
                DELAY_SECONDS,
                TimeUnit.SECONDS
        );


        System.out.println(
                "News image migration monitor initialized."
        );
    }


    // =====================================================
    // AUTOMATIC MIGRATION CYCLE
    // =====================================================

    private void automaticMigrationCycle() {

        /*
         * Prevent overlapping cycles.
         */
        if (!migrationRunning.compareAndSet(
                false,
                true)) {

            return;
        }


        try {

            long remaining =
                    newsRepository
                            .countByImageUrlStartingWith(
                                    OLD_IMAGE_PREFIX
                            );


            /*
             * Nothing left to migrate.
             */
            if (remaining == 0) {

                return;
            }


            System.out.println(
                    "Automatic image migration cycle started. "
                            + "Remaining="
                            + remaining
            );


            List<News> candidates =
                    newsRepository
                            .findTop5ByImageUrlStartingWithOrderByIdAsc(
                                    OLD_IMAGE_PREFIX
                            );


            if (candidates.isEmpty()) {

                System.out.println(
                        "Migration monitor found no candidates."
                );

                return;
            }


            int migratedThisCycle = 0;
            int failedThisCycle = 0;


            /*
             * Process one article at a time.
             */
            for (News news : candidates) {

                try {

                    boolean migrated =
                            migrateSingleArticle(
                                    news.getId()
                            );


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


                    System.err.println(
                            "Image migration failed for article ID "
                                    + news.getId()
                                    + ": "
                                    + ex.getMessage()
                    );


                    /*
                     * If an article cannot obtain an external
                     * image, give it the AgniPress fallback.
                     *
                     * This prevents one problematic publisher
                     * from blocking migration forever.
                     */
                    try {

                        saveFallbackImage(
                                news.getId(),
                                news.getCategory()
                        );

                    } catch (Exception fallbackException) {

                        System.err.println(
                                "Fallback failed for article ID "
                                        + news.getId()
                                        + ": "
                                        + fallbackException.getMessage()
                        );
                    }
                }
            }


            long remainingAfter =
                    newsRepository
                            .countByImageUrlStartingWith(
                                    OLD_IMAGE_PREFIX
                            );


            System.out.println(
                    "Image migration cycle completed. "
                            + "Migrated="
                            + migratedThisCycle
                            + ", Failed="
                            + failedThisCycle
                            + ", Remaining="
                            + remainingAfter
            );


            if (remainingAfter == 0) {

                System.out.println(
                        "========================================"
                );

                System.out.println(
                        "ALL OLD NEWS IMAGES HAVE BEEN MIGRATED."
                );

                System.out.println(
                        "Total migrated since current startup: "
                                + totalMigrated.get()
                );

                System.out.println(
                        "Total failed since current startup: "
                                + totalFailed.get()
                );

                System.out.println(
                        "========================================"
                );
            }


        } catch (Exception ex) {

            /*
             * IMPORTANT:
             *
             * Never allow an exception in one migration cycle
             * to kill the scheduler.
             */
            System.err.println(
                    "Image migration monitor error: "
                            + ex.getMessage()
            );


        } finally {

            migrationRunning.set(false);
        }
    }


    // =====================================================
    // MANUAL START
    //
    // Kept for compatibility with the existing endpoint.
    //
    // /api-integration/images/migrate?batchSize=5
    //
    // This triggers ONE immediate cycle.
    // The automatic monitor will continue afterward.
    // =====================================================

    public String startMigration(int requestedBatchSize) {

        if (migrationRunning.get()) {

            return "Image migration is already running.";
        }


        scheduler.execute(
                this::automaticMigrationCycle
        );


        return "Image migration batch started. "
                + "Automatic migration monitor is active.";
    }


    // =====================================================
    // MANUAL FULL MIGRATION
    //
    // /api-integration/images/migrate/all
    //
    // We no longer create a long-running migration thread.
    //
    // Instead, the permanent monitor handles small batches.
    // =====================================================

    public String startFullMigration() {

        if (migrationRunning.get()) {

            return "Image migration is already running.";
        }


        scheduler.execute(
                this::automaticMigrationCycle
        );


        long remaining =
                newsRepository
                        .countByImageUrlStartingWith(
                                OLD_IMAGE_PREFIX
                        );


        return "Full image migration activated. "
                + "Remaining old images: "
                + remaining
                + ". Migration will continue automatically.";
    }


    // =====================================================
    // MIGRATE ONE ARTICLE
    // =====================================================

    private boolean migrateSingleArticle(
            Long newsId) {

        News news =
                newsRepository
                        .findById(newsId)
                        .orElse(null);


        if (news == null) {

            return false;
        }


        String currentImageUrl =
                news.getImageUrl();


        /*
         * Article may already have been migrated.
         */
        if (!isOldLocalImage(currentImageUrl)) {

            return false;
        }


        String sourceUrl =
                news.getSourceUrl();


        String newImageUrl;


        // =================================================
        // NO SOURCE URL
        // =================================================

        if (sourceUrl == null
                || sourceUrl.isBlank()) {

            newImageUrl =
                    buildFallbackUrl(
                            news.getCategory()
                    );

        } else {

            /*
             * Existing image-resolution architecture:
             *
             * Level 1 → NewsAPI image
             * Level 2 → publisher article image
             * Level 3 → AgniPress fallback
             */
            newImageUrl =
                    articleImageService.resolveImage(
                            null,
                            sourceUrl,
                            news.getCategory()
                    );
        }


        /*
         * Never save a null/blank image URL.
         */
        if (newImageUrl == null
                || newImageUrl.isBlank()) {

            newImageUrl =
                    buildFallbackUrl(
                            news.getCategory()
                    );
        }


        final String resolvedImageUrl =
                newImageUrl;


        // =================================================
        // SAVE ONLY THE URL
        // =================================================

        Boolean saved =
                transactionTemplate.execute(
                        status -> {

                            News current =
                                    newsRepository
                                            .findById(newsId)
                                            .orElse(null);


                            if (current == null) {

                                return false;
                            }


                            /*
                             * Another process may have migrated
                             * this record already.
                             */
                            if (!isOldLocalImage(
                                    current.getImageUrl()
                            )) {

                                return false;
                            }


                            /*
                             * IMPORTANT:
                             *
                             * We store only the external URL
                             * or AgniPress fallback URL.
                             *
                             * No image binary is stored.
                             */
                            current.setImageUrl(
                                    resolvedImageUrl
                            );


                            newsRepository.save(current);


                            return true;
                        }
                );


        return Boolean.TRUE.equals(saved);
    }


    // =====================================================
    // SAVE FALLBACK
    // =====================================================

    private void saveFallbackImage(
            Long newsId,
            String category) {

        String fallbackUrl =
                buildFallbackUrl(category);


        transactionTemplate.executeWithoutResult(
                status -> {

                    News current =
                            newsRepository
                                    .findById(newsId)
                                    .orElse(null);


                    if (current == null) {

                        return;
                    }


                    /*
                     * Only replace old local URLs.
                     */
                    if (!isOldLocalImage(
                            current.getImageUrl()
                    )) {

                        return;
                    }


                    current.setImageUrl(
                            fallbackUrl
                    );


                    newsRepository.save(current);
                }
        );
    }


    // =====================================================
    // MIGRATION STATUS
    // =====================================================

    public String getStatus() {

        long remaining =
                newsRepository
                        .countByImageUrlStartingWith(
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


    // =====================================================
    // OLD LOCAL IMAGE CHECK
    // =====================================================

    private boolean isOldLocalImage(
            String imageUrl) {

        return imageUrl != null
                && imageUrl.startsWith(
                        OLD_IMAGE_PREFIX
                );
    }


    // =====================================================
    // FALLBACK URL
    // =====================================================

    private String buildFallbackUrl(
            String category) {

        String safeCategory =
                category == null
                        || category.isBlank()
                        ? "general"
                        : category.trim();


        return "/images/fallback?category="
                + safeCategory;
    }
}