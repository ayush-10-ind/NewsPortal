package com.newsportal.service;

import com.newsportal.entity.News;
import com.newsportal.repository.NewsRepository;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class NewsImageMigrationService {

    private final NewsRepository newsRepository;
    private final ArticleImageService articleImageService;
    private final TransactionTemplate transactionTemplate;

    // =====================================================
    // MIGRATION STATE
    // =====================================================

    private final AtomicBoolean running =
            new AtomicBoolean(false);

    private final AtomicInteger totalProcessed =
            new AtomicInteger(0);

    private final AtomicInteger migrated =
            new AtomicInteger(0);

    private final AtomicInteger failed =
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
    // START BACKGROUND MIGRATION
    // =====================================================

    public synchronized String startMigration(
            int requestedBatchSize) {

        if (running.get()) {

            return "Image migration is already running. "
                    + "Check /api-integration/images/migrate/status";
        }

        int batchSize =
                Math.max(
                        1,
                        Math.min(
                                requestedBatchSize,
                                5
                        )
                );

        running.set(true);

        totalProcessed.set(0);
        migrated.set(0);
        failed.set(0);

        Thread migrationThread =
                new Thread(
                        () -> runMigration(batchSize),
                        "news-image-migration"
                );

        migrationThread.setDaemon(true);
        migrationThread.start();

        return "Image migration started in background. "
                + "Batch size: "
                + batchSize
                + ". "
                + "Check /api-integration/images/migrate/status";
    }

    // =====================================================
    // BACKGROUND MIGRATION
    // =====================================================

    private void runMigration(int batchSize) {

        System.out.println();
        System.out.println(
                "========================================"
        );
        System.out.println(
                "BACKGROUND NEWS IMAGE MIGRATION STARTED"
        );
        System.out.println(
                "Batch size: " + batchSize
        );
        System.out.println(
                "========================================"
        );

        try {

            while (true) {

                // =============================================
                // GET NEXT OLD IMAGE
                // =============================================

                List<News> oldNews =
                        newsRepository.findAll(
                                Sort.by(
                                        Sort.Direction.ASC,
                                        "id"
                                )
                        );

                News target = null;

                for (News news : oldNews) {

                    if (news != null &&
                            isOldLocalImage(
                                    news.getImageUrl()
                            )) {

                        target = news;
                        break;
                    }
                }

                // =============================================
                // NOTHING LEFT
                // =============================================

                if (target == null) {

                    System.out.println();
                    System.out.println(
                            "========================================"
                    );
                    System.out.println(
                            "IMAGE MIGRATION COMPLETED"
                    );
                    System.out.println(
                            "Migrated: "
                                    + migrated.get()
                    );
                    System.out.println(
                            "Failed: "
                                    + failed.get()
                    );
                    System.out.println(
                            "========================================"
                    );

                    break;
                }

                // =============================================
                // MIGRATE ONE ARTICLE
                // =============================================

                migrateSingleArticle(
                        target.getId()
                );

                totalProcessed.incrementAndGet();

                // =============================================
                // SMALL DELAY
                // =============================================
                //
                // Prevents hammering publishers and Railway.
                //

                Thread.sleep(300);

            }

        } catch (InterruptedException e) {

            Thread.currentThread().interrupt();

            System.out.println(
                    "Image migration thread interrupted."
            );

        } catch (Exception e) {

            System.out.println(
                    "Background image migration failed: "
                            + e.getMessage()
            );

            e.printStackTrace();

        } finally {

            running.set(false);
        }
    }

    // =====================================================
    // MIGRATE ONE ARTICLE
    // =====================================================

    private void migrateSingleArticle(Long newsId) {

        try {

            News news =
                    newsRepository
                            .findById(newsId)
                            .orElse(null);

            if (news == null) {
                return;
            }

            // Another process may already have migrated it.

            if (!isOldLocalImage(
                    news.getImageUrl()
            )) {
                return;
            }

            System.out.println();
            System.out.println(
                    "----------------------------------------"
            );

            System.out.println(
                    "MIGRATING NEWS ID: "
                            + news.getId()
            );

            System.out.println(
                    "TITLE: "
                            + news.getTitle()
            );

            System.out.println(
                    "OLD IMAGE: "
                            + news.getImageUrl()
            );

            String category =
                    news.getCategory();

            if (category == null ||
                    category.isBlank()) {

                category = "General";
            }

            String sourceUrl =
                    news.getSourceUrl();

            String resolvedImage;

            // =============================================
            // NO SOURCE URL
            // =============================================

            if (sourceUrl == null ||
                    sourceUrl.isBlank()) {

                resolvedImage =
                        buildFallbackImageUrl(
                                category
                        );

            } else {

                // =============================================
                // RESOLVE IMAGE
                // =============================================

                resolvedImage =
                        articleImageService.resolveImage(
                                null,
                                sourceUrl,
                                category
                        );

                if (resolvedImage == null ||
                        resolvedImage.isBlank()) {

                    resolvedImage =
                            buildFallbackImageUrl(
                                    category
                            );
                }
            }

            final String finalImage =
                    resolvedImage;

            // =============================================
            // INDEPENDENT TRANSACTION
            // =============================================

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
                                finalImage
                        );

                        newsRepository.save(
                                current
                        );
                    }
            );

            migrated.incrementAndGet();

            System.out.println(
                    "NEW IMAGE: "
                            + finalImage
            );

            System.out.println(
                    "DATABASE COMMIT SUCCESSFUL."
            );

            System.out.println(
                    "MIGRATED COUNT: "
                            + migrated.get()
            );

            System.out.println(
                    "----------------------------------------"
            );

        } catch (Exception e) {

            failed.incrementAndGet();

            System.out.println();
            System.out.println(
                    "IMAGE MIGRATION FAILED"
            );

            System.out.println(
                    "News ID: "
                            + newsId
            );

            System.out.println(
                    "Error: "
                            + e.getMessage()
            );

            e.printStackTrace();
        }
    }

    // =====================================================
    // STATUS
    // =====================================================

    public String getStatus() {

        long remaining =
                newsRepository
                        .findAll()
                        .stream()
                        .filter(
                                news ->
                                        news != null &&
                                        isOldLocalImage(
                                                news.getImageUrl()
                                        )
                        )
                        .count();

        return "{"
                + "\"running\":"
                + running.get()
                + ","
                + "\"processed\":"
                + totalProcessed.get()
                + ","
                + "\"migrated\":"
                + migrated.get()
                + ","
                + "\"failed\":"
                + failed.get()
                + ","
                + "\"remaining\":"
                + remaining
                + "}";
    }

    // =====================================================
    // OLD LOCAL IMAGE CHECK
    // =====================================================

    private boolean isOldLocalImage(
            String imageUrl) {

        return imageUrl != null &&
                imageUrl.startsWith(
                        "/uploads/news/"
                );
    }

    // =====================================================
    // FALLBACK IMAGE
    // =====================================================

    private String buildFallbackImageUrl(
            String category) {

        String safeCategory =
                category == null ||
                        category.isBlank()
                        ? "General"
                        : category.trim();

        return "/images/fallback?category="
                + encodeCategory(
                        safeCategory
                );
    }

    // =====================================================
    // CATEGORY ENCODING
    // =====================================================

    private String encodeCategory(
            String category) {

        return category
                .replace(" ", "%20")
                .replace("&", "%26")
                .replace("?", "%3F")
                .replace("#", "%23");
    }
}