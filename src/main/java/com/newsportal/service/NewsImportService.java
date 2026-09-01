package com.newsportal.service;

import com.newsportal.dto.NewsApiArticleDTO;
import com.newsportal.entity.News;
import com.newsportal.entity.NewsSourceType;
import com.newsportal.repository.NewsRepository;

import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class NewsImportService {

    private final NewsApiService newsApiService;

    private final NewsRepository newsRepository;

    private final NewsArticleGenerationService
            articleGenerationService;

    private final ArticleImageService
            articleImageService;


    // =====================================================
    // CONSTRUCTOR
    // =====================================================

    public NewsImportService(
            NewsApiService newsApiService,
            NewsRepository newsRepository,
            NewsArticleGenerationService articleGenerationService,
            ArticleImageService articleImageService) {

        this.newsApiService =
                newsApiService;

        this.newsRepository =
                newsRepository;

        this.articleGenerationService =
                articleGenerationService;

        this.articleImageService =
                articleImageService;
    }


    // =====================================================
    // IMPORT NEWS
    // =====================================================

    public int importNews() {

        System.out.println();
        System.out.println(
                "========================================"
        );
        System.out.println(
                "NEWS IMPORT STARTED"
        );
        System.out.println(
                "========================================"
        );


        System.out.println(
                "Fetching latest news from News API..."
        );


        List<NewsApiArticleDTO> articles =
                newsApiService.getAllTopHeadlines();


        if (articles == null ||
                articles.isEmpty()) {

            System.out.println(
                    "No articles received."
            );

            System.out.println(
                    "NEWS IMPORT COMPLETED"
            );

            return 0;
        }


        int importedCount = 0;

        int existingCount = 0;

        int rejectedCount = 0;

        int imageLevel1Count = 0;

        int imageLevel2Count = 0;

        int imageLevel3Count = 0;


        // =================================================
        // PROCESS ARTICLES
        // =================================================

        for (NewsApiArticleDTO article :
                articles) {

            try {

                // =========================================
                // BASIC VALIDATION
                // =========================================

                if (article == null ||
                        article.getTitle() == null ||
                        article.getTitle().isBlank() ||
                        article.getUrl() == null ||
                        article.getUrl().isBlank()) {

                    System.out.println(
                            "Skipping invalid article."
                    );

                    rejectedCount++;

                    continue;
                }


                String title =
                        article.getTitle().trim();

                String sourceUrl =
                        article.getUrl().trim();


                // =========================================
                // QUALITY FILTER
                // =========================================

                if (isLowQualityArticle(article)) {

                    rejectedCount++;

                    System.out.println(
                            "REJECTED LOW-QUALITY ARTICLE: "
                                    + title
                    );

                    continue;
                }


                // =========================================
                // DUPLICATE CHECK
                // =========================================

                Optional<News> existingArticle =
                        newsRepository.findBySourceUrl(
                                sourceUrl
                        );


                if (existingArticle.isPresent()) {

                    existingCount++;

                    System.out.println(
                            "Skipping duplicate: "
                                    + title
                    );

                    continue;
                }


                // =========================================
                // CATEGORY
                // =========================================

                String category =
                        article.getCategory();


                if (category == null ||
                        category.isBlank()) {

                    category = "General";
                }


                category =
                        category.trim();


                // =========================================
                // CONTENT
                // =========================================

                String initialContent =
                        article.getDescription();


                if (initialContent == null ||
                        initialContent.isBlank()) {

                    initialContent =
                            article.getContent();
                }


                if (initialContent == null ||
                        initialContent.isBlank()) {

                    initialContent =
                            "Article content is being prepared.";
                }


                // =========================================
                // THREE-LEVEL IMAGE SYSTEM
                // =========================================

                String newsApiImage =
                        article.getUrlToImage();


                String resolvedImage =
                        articleImageService
                                .resolveImage(
                                        newsApiImage,
                                        sourceUrl,
                                        category
                                );


                if (resolvedImage == null ||
                        resolvedImage.isBlank()) {

                    resolvedImage =
                            "/images/fallback?category="
                                    + category;
                }


                // =========================================
                // IMAGE STATISTICS
                // =========================================

                if (resolvedImage.startsWith(
                        "/images/fallback")) {

                    imageLevel3Count++;

                } else if (
                        newsApiImage != null &&
                        !newsApiImage.isBlank() &&
                        resolvedImage.equals(
                                newsApiImage.trim()
                        )) {

                    imageLevel1Count++;

                } else {

                    imageLevel2Count++;
                }


                // =========================================
                // CREATE NEWS ENTITY
                // =========================================

                News news =
                        new News();


                news.setTitle(
                        title
                );


                news.setAuthor(
                        article.getAuthor()
                );


                news.setCategory(
                        category
                );


                news.setContent(
                        initialContent
                );


                news.setImageUrl(
                        resolvedImage
                );


                news.setSourceUrl(
                        sourceUrl
                );


                // =========================================
                // SOURCE
                // =========================================

                if (article.getSource() != null) {

                    news.setSourceName(
                            article.getSource()
                                    .getName()
                    );
                }


                news.setSourceType(
                        NewsSourceType.EXTERNAL_API
                );


                // =========================================
                // PUBLISHED DATE
                // =========================================

                news.setPublishedDate(
                        convertPublishedDate(
                                article.getPublishedAt()
                        )
                );


                // =========================================
                // SAVE
                // =========================================

                News savedNews =
                        newsRepository.save(
                                news
                        );


                importedCount++;


                System.out.println();
                System.out.println(
                        "IMPORTED ARTICLE"
                );

                System.out.println(
                        "Title: "
                                + savedNews.getTitle()
                );

                System.out.println(
                        "Category: "
                                + category
                );

                System.out.println(
                        "Image: "
                                + resolvedImage
                );


                // =========================================
                // ASHNA BACKGROUND GENERATION
                // =========================================

                articleGenerationService
                        .generateArticleAsync(
                                savedNews.getId()
                        );


            } catch (Exception e) {

                System.out.println(
                        "Failed to import article: "
                                + (
                                article != null
                                        ? article.getTitle()
                                        : "Unknown article"
                        )
                );

                System.out.println(
                        "Error: "
                                + e.getMessage()
                );
            }
        }


        // =================================================
        // IMPORT SUMMARY
        // =================================================

        System.out.println();
        System.out.println(
                "========================================"
        );

        System.out.println(
                "NEWS IMPORT COMPLETED"
        );

        System.out.println(
                "========================================"
        );

        System.out.println(
                "Articles received: "
                        + articles.size()
        );

        System.out.println(
                "New articles imported: "
                        + importedCount
        );

        System.out.println(
                "Existing articles skipped: "
                        + existingCount
        );

        System.out.println(
                "Low-quality articles rejected: "
                        + rejectedCount
        );

        System.out.println(
                "Image Level 1 - NewsAPI: "
                        + imageLevel1Count
        );

        System.out.println(
                "Image Level 2 - Article metadata: "
                        + imageLevel2Count
        );

        System.out.println(
                "Image Level 3 - AgniPress fallback: "
                        + imageLevel3Count
        );

        System.out.println(
                "========================================"
        );


        return importedCount;
    }


    // =====================================================
    // LOW-QUALITY / PROMOTIONAL FILTER
    // =====================================================

    private boolean isLowQualityArticle(
            NewsApiArticleDTO article) {

        String title =
                article.getTitle() == null
                        ? ""
                        : article.getTitle();


        String description =
                article.getDescription() == null
                        ? ""
                        : article.getDescription();


        String source =
                article.getSource() == null ||
                        article.getSource().getName() == null
                        ? ""
                        : article.getSource().getName();


        String text =
                (
                        title
                                + " "
                                + description
                                + " "
                                + source
                )
                        .toLowerCase(Locale.ENGLISH);


        // =================================================
        // VERY SHORT / EMPTY ARTICLES
        // =================================================

        if (title.trim().length() < 20) {

            return true;
        }


        // =================================================
        // PRESS RELEASE / ADVERTISING SIGNALS
        // =================================================

        String[] blockedPhrases = {

                "press release",

                "sponsored",

                "advertorial",

                "paid content",

                "promotional content",

                "promotion",

                "coupon",

                "promo code",

                "discount code",

                "buy now",

                "limited time offer",

                "special offer",

                "giveaway",

                "sweepstakes",

                "shopping deal",

                "deal alert",

                "best deals",

                "product deals",

                "affiliate",

                "partner content",

                "brand partnership",

                "sponsored content",

                "advertisement"
        };


        for (String phrase :
                blockedPhrases) {

            if (text.contains(phrase)) {

                return true;
            }
        }


        // =================================================
        // CORPORATE PR / APPOINTMENT SIGNALS
        // =================================================

        /*
         * These are intentionally phrase based rather than
         * blocking the word "announces" by itself.
         *
         * Genuine news often contains "announces".
         */

        String[] corporatePressReleasePhrases = {

                "announces retirement of",
                "announces appointment of",
                "announces the appointment of",
                "appoints as senior",
                "appoints as chief",
                "appointed as chief",
                "new managing director",
                "new senior managing director",
                "joins as chief",
                "named as chief",
                "corporate announcement"
        };


        for (String phrase :
                corporatePressReleasePhrases) {

            if (title
                    .toLowerCase(Locale.ENGLISH)
                    .contains(phrase)) {

                return true;
            }
        }


        // =================================================
        // VERY OBVIOUS COMMERCIAL TITLES
        // =================================================

        String lowerTitle =
                title.toLowerCase(
                        Locale.ENGLISH
                );


        String[] commercialTitleSignals = {

                "best ",
                "top deals",
                "where to buy",
                "how to buy",
                "buying guide",
                "gift guide",
                "shopping guide",
                "review:",
                "deal:",
                "sale:",
                "discount:"
        };


        for (String signal :
                commercialTitleSignals) {

            if (lowerTitle.startsWith(signal)) {

                return true;
            }
        }


        return false;
    }


    // =====================================================
    // PUBLISHED DATE
    // =====================================================

    private LocalDate convertPublishedDate(
            String publishedAt) {

        if (publishedAt == null ||
                publishedAt.isBlank()) {

            return LocalDate.now();
        }


        try {

            return OffsetDateTime
                    .parse(
                            publishedAt
                    )
                    .toLocalDate();

        } catch (Exception e) {

            System.out.println(
                    "Could not parse published date: "
                            + publishedAt
            );

            return LocalDate.now();
        }
    }
}