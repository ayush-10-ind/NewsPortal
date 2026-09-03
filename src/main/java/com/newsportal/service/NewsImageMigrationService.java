package com.newsportal.service;

import com.newsportal.entity.News;
import com.newsportal.repository.NewsRepository;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class NewsImageMigrationService {

    private static final String OLD_IMAGE_PREFIX = "/uploads/news/";

    /*
     * Internal batch size.
     *
     * The migration will continue automatically until all old
     * /uploads/news/ records have been processed.
     *
     * We keep the internal batch small so Railway and external
     * publishers are not hit with hundreds of requests at once.
     */
    private static final int INTERNAL_BATCH_SIZE = 10;

    /*
     * Small pause between batches.
     *
     * This gives the application and external image servers a
     * little breathing room.
     */
    private static final long BATCH_DELAY_MS = 500L;


    private final NewsRepository newsRepository;
    private final ArticleImageService articleImageService;
    private final TransactionTemplate transactionTemplate;


    private final AtomicBoolean migrationRunning =
            new AtomicBoolean(false);

    private final AtomicInteger totalMigrated =
            new AtomicInteger(0);

    private final AtomicInteger totalFailed =
            new AtomicInteger(0);


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
    // START ONE SMALL BATCH
    //
    // Existing endpoint:
    //
    // /api-integration/images/migrate?batchSize=10
    //
    // This still processes only ONE batch.
    // =====================================================

    public String startMigration(int requestedBatchSize) {

        int batchSize =
                Math.max(
                        1,
                        Math.min(requestedBatchSize, INTERNAL_BATCH_SIZE)
                );


        if (!migrationRunning.compareAndSet(false, true)) {

            return "Image migration is already running.";
        }


        Thread migrationThread =
                new Thread(
                        () -> runSingleBatch(batchSize),
                        "news-image-migration-batch"
                );


        migrationThread.setDaemon(true);

        migrationThread.start();


        return "Image migration started. "
                + "Batch size: "
                + batchSize;
    }


    // =====================================================
    // START COMPLETE MIGRATION
    //
    // New endpoint:
    //
    // /api-integration/images/migrate/all
    //
    // This automatically processes ALL remaining old images.
    // =====================================================

    public String startFullMigration() {

        if (!migrationRunning.compareAndSet(false, true)) {

            return "Image migration is already running.";
        }


        Thread migrationThread =
                new Thread(
                        this::runFullMigration,
                        "news-image-migration-all"
                );


        migrationThread.setDaemon(true);

        migrationThread.start();


        return "Full image migration started. "
                + "The migration will continue automatically "
                + "until all old local image URLs are processed.";
    }


    // =====================================================
    // RUN ONE BATCH
    // =====================================================

    private void runSingleBatch(int batchSize) {

        int migratedThisBatch = 0;
        int failedThisBatch = 0;

        try {

            List<News> candidates =
                    newsRepository
                            .findByImageUrlStartingWith(
                                    OLD_IMAGE_PREFIX,
                                    PageRequest.of(
                                            0,
                                            batchSize,
                                            Sort.by(
                                                    Sort.Direction.ASC,
                                                    "id"
                                            )
                                    )
                            )
                            .getContent();


            if (candidates.isEmpty()) {

                System.out.println(
                        "Image migration: "
                                + "no old local images remaining."
                );

                return;
            }


            for (News candidate : candidates) {

                try {

                    boolean migrated =
                            migrateSingleArticle(
                                    candidate.getId()
                            );


                    if (migrated) {

                        migratedThisBatch++;

                        totalMigrated.incrementAndGet();

                    } else {

                        failedThisBatch++;

                        totalFailed.incrementAndGet();
                    }


                } catch (Exception ex) {

                    failedThisBatch++;

                    totalFailed.incrementAndGet();

                    System.err.println(
                            "Failed to migrate article "
                                    + candidate.getId()
                                    + ": "
                                    + ex.getMessage()
                    );
                }
            }


        } catch (Exception ex) {

            System.err.println(
                    "Image migration batch failed: "
                            + ex.getMessage()
            );

        } finally {

            migrationRunning.set(false);


            System.out.println(
                    "Image migration batch completed. "
                            + "Migrated="
                            + migratedThisBatch
                            + ", Failed="
                            + failedThisBatch
            );
        }
    }


    // =====================================================
    // RUN COMPLETE MIGRATION
    // =====================================================

    private void runFullMigration() {

        int batchNumber = 0;

        try {

            while (true) {

                batchNumber++;


                long remainingBefore =
                        newsRepository
                                .countByImageUrlStartingWith(
                                        OLD_IMAGE_PREFIX
                                );


                System.out.println(
                        "========================================"
                );

                System.out.println(
                        "Starting migration batch #"
                                + batchNumber
                );

                System.out.println(
                        "Remaining before batch: "
                                + remainingBefore
                );

                System.out.println(
                        "========================================"
                );


                /*
                 * Nothing left to migrate.
                 */
                if (remainingBefore == 0) {

                    System.out.println(
                            "========================================"
                    );

                    System.out.println(
                            "FULL IMAGE MIGRATION COMPLETED"
                    );

                    System.out.println(
                            "Total migrated: "
                                    + totalMigrated.get()
                    );

                    System.out.println(
                            "Total failed: "
                                    + totalFailed.get()
                    );

                    System.out.println(
                            "========================================"
                    );

                    break;
                }


                /*
                 * Fetch only the next 10 old-image records.
                 */
                List<News> candidates =
                        newsRepository
                                .findByImageUrlStartingWith(
                                        OLD_IMAGE_PREFIX,
                                        PageRequest.of(
                                                0,
                                                INTERNAL_BATCH_SIZE,
                                                Sort.by(
                                                        Sort.Direction.ASC,
                                                        "id"
                                                )
                                        )
                                )
                                .getContent();


                /*
                 * Safety condition.
                 *
                 * If the database says records remain but the
                 * query returns nothing, stop rather than looping
                 * forever.
                 */
                if (candidates.isEmpty()) {

                    System.err.println(
                            "Migration stopped because "
                                    + "remaining count was non-zero "
                                    + "but no migration candidates "
                                    + "were returned."
                    );

                    break;
                }


                int migratedThisBatch = 0;
                int failedThisBatch = 0;


                // =========================================
                // PROCESS CURRENT BATCH
                // =========================================

                for (News candidate : candidates) {

                    try {

                        boolean migrated =
                                migrateSingleArticle(
                                        candidate.getId()
                                );


                        if (migrated) {

                            migratedThisBatch++;

                            totalMigrated.incrementAndGet();

                        } else {

                            failedThisBatch++;

                            totalFailed.incrementAndGet();
                        }


                    } catch (Exception ex) {

                        failedThisBatch++;

                        totalFailed.incrementAndGet();


                        System.err.println(
                                "Migration failed for article ID "
                                        + candidate.getId()
                                        + ": "
                                        + ex.getMessage()
                        );


                        /*
                         * IMPORTANT:
                         *
                         * We do not allow one failed article to
                         * stop the entire migration.
                         *
                         * Give the failed article the AgniPress
                         * fallback so that it no longer remains
                         * an old /uploads/news/ record.
                         */
                        try {

                            saveFallbackImage(
                                    candidate.getId(),
                                    candidate.getCategory()
                            );

                        } catch (Exception fallbackException) {

                            System.err.println(
                                    "Fallback also failed for "
                                            + "article ID "
                                            + candidate.getId()
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
                        "Batch #"
                                + batchNumber
                                + " completed. "
                                + "Migrated="
                                + migratedThisBatch
                                + ", Failed="
                                + failedThisBatch
                                + ", Remaining="
                                + remainingAfter
                );


                /*
                 * If this batch made absolutely no progress,
                 * stop instead of creating an infinite loop.
                 */
                if (migratedThisBatch == 0
                        && failedThisBatch == 0) {

                    System.err.println(
                            "Migration stopped because "
                                    + "no progress was made."
                    );

                    break;
                }


                /*
                 * Small delay between batches.
                 */
                try {

                    Thread.sleep(BATCH_DELAY_MS);

                } catch (InterruptedException ex) {

                    Thread.currentThread().interrupt();

                    System.err.println(
                            "Image migration interrupted."
                    );

                    break;
                }
            }


        } catch (Exception ex) {

            System.err.println(
                    "FULL IMAGE MIGRATION FAILED: "
                            + ex.getMessage()
            );


        } finally {

            migrationRunning.set(false);


            long remaining =
                    newsRepository
                            .countByImageUrlStartingWith(
                                    OLD_IMAGE_PREFIX
                            );


            System.out.println(
                    "========================================"
            );

            System.out.println(
                    "IMAGE MIGRATION THREAD STOPPED"
            );

            System.out.println(
                    "Remaining old images: "
                            + remaining
            );

            System.out.println(
                    "Total migrated: "
                            + totalMigrated.get()
            );

            System.out.println(
                    "Total failed: "
                            + totalFailed.get()
            );

            System.out.println(
                    "========================================"
            );
        }
    }


    // =====================================================
    // MIGRATE ONE ARTICLE
    // =====================================================

    private boolean migrateSingleArticle(Long newsId) {

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
         * Make sure this article still needs migration.
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

        if (sourceUrl == null || sourceUrl.isBlank()) {

            newImageUrl =
                    buildFallbackUrl(
                            news.getCategory()
                    );

        } else {

            /*
             * ArticleImageService performs the existing
             * image-resolution strategy:
             *
             * 1. Validate external image
             * 2. Try publisher article metadata
             * 3. Use AgniPress fallback
             */
            newImageUrl =
                    articleImageService.resolveImage(
                            null,
                            sourceUrl,
                            news.getCategory()
                    );
        }


        /*
         * Never save an empty image URL.
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
                     * Prevent overwriting an image if another
                     * process already migrated this article.
                     */
                    if (!isOldLocalImage(
                            current.getImageUrl()
                    )) {

                        return;
                    }


                    /*
                     * ONLY the URL is stored.
                     *
                     * No image binary is saved to Railway.
                     */
                    current.setImageUrl(
                            resolvedImageUrl
                    );


                    newsRepository.save(current);
                }
        );


        return true;
    }


    // =====================================================
    // SAVE FALLBACK AFTER FAILURE
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
    // OLD IMAGE CHECK
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
                category == null || category.isBlank()
                        ? "general"
                        : category.trim();


        return "/images/fallback?category="
                + safeCategory;
    }
}