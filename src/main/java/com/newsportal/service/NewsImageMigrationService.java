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
    // START ONE MIGRATION BATCH
    // =====================================================

    public String startMigration(int requestedBatchSize) {

        /*
         * Safety limit:
         *
         * Minimum = 1
         * Maximum = 10
         *
         * This prevents accidentally starting a huge batch
         * that could generate many external HTTP requests.
         */
        int batchSize =
                Math.max(
                        1,
                        Math.min(requestedBatchSize, 10)
                );


        /*
         * Prevent two migration threads from running at the
         * same time.
         */
        if (!migrationRunning.compareAndSet(false, true)) {

            return "Image migration is already running.";
        }


        Thread migrationThread =
                new Thread(
                        () -> runMigration(batchSize),
                        "news-image-migration"
                );


        /*
         * Do not keep the JVM alive if the application shuts down.
         */
        migrationThread.setDaemon(true);

        migrationThread.start();


        return "Image migration started. "
                + "Batch size: "
                + batchSize;
    }


    // =====================================================
    // RUN ONE BATCH ONLY
    // =====================================================

    private void runMigration(int batchSize) {

        int migratedThisBatch = 0;
        int failedThisBatch = 0;

        try {

            /*
             * IMPORTANT:
             *
             * This query fetches ONLY articles whose image URL
             * still starts with /uploads/news/.
             *
             * It does NOT load all 1,912 articles.
             */
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


            /*
             * Nothing left to migrate.
             */
            if (candidates.isEmpty()) {

                System.out.println(
                        "Image migration: "
                                + "no old local images remaining."
                );

                return;
            }


            /*
             * Process only this batch.
             */
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
                            "Failed to migrate image "
                                    + "for article ID "
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
                    "========================================"
            );

            System.out.println(
                    "Image migration batch completed."
            );

            System.out.println(
                    "Migrated this batch: "
                            + migratedThisBatch
            );

            System.out.println(
                    "Failed this batch: "
                            + failedThisBatch
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


        /*
         * Article disappeared between the batch query and
         * this operation.
         */
        if (news == null) {

            return false;
        }


        String currentImageUrl =
                news.getImageUrl();


        /*
         * Another migration request may have already processed
         * this article.
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
             * Existing ArticleImageService handles:
             *
             * Level 1:
             * External NewsAPI image
             *
             * Level 2:
             * Publisher og:image / twitter:image
             *
             * Level 3:
             * AgniPress fallback
             *
             * We pass null for the NewsAPI image because these
             * old records only contain the old local path.
             */
            newImageUrl =
                    articleImageService.resolveImage(
                            null,
                            sourceUrl,
                            news.getCategory()
                    );
        }


        /*
         * Absolute safety fallback.
         *
         * The database must NEVER be left with a blank image URL
         * after a successful migration.
         */
        if (newImageUrl == null || newImageUrl.isBlank()) {

            newImageUrl =
                    buildFallbackUrl(
                            news.getCategory()
                    );
        }


        final String resolvedImageUrl =
                newImageUrl;


        // =================================================
        // SAVE URL ONLY
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
                     * Prevent overwriting an image that another
                     * migration process has already updated.
                     */
                    if (!isOldLocalImage(
                            current.getImageUrl()
                    )) {

                        return;
                    }


                    /*
                     * IMPORTANT:
                     *
                     * Only the URL is stored.
                     *
                     * No image binary is written to Railway.
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
    // MIGRATION STATUS
    // =====================================================

    public String getStatus() {

        /*
         * Efficient database COUNT.
         *
         * We no longer load all news records into Java memory.
         */
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
    // CHECK FOR OLD LOCAL IMAGE
    // =====================================================

    private boolean isOldLocalImage(
            String imageUrl) {

        return imageUrl != null
                && imageUrl.startsWith(
                        OLD_IMAGE_PREFIX
                );
    }


    // =====================================================
    // AGNIPRESS FALLBACK IMAGE
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