package com.newsportal.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.newsportal.entity.News;
import com.newsportal.entity.NewsSourceType;
import com.newsportal.repository.NewsRepository;
import com.newsportal.source.MultiSourceNewsFetcherService;
import com.newsportal.source.NewsSection;
import com.newsportal.source.RssNewsFetcherService;

import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class RssNewsImportService {

    private final MultiSourceNewsFetcherService
            multiSourceNewsFetcherService;

    private final NewsRepository newsRepository;

    private final AshnaArticleAnalyzerService
            ashnaArticleAnalyzerService;

    private final ArticleImageService
            articleImageService;

    private final ObjectMapper objectMapper;

    // =====================================================
    // QUALITY THRESHOLD
    // =====================================================

    private static final int MIN_QUALITY_SCORE = 3;

    // =====================================================
    // CONSTRUCTOR
    // =====================================================

    public RssNewsImportService(
            MultiSourceNewsFetcherService
                    multiSourceNewsFetcherService,
            NewsRepository newsRepository,
            AshnaArticleAnalyzerService
                    ashnaArticleAnalyzerService,
            ArticleImageService articleImageService,
            ObjectMapper objectMapper) {

        this.multiSourceNewsFetcherService =
                multiSourceNewsFetcherService;

        this.newsRepository =
                newsRepository;

        this.ashnaArticleAnalyzerService =
                ashnaArticleAnalyzerService;

        this.articleImageService =
                articleImageService;

        this.objectMapper =
                objectMapper;
    }

    // =====================================================
    // IMPORT ONE SECTION
    // =====================================================

    public int importSection(
            NewsSection section) {

        if (section == null) {

            System.out.println(
                    "RSS IMPORT SKIPPED: Section is null."
            );

            return 0;
        }

        System.out.println();
        System.out.println(
                "========================================"
        );

        System.out.println(
                "AGNIPRESS RSS IMPORT"
        );

        System.out.println(
                "Section: "
                        + section.getDisplayName()
        );

        System.out.println(
                "========================================"
        );

        // =================================================
        // FETCH RSS ARTICLES
        // =================================================

        List<RssNewsFetcherService.RssArticle>
                articles =
                multiSourceNewsFetcherService
                        .fetchRssForSection(
                                section
                        );

        int importedCount = 0;

        int duplicateCount = 0;

        int rejectedCount = 0;

        int failedCount = 0;

        int fallbackImageCount = 0;

        System.out.println();

        System.out.println(
                "RSS articles received: "
                        + articles.size()
        );

        // =================================================
        // PROCESS EACH ARTICLE
        // =================================================

        for (int index = 0;
             index < articles.size();
             index++) {

            RssNewsFetcherService.RssArticle article =
                    articles.get(index);

            int articleNumber =
                    index + 1;

            System.out.println();

            System.out.println(
                    "========================================"
            );

            System.out.println(
                    "PROCESSING RSS ARTICLE "
                            + articleNumber
                            + " / "
                            + articles.size()
            );

            System.out.println(
                    "========================================"
            );

            try {

                // =================================================
                // NULL CHECK
                // =================================================

                if (article == null) {

                    failedCount++;

                    System.out.println(
                            "STATUS: FAILED"
                    );

                    System.out.println(
                            "Reason: RSS article object is null."
                    );

                    continue;
                }

                // =================================================
                // BASIC VALIDATION
                // =================================================

                String title =
                        clean(
                                article.getTitle()
                        );

                String sourceUrl =
                        clean(
                                article.getUrl()
                        );

                System.out.println(
                        "Title: "
                                + title
                );

                System.out.println(
                        "URL: "
                                + sourceUrl
                );

                if (title == null ||
                        title.isBlank()) {

                    rejectedCount++;

                    System.out.println(
                            "STATUS: REJECTED"
                    );

                    System.out.println(
                            "Reason: Missing title."
                    );

                    continue;
                }

                if (sourceUrl == null ||
                        sourceUrl.isBlank()) {

                    rejectedCount++;

                    System.out.println(
                            "STATUS: REJECTED"
                    );

                    System.out.println(
                            "Reason: Missing source URL."
                    );

                    continue;
                }

                // =================================================
                // DUPLICATE CHECK
                // =================================================

                Optional<News> existingArticle =
                        newsRepository.findBySourceUrl(
                                sourceUrl
                        );

                if (existingArticle.isPresent()) {

                    duplicateCount++;

                    System.out.println(
                            "STATUS: DUPLICATE"
                    );

                    System.out.println(
                            "Existing database ID: "
                                    + existingArticle
                                    .get()
                                    .getId()
                    );

                    System.out.println(
                            "Existing source name: "
                                    + existingArticle
                                    .get()
                                    .getSourceName()
                    );

                    System.out.println(
                            "Reason: source_url already exists."
                    );

                    continue;
                }

                System.out.println(
                        "Duplicate check: NOT FOUND"
                );

                // =================================================
                // INITIAL VALUES
                // =================================================

                String sourceName =
                        clean(
                                article.getSourceName()
                        );

                if (sourceName == null ||
                        sourceName.isBlank()) {

                    sourceName =
                            "Unknown RSS Source";
                }

                String author =
                        clean(
                                article.getAuthor()
                        );

                if (author == null ||
                        author.isBlank()) {

                    author =
                            "Unknown";
                }

                String rawContent =
                        clean(
                                article.getDescription()
                        );

                if (rawContent == null ||
                        rawContent.isBlank()) {

                    rawContent =
                            "Article content is being prepared.";
                }

                String category =
                        section.getDisplayName();

                // =================================================
                // RSS IMAGE
                // =================================================

                String rssImageUrl =
                        clean(
                                article.getImageUrl()
                        );

                System.out.println();

                System.out.println(
                        "RSS IMAGE URL: "
                                + (
                                rssImageUrl != null &&
                                        !rssImageUrl.isBlank()
                                        ? rssImageUrl
                                        : "NOT PROVIDED"
                        )
                );

                // =================================================
                // ASHNA ANALYSIS
                // =================================================

                AshnaArticleAnalyzerService.ArticleAnalysis
                        analysis =
                        null;

                try {

                    System.out.println();

                    System.out.println(
                            "ASHNA ANALYSIS STARTED"
                    );

                    analysis =
                            ashnaArticleAnalyzerService
                                    .analyzeArticle(
                                            title,
                                            sourceName,
                                            sourceUrl,
                                            author,
                                            article.getPublishedDate(),
                                            rawContent
                                    );

                    System.out.println(
                            "ASHNA ANALYSIS COMPLETED"
                    );

                } catch (Exception ashnaException) {

                    System.out.println(
                            "ASHNA ANALYSIS FAILED"
                    );

                    System.out.println(
                            "Reason: "
                                    + ashnaException
                                    .getMessage()
                    );

                    /*
                     * We do not automatically reject the
                     * article when Ashna is temporarily
                     * unavailable.
                     *
                     * The original RSS data can still
                     * be imported safely.
                     */
                }

                // =================================================
                // APPLY ASHNA RESULT
                // =================================================

                if (analysis != null) {

                    JsonNode analysisNode =
                            objectMapper.valueToTree(
                                    analysis
                            );

                    boolean newsworthy =
                            getBoolean(
                                    analysisNode,
                                    "newsworthy",
                                    true
                            );

                    int qualityScore =
                            getInt(
                                    analysisNode,
                                    "qualityScore",
                                    5
                            );

                    System.out.println();

                    System.out.println(
                            "ASHNA RESULT"
                    );

                    System.out.println(
                            "Newsworthy: "
                                    + newsworthy
                    );

                    System.out.println(
                            "Quality Score: "
                                    + qualityScore
                    );

                    // =================================================
                    // NEWSWORTHINESS CHECK
                    // =================================================

                    if (!newsworthy) {

                        rejectedCount++;

                        System.out.println(
                                "STATUS: REJECTED"
                        );

                        System.out.println(
                                "Reason: Ashna marked article as not newsworthy."
                        );

                        continue;
                    }

                    // =================================================
                    // QUALITY CHECK
                    // =================================================

                    if (qualityScore <
                            MIN_QUALITY_SCORE) {

                        rejectedCount++;

                        System.out.println(
                                "STATUS: REJECTED"
                        );

                        System.out.println(
                                "Reason: Ashna quality score "
                                        + qualityScore
                                        + " is below minimum "
                                        + MIN_QUALITY_SCORE
                        );

                        continue;
                    }

                    // =================================================
                    // ASHNA HEADLINE
                    // =================================================

                    String ashnaHeadline =
                            getText(
                                    analysisNode,
                                    "headline"
                            );

                    if (ashnaHeadline != null &&
                            !ashnaHeadline.isBlank()) {

                        title =
                                ashnaHeadline.trim();
                    }

                    // =================================================
                    // ASHNA CONTENT
                    // =================================================

                    String ashnaContent =
                            getText(
                                    analysisNode,
                                    "content"
                            );

                    if (ashnaContent != null &&
                            !ashnaContent.isBlank()) {

                        rawContent =
                                ashnaContent.trim();

                    } else {

                        String summary =
                                getText(
                                        analysisNode,
                                        "summary"
                                );

                        if (summary != null &&
                                !summary.isBlank()) {

                            rawContent =
                                    summary.trim();
                        }
                    }

                    // =================================================
                    // ASHNA AUTHOR
                    // =================================================

                    String ashnaAuthor =
                            getText(
                                    analysisNode,
                                    "author"
                            );

                    if (ashnaAuthor != null &&
                            !ashnaAuthor.isBlank()) {

                        author =
                                ashnaAuthor.trim();
                    }

                    // =================================================
                    // ASHNA SECTION
                    // =================================================

                    String ashnaSection =
                            getText(
                                    analysisNode,
                                    "section"
                            );

                    NewsSection resolvedSection =
                            resolveSection(
                                    ashnaSection,
                                    section
                            );

                    category =
                            resolvedSection
                                    .getDisplayName();

                    System.out.println(
                            "Final section: "
                                    + category
                    );

                    System.out.println(
                            "Final headline: "
                                    + title
                    );
                }

                // =================================================
                // IMAGE RESOLUTION
                // =================================================

                System.out.println();

                System.out.println(
                        "IMAGE RESOLUTION STARTED"
                );

                /*
                 * IMPORTANT:
                 *
                 * We now pass the actual image extracted
                 * from the RSS feed.
                 *
                 * Previously this was:
                 *
                 * resolveImage(null, sourceUrl, category)
                 *
                 * which meant the RSS image was completely
                 * ignored.
                 */

                String resolvedImage =
                        articleImageService
                                .resolveImage(
                                        rssImageUrl,
                                        sourceUrl,
                                        category
                                );

                // =================================================
                // FALLBACK IMAGE
                // =================================================

                if (resolvedImage == null ||
                        resolvedImage.isBlank()) {

                    resolvedImage =
                            "/images/fallback?category="
                                    + category;
                }

                System.out.println(
                        "Resolved image: "
                                + resolvedImage
                );

                if (resolvedImage.startsWith(
                        "/images/fallback")) {

                    fallbackImageCount++;

                    System.out.println(
                            "Image type: AGNIPRESS FALLBACK"
                    );

                } else if (rssImageUrl != null &&
                        !rssImageUrl.isBlank() &&
                        resolvedImage.equals(
                                rssImageUrl
                        )) {

                    System.out.println(
                            "Image type: RSS SOURCE IMAGE"
                    );

                } else {

                    System.out.println(
                            "Image type: EXTERNAL IMAGE"
                    );
                }

                // =================================================
                // CREATE NEWS ENTITY
                // =================================================

                News news =
                        new News();

                news.setTitle(
                        title
                );

                news.setAuthor(
                        author
                );

                news.setCategory(
                        category
                );

                news.setContent(
                        rawContent
                );

                news.setImageUrl(
                        resolvedImage
                );

                news.setSourceUrl(
                        sourceUrl
                );

                news.setSourceName(
                        sourceName
                );

                news.setSourceType(
                        NewsSourceType.EXTERNAL_API
                );

                news.setPublishedDate(
                        convertPublishedDate(
                                article.getPublishedDate()
                        )
                );

                news.setViewCount(
                        0L
                );

                // =================================================
                // DATABASE SAVE
                // =================================================

                System.out.println();

                System.out.println(
                        "DATABASE SAVE STARTED"
                );

                /*
                 * saveAndFlush() forces Hibernate to execute
                 * the INSERT immediately instead of waiting
                 * for the surrounding transaction.
                 */

                News savedNews =
                        newsRepository.saveAndFlush(
                                news
                        );

                System.out.println(
                        "DATABASE SAVE COMPLETED"
                );

                System.out.println(
                        "Generated database ID: "
                                + savedNews.getId()
                );

                // =================================================
                // DATABASE READ-BACK VERIFICATION
                // =================================================

                System.out.println();

                System.out.println(
                        "DATABASE READ-BACK VERIFICATION"
                );

                Optional<News> persistedArticle =
                        newsRepository.findBySourceUrl(
                                sourceUrl
                        );

                if (persistedArticle.isEmpty()) {

                    failedCount++;

                    System.out.println(
                            "STATUS: FAILED"
                    );

                    System.out.println(
                            "Reason: Article was saved but "
                                    + "could not be read back "
                                    + "using source_url."
                    );

                    System.out.println(
                            "URL checked: "
                                    + sourceUrl
                    );

                    continue;
                }

                News verifiedArticle =
                        persistedArticle.get();

                // =================================================
                // SUCCESS
                // =================================================

                importedCount++;

                System.out.println();

                System.out.println(
                        "STATUS: IMPORTED"
                );

                System.out.println(
                        "Database ID: "
                                + verifiedArticle.getId()
                );

                System.out.println(
                        "Title: "
                                + verifiedArticle.getTitle()
                );

                System.out.println(
                        "Category: "
                                + verifiedArticle.getCategory()
                );

                System.out.println(
                        "Source Name: "
                                + verifiedArticle.getSourceName()
                );

                System.out.println(
                        "Source Type: "
                                + verifiedArticle.getSourceType()
                );

                System.out.println(
                        "Source URL: "
                                + verifiedArticle.getSourceUrl()
                );

                System.out.println(
                        "Image URL: "
                                + verifiedArticle.getImageUrl()
                );

                System.out.println(
                        "Published Date: "
                                + verifiedArticle.getPublishedDate()
                );

            } catch (Exception e) {

                failedCount++;

                System.out.println();

                System.out.println(
                        "STATUS: FAILED"
                );

                System.out.println(
                        "Title: "
                                + (
                                article != null
                                        ? article.getTitle()
                                        : "Unknown"
                        )
                );

                System.out.println(
                        "Error Type: "
                                + e.getClass()
                                .getSimpleName()
                );

                System.out.println(
                        "Error Message: "
                                + e.getMessage()
                );

                e.printStackTrace();
            }
        }

        // =================================================
        // FINAL SUMMARY
        // =================================================

        System.out.println();

        System.out.println(
                "========================================"
        );

        System.out.println(
                "RSS IMPORT COMPLETED"
        );

        System.out.println(
                "========================================"
        );

        System.out.println(
                "Section: "
                        + section.getDisplayName()
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
                "Duplicates skipped: "
                        + duplicateCount
        );

        System.out.println(
                "Rejected by validation/Ashna: "
                        + rejectedCount
        );

        System.out.println(
                "Failed articles: "
                        + failedCount
        );

        System.out.println(
                "Fallback images: "
                        + fallbackImageCount
        );

        System.out.println(
                "========================================"
        );

        return importedCount;
    }

    // =====================================================
    // IMPORT ALL RSS SOURCES
    // =====================================================

    public int importAllRssSources() {

        int totalImported = 0;

        for (NewsSection section :
                NewsSection.values()) {

            try {

                totalImported +=
                        importSection(
                                section
                        );

            } catch (Exception e) {

                System.out.println(
                        "RSS SECTION FAILED: "
                                + section.getDisplayName()
                );

                System.out.println(
                        "Error: "
                                + e.getMessage()
                );

                e.printStackTrace();
            }
        }

        System.out.println();

        System.out.println(
                "========================================"
        );

        System.out.println(
                "AGNIPRESS RSS IMPORT ALL COMPLETED"
        );

        System.out.println(
                "Total imported: "
                        + totalImported
        );

        System.out.println(
                "========================================"
        );

        return totalImported;
    }

    // =====================================================
    // SECTION RESOLUTION
    // =====================================================

    private NewsSection resolveSection(
            String value,
            NewsSection fallback) {

        if (value == null ||
                value.isBlank()) {

            return fallback;
        }

        String normalized =
                value.trim()
                        .toUpperCase(
                                Locale.ENGLISH
                        );

        for (NewsSection section :
                NewsSection.values()) {

            if (section.name()
                    .equalsIgnoreCase(normalized)) {

                return section;
            }

            if (section.getDisplayName()
                    .equalsIgnoreCase(
                            value.trim()
                    )) {

                return section;
            }
        }

        return fallback;
    }

    // =====================================================
    // JSON TEXT
    // =====================================================

    private String getText(
            JsonNode node,
            String field) {

        if (node == null) {

            return null;
        }

        JsonNode value =
                node.get(field);

        if (value == null ||
                value.isNull()) {

            return null;
        }

        String text =
                value.asText();

        if (text == null ||
                text.isBlank()) {

            return null;
        }

        return text.trim();
    }

    // =====================================================
    // JSON INTEGER
    // =====================================================

    private int getInt(
            JsonNode node,
            String field,
            int defaultValue) {

        if (node == null) {

            return defaultValue;
        }

        JsonNode value =
                node.get(field);

        if (value == null ||
                !value.isNumber()) {

            return defaultValue;
        }

        return value.asInt(
                defaultValue
        );
    }

    // =====================================================
    // JSON BOOLEAN
    // =====================================================

    private boolean getBoolean(
            JsonNode node,
            String field,
            boolean defaultValue) {

        if (node == null) {

            return defaultValue;
        }

        JsonNode value =
                node.get(field);

        if (value == null ||
                !value.isBoolean()) {

            return defaultValue;
        }

        return value.asBoolean(
                defaultValue
        );
    }

    // =====================================================
    // CLEAN STRING
    // =====================================================

    private String clean(
            String value) {

        if (value == null) {

            return null;
        }

        String cleaned =
                value.trim();

        if (cleaned.isBlank()) {

            return null;
        }

        return cleaned;
    }

    // =====================================================
    // DATE CONVERSION
    // =====================================================

    private LocalDate convertPublishedDate(
            String publishedDate) {

        if (publishedDate == null ||
                publishedDate.isBlank()) {

            return LocalDate.now();
        }

        String value =
                publishedDate.trim();

        // =================================================
        // ISO OFFSET DATE
        // =================================================

        try {

            return OffsetDateTime
                    .parse(value)
                    .toLocalDate();

        } catch (Exception ignored) {
        }

        // =================================================
        // ISO ZONED DATE
        // =================================================

        try {

            return ZonedDateTime
                    .parse(value)
                    .toLocalDate();

        } catch (Exception ignored) {
        }

        // =================================================
        // RFC 1123
        // =================================================

        try {

            return ZonedDateTime
                    .parse(
                            value,
                            DateTimeFormatter.RFC_1123_DATE_TIME
                    )
                    .toLocalDate();

        } catch (Exception ignored) {
        }

        // =================================================
        // COMMON RSS FORMATS
        // =================================================

        List<DateTimeFormatter> formatters =
                Arrays.asList(

                        DateTimeFormatter.ofPattern(
                                "EEE, dd MMM yyyy HH:mm:ss Z",
                                Locale.ENGLISH
                        ),

                        DateTimeFormatter.ofPattern(
                                "EEE, dd MMM yyyy HH:mm Z",
                                Locale.ENGLISH
                        ),

                        DateTimeFormatter.ofPattern(
                                "dd MMM yyyy HH:mm:ss Z",
                                Locale.ENGLISH
                        ),

                        DateTimeFormatter.ofPattern(
                                "yyyy-MM-dd",
                                Locale.ENGLISH
                        )
                );

        for (DateTimeFormatter formatter :
                formatters) {

            try {

                if (formatter.toString()
                        .contains("yyyy-MM-dd")) {

                    return LocalDate.parse(
                            value,
                            formatter
                    );
                }

                return ZonedDateTime
                        .parse(
                                value,
                                formatter
                        )
                        .toLocalDate();

            } catch (Exception ignored) {
            }
        }

        System.out.println(
                "Could not parse RSS published date: "
                        + publishedDate
        );

        return LocalDate.now();
    }
}