package com.newsportal.service;

import com.newsportal.entity.News;
import com.newsportal.repository.NewsRepository;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class NewsImageMigrationService {

    private final NewsRepository newsRepository;
    private final ArticleImageService articleImageService;

    // =====================================================
    // CONSTRUCTOR
    // =====================================================

    public NewsImageMigrationService(
            NewsRepository newsRepository,
            ArticleImageService articleImageService) {

        this.newsRepository = newsRepository;
        this.articleImageService = articleImageService;
    }

    // =====================================================
    // BATCH IMAGE MIGRATION
    // =====================================================
    //
    // IMPORTANT:
    //
    // There is NO @Transactional on this method.
    //
    // Every newsRepository.saveAndFlush(news) below runs
    // in its own Spring Data transaction.
    //
    // Therefore:
    //
    // Article 1 -> SAVE -> COMMIT
    // Article 2 -> SAVE -> COMMIT
    // Article 3 -> SAVE -> COMMIT
    //
    // If Railway stops the application after Article 3,
    // Articles 1-3 remain safely migrated.
    //
    // =====================================================

    public int migrateOldImages(int requestedBatchSize) {

        // =================================================
        // SAFETY LIMIT
        // =================================================
        //
        // We deliberately keep this small.
        //
        // Minimum = 1
        // Maximum = 5
        //
        int batchSize = Math.max(
                1,
                Math.min(
                        requestedBatchSize,
                        5
                )
        );

        System.out.println();
        System.out.println(
                "========================================"
        );

        System.out.println(
                "SAFE BATCH NEWS IMAGE MIGRATION STARTED"
        );

        System.out.println(
                "Requested batch size: "
                        + requestedBatchSize
        );

        System.out.println(
                "Actual batch size: "
                        + batchSize
        );

        System.out.println(
                "Each article will be committed separately."
        );

        System.out.println(
                "========================================"
        );

        // =================================================
        // LOAD NEWS
        // =================================================

        List<News> allNews =
                newsRepository.findAll(
                        Sort.by(
                                Sort.Direction.ASC,
                                "id"
                        )
                );

        if (allNews == null ||
                allNews.isEmpty()) {

            System.out.println(
                    "No news articles found."
            );

            return 0;
        }

        int oldImageCount = 0;
        int migratedCount = 0;
        int skippedCount = 0;
        int failedCount = 0;

        // =================================================
        // PROCESS BATCH
        // =================================================

        for (News news : allNews) {

            if (oldImageCount >= batchSize) {
                break;
            }

            try {

                if (news == null) {
                    continue;
                }

                String imageUrl =
                        news.getImageUrl();

                // =================================================
                // ONLY PROCESS OLD LOCAL IMAGE PATHS
                // =================================================

                if (!isOldLocalImage(imageUrl)) {

                    skippedCount++;

                    continue;
                }

                oldImageCount++;

                System.out.println();
                System.out.println(
                        "----------------------------------------"
                );

                System.out.println(
                        "PROCESSING "
                                + oldImageCount
                                + " / "
                                + batchSize
                );

                System.out.println(
                        "News ID: "
                                + news.getId()
                );

                System.out.println(
                        "Title: "
                                + news.getTitle()
                );

                System.out.println(
                        "Old image: "
                                + imageUrl
                );

                System.out.println(
                        "Source URL: "
                                + news.getSourceUrl()
                );

                // =================================================
                // CATEGORY
                // =================================================

                String category =
                        news.getCategory();

                if (category == null ||
                        category.isBlank()) {

                    category = "General";
                }

                // =================================================
                // SOURCE URL
                // =================================================

                String sourceUrl =
                        news.getSourceUrl();

                String resolvedImage;

                // =================================================
                // NO SOURCE URL
                // =================================================

                if (sourceUrl == null ||
                        sourceUrl.isBlank()) {

                    resolvedImage =
                            buildFallbackImageUrl(
                                    category
                            );

                    System.out.println(
                            "No source URL."
                    );

                    System.out.println(
                            "Using fallback."
                    );

                } else {

                    // =================================================
                    // RESOLVE IMAGE
                    // =================================================

                    resolvedImage =
                            articleImageService.resolveImage(
                                    null,
                                    sourceUrl,
                                    category
                            );

                    // =================================================
                    // SAFETY FALLBACK
                    // =================================================

                    if (resolvedImage == null ||
                            resolvedImage.isBlank()) {

                        resolvedImage =
                                buildFallbackImageUrl(
                                        category
                                );

                        System.out.println(
                                "Image resolution returned empty."
                        );

                        System.out.println(
                                "Using fallback."
                        );
                    }
                }

                // =================================================
                // UPDATE IMAGE URL
                // =================================================

                news.setImageUrl(
                        resolvedImage
                );

                // =================================================
                // IMPORTANT
                // =================================================
                //
                // saveAndFlush() is intentionally used.
                //
                // Because there is NO surrounding
                // @Transactional method, Spring Data creates
                // a separate transaction for this save.
                //
                // This article is committed independently.
                //
                // =================================================

                newsRepository.saveAndFlush(
                        news
                );

                migratedCount++;

                System.out.println(
                        "NEW IMAGE: "
                                + resolvedImage
                );

                System.out.println(
                        "DATABASE COMMIT SUCCESSFUL."
                );

                System.out.println(
                        "Migration successful."
                );

                System.out.println(
                        "----------------------------------------"
                );

            } catch (Exception e) {

                failedCount++;

                System.out.println();
                System.out.println(
                        "IMAGE MIGRATION FAILED"
                );

                System.out.println(
                        "News ID: "
                                + (
                                news != null
                                        ? news.getId()
                                        : "Unknown"
                        )
                );

                System.out.println(
                        "Error: "
                                + e.getMessage()
                );

                e.printStackTrace();

                // =================================================
                // IMPORTANT
                // =================================================
                //
                // We DO NOT modify the failed record.
                //
                // Its old /uploads/news/... URL remains.
                //
                // Therefore a future migration request can
                // retry it.
                //
            }
        }

        // =====================================================
        // SUMMARY
        // =====================================================

        System.out.println();
        System.out.println(
                "========================================"
        );

        System.out.println(
                "SAFE BATCH MIGRATION COMPLETED"
        );

        System.out.println(
                "========================================"
        );

        System.out.println(
                "Total articles in database: "
                        + allNews.size()
        );

        System.out.println(
                "Batch size: "
                        + batchSize
        );

        System.out.println(
                "Old images processed: "
                        + oldImageCount
        );

        System.out.println(
                "Images migrated: "
                        + migratedCount
        );

        System.out.println(
                "Articles skipped: "
                        + skippedCount
        );

        System.out.println(
                "Migration failures: "
                        + failedCount
        );

        System.out.println(
                "Local images downloaded: 0"
        );

        System.out.println(
                "Local images stored: 0"
        );

        System.out.println(
                "========================================"
        );

        return migratedCount;
    }

    // =====================================================
    // CHECK OLD LOCAL IMAGE
    // =====================================================

    private boolean isOldLocalImage(
            String imageUrl) {

        return imageUrl != null &&
                imageUrl.startsWith(
                        "/uploads/news/"
                );
    }

    // =====================================================
    // FALLBACK
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