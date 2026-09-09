package com.newsportal.service;

import com.newsportal.dto.NewsApiArticleDTO;
import com.newsportal.entity.News;
import com.newsportal.entity.NewsSourceType;
import com.newsportal.repository.NewsRepository;
import com.newsportal.source.NewsSection;
import com.newsportal.source.SectionRouterService;

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

    private final SectionRouterService
            sectionRouterService;

    private final AshnaArticleAnalyzerService
            ashnaArticleAnalyzerService;


    // =====================================================
    // CONSTRUCTOR
    // =====================================================

    public NewsImportService(
            NewsApiService newsApiService,
            NewsRepository newsRepository,
            NewsArticleGenerationService articleGenerationService,
            ArticleImageService articleImageService,
            SectionRouterService sectionRouterService,
            AshnaArticleAnalyzerService ashnaArticleAnalyzerService) {

        this.newsApiService =
                newsApiService;

        this.newsRepository =
                newsRepository;

        this.articleGenerationService =
                articleGenerationService;

        this.articleImageService =
                articleImageService;

        this.sectionRouterService =
                sectionRouterService;

        this.ashnaArticleAnalyzerService =
                ashnaArticleAnalyzerService;
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
                "AGNIPRESS NEWS IMPORT STARTED"
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

        int noSourceCount = 0;

        int ashnaSuccessCount = 0;

        int ashnaFailureCount = 0;

        int ashnaRejectedCount = 0;

        int imageLevel1Count = 0;

        int imageLevel2Count = 0;

        int imageLevel3Count = 0;


        // =================================================
        // SECTION STATISTICS
        // =================================================

        int indiaCount = 0;

        int worldCount = 0;

        int sportsCount = 0;

        int animeCount = 0;

        int businessCount = 0;

        int technologyCount = 0;

        int entertainmentCount = 0;

        int scienceCount = 0;

        int gamingCount = 0;


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
                // BASIC QUALITY FILTER
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
                // SOURCE INFORMATION
                // =========================================

                String sourceName =
                        "Unknown";

                if (article.getSource() != null &&
                        article.getSource().getName() != null &&
                        !article.getSource().getName().isBlank()) {

                    sourceName =
                            article.getSource()
                                    .getName()
                                    .trim();
                }


                String author =
                        article.getAuthor();


                String publishedDate =
                        article.getPublishedAt();


                // =========================================
                // RAW CONTENT
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
                // ORIGINAL SECTION
                //
                // This is only the initial hint.
                // Ashna will make the final classification.
                // =========================================

                String rawCategory =
                        article.getCategory();


                if (rawCategory == null ||
                        rawCategory.isBlank()) {

                    rawCategory =
                            "General";
                }


                NewsSection initialSection =
                        sectionRouterService
                                .resolveSection(
                                        rawCategory
                                );


                // =========================================
                // ASHNA ARTICLE ANALYSIS
                // =========================================

                System.out.println();
                System.out.println(
                        "----------------------------------------"
                );

                System.out.println(
                        "ASHNA ANALYSIS STARTED"
                );

                System.out.println(
                        "Title: "
                                + title
                );

                System.out.println(
                        "Source: "
                                + sourceName
                );


                AshnaArticleAnalyzerService
                        .ArticleAnalysis analysis = null;


                try {

                    analysis =
                            ashnaArticleAnalyzerService
                                    .analyzeArticle(
                                            title,
                                            sourceName,
                                            sourceUrl,
                                            author,
                                            publishedDate,
                                            initialContent
                                    );


                    ashnaSuccessCount++;


                    System.out.println(
                            "ASHNA ANALYSIS COMPLETED"
                    );

                    System.out.println(
                            "Section: "
                                    + analysis.getSection()
                    );

                    System.out.println(
                            "Quality Score: "
                                    + analysis.getQualityScore()
                    );

                    System.out.println(
                            "Newsworthy: "
                                    + analysis.isNewsworthy()
                    );


                } catch (Exception ashnaException) {

                    ashnaFailureCount++;


                    System.out.println(
                            "ASHNA ANALYSIS FAILED"
                    );

                    System.out.println(
                            "Reason: "
                                    + ashnaException.getMessage()
                    );

                    System.out.println(
                            "Falling back to NewsAPI data."
                    );
                }


                // =========================================
                // DETERMINE FINAL SECTION
                // =========================================

                NewsSection section =
                        initialSection;


                if (analysis != null &&
                        analysis.getSection() != null &&
                        !analysis.getSection().isBlank()) {

                    section =
                            sectionRouterService
                                    .resolveSection(
                                            analysis.getSection()
                                    );
                }


                String category =
                        section.getDisplayName();


                // =========================================
                // ASHNA NEWSWORTHINESS CHECK
                // =========================================

                if (analysis != null) {

                    if (!analysis.isNewsworthy()) {

                        ashnaRejectedCount++;

                        rejectedCount++;


                        System.out.println(
                                "REJECTED BY ASHNA: "
                                        + title
                        );

                        System.out.println(
                                "Reason: Article was not "
                                        + "considered newsworthy."
                        );

                        continue;
                    }


                    if (analysis.getQualityScore() < 4) {

                        ashnaRejectedCount++;

                        rejectedCount++;


                        System.out.println(
                                "REJECTED BY ASHNA QUALITY FILTER: "
                                        + title
                        );

                        System.out.println(
                                "Quality Score: "
                                        + analysis.getQualityScore()
                        );

                        continue;
                    }
                }


                // =========================================
                // CHECK SECTION SOURCE
                // =========================================

                if (!sectionRouterService
                        .hasSource(section)) {

                    System.out.println(
                            "No usable source configured for section: "
                                    + category
                    );

                    noSourceCount++;

                    continue;
                }


                // =========================================
                // ASHNA HEADLINE
                // =========================================

                String finalTitle =
                        title;


                if (analysis != null &&
                        analysis.getHeadline() != null &&
                        !analysis.getHeadline().isBlank()) {

                    finalTitle =
                            analysis.getHeadline().trim();
                }


                // =========================================
                // ASHNA CONTENT
                // =========================================

                String finalContent =
                        initialContent;


                if (analysis != null &&
                        analysis.getContent() != null &&
                        !analysis.getContent().isBlank()) {

                    finalContent =
                            analysis.getContent().trim();
                }


                // =========================================
                // ASHNA AUTHOR
                // =========================================

                String finalAuthor =
                        author;


                if (analysis != null &&
                        analysis.getAuthor() != null &&
                        !analysis.getAuthor().isBlank()) {

                    finalAuthor =
                            analysis.getAuthor().trim();
                }


                // =========================================
                // SECTION STATISTICS
                // =========================================

                switch (section) {

                    case INDIA:
                        indiaCount++;
                        break;

                    case WORLD:
                        worldCount++;
                        break;

                    case SPORTS:
                        sportsCount++;
                        break;

                    case ANIME:
                        animeCount++;
                        break;

                    case BUSINESS:
                        businessCount++;
                        break;

                    case TECHNOLOGY:
                        technologyCount++;
                        break;

                    case ENTERTAINMENT:
                        entertainmentCount++;
                        break;

                    case SCIENCE:
                        scienceCount++;
                        break;

                    case GAMING:
                        gamingCount++;
                        break;
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
                        finalTitle
                );


                news.setAuthor(
                        finalAuthor
                );


                news.setCategory(
                        category
                );


                news.setContent(
                        finalContent
                );


                news.setImageUrl(
                        resolvedImage
                );


                news.setSourceUrl(
                        sourceUrl
                );


                // =========================================
                // SOURCE NAME
                // =========================================

                news.setSourceName(
                        sourceName
                );


                news.setSourceType(
                        NewsSourceType.EXTERNAL_API
                );


                // =========================================
                // PUBLISHED DATE
                // =========================================

                news.setPublishedDate(
                        convertPublishedDate(
                                publishedDate
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


                // =========================================
                // IMPORT LOG
                // =========================================

                System.out.println();

                System.out.println(
                        "----------------------------------------"
                );

                System.out.println(
                        "IMPORTED ARTICLE"
                );

                System.out.println(
                        "Title: "
                                + savedNews.getTitle()
                );

                System.out.println(
                        "Section: "
                                + category
                );

                System.out.println(
                        "Source: "
                                + savedNews.getSourceName()
                );

                System.out.println(
                        "Ashna Quality: "
                                + (
                                analysis != null
                                        ? analysis.getQualityScore()
                                        : "N/A"
                        )
                );

                System.out.println(
                        "Image: "
                                + resolvedImage
                );

                System.out.println(
                        "----------------------------------------"
                );


                // =========================================
                // EXISTING ASHNA ARTICLE GENERATION
                // =========================================
                //
                // This remains separate from the analyzer.
                //
                // Analyzer:
                // classification + quality + metadata
                //
                // Generation:
                // final editorial article
                //
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
                "AGNIPRESS NEWS IMPORT COMPLETED"
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
                "No usable section source: "
                        + noSourceCount
        );


        // =============================================
        // ASHNA COUNTS
        // =============================================

        System.out.println();

        System.out.println(
                "ASHNA ANALYSIS BREAKDOWN"
        );

        System.out.println(
                "Ashna analysis successful: "
                        + ashnaSuccessCount
        );

        System.out.println(
                "Ashna analysis failed: "
                        + ashnaFailureCount
        );

        System.out.println(
                "Rejected by Ashna: "
                        + ashnaRejectedCount
        );


        // =============================================
        // SECTION COUNTS
        // =============================================

        System.out.println();

        System.out.println(
                "SECTION BREAKDOWN"
        );

        System.out.println(
                "India: "
                        + indiaCount
        );

        System.out.println(
                "World: "
                        + worldCount
        );

        System.out.println(
                "Sports: "
                        + sportsCount
        );

        System.out.println(
                "Anime: "
                        + animeCount
        );

        System.out.println(
                "Business: "
                        + businessCount
        );

        System.out.println(
                "Technology: "
                        + technologyCount
        );

        System.out.println(
                "Entertainment: "
                        + entertainmentCount
        );

        System.out.println(
                "Science: "
                        + scienceCount
        );

        System.out.println(
                "Gaming: "
                        + gamingCount
        );


        // =============================================
        // IMAGE COUNTS
        // =============================================

        System.out.println();

        System.out.println(
                "IMAGE BREAKDOWN"
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