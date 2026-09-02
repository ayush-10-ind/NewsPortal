package com.newsportal.service;

import com.newsportal.entity.News;
import com.newsportal.repository.NewsRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

        this.newsRepository =
                newsRepository;

        this.articleImageService =
                articleImageService;
    }


    // =====================================================
    // MIGRATE OLD LOCAL IMAGES
    // =====================================================
    //
    // Converts old:
    //
    // /uploads/news/xxxxx.webp
    //
    // into:
    //
    // external image URL
    //
    // OR:
    //
    // /images/fallback?category=...
    //
    // No image files are downloaded or stored.
    //
    // =====================================================

    @Transactional
    public int migrateOldImages() {

        System.out.println();
        System.out.println(
                "========================================"
        );

        System.out.println(
                "OLD NEWS IMAGE MIGRATION STARTED"
        );

        System.out.println(
                "========================================"
        );


        // =================================================
        // FIND ALL NEWS
        // =================================================

        List<News> allNews =
                newsRepository.findAll();


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
        // PROCESS ARTICLES
        // =================================================

        for (News news : allNews) {

            try {

                if (news == null) {

                    continue;
                }


                String imageUrl =
                        news.getImageUrl();


                // =================================================
                // ONLY MIGRATE OLD LOCAL IMAGE PATHS
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
                        "OLD IMAGE FOUND"
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


                // =================================================
                // NO SOURCE URL
                // =================================================

                if (sourceUrl == null ||
                        sourceUrl.isBlank()) {

                    String fallback =
                            buildFallbackImageUrl(
                                    category
                            );


                    news.setImageUrl(
                            fallback
                    );


                    newsRepository.save(
                            news
                    );


                    migratedCount++;


                    System.out.println(
                            "No source URL."
                    );

                    System.out.println(
                            "Using fallback: "
                                    + fallback
                    );

                    continue;
                }


                // =================================================
                // RESOLVE NEW IMAGE
                // =================================================

                String resolvedImage =
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
                }


                // =================================================
                // UPDATE DATABASE ONLY
                // =================================================

                news.setImageUrl(
                        resolvedImage
                );


                newsRepository.save(
                        news
                );


                migratedCount++;


                System.out.println(
                        "NEW IMAGE: "
                                + resolvedImage
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
                "OLD NEWS IMAGE MIGRATION COMPLETED"
        );

        System.out.println(
                "========================================"
        );

        System.out.println(
                "Total articles: "
                        + allNews.size()
        );

        System.out.println(
                "Old local images found: "
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