package com.newsportal.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.newsportal.entity.News;
import com.newsportal.entity.NewsSourceType;
import com.newsportal.repository.NewsRepository;
import com.newsportal.source.MultiSourceNewsFetcherService;
import com.newsportal.source.NewsSection;
import com.newsportal.source.RssNewsFetcherService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger logger =
            LoggerFactory.getLogger(RssNewsImportService.class);

    private final MultiSourceNewsFetcherService
            multiSourceNewsFetcherService;

    private final NewsRepository newsRepository;

    private final AshnaArticleAnalyzerService
            ashnaArticleAnalyzerService;

    private final ArticleImageService
            articleImageService;

    private final ObjectMapper objectMapper;

    private static final int MIN_QUALITY_SCORE = 3;

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
        this.newsRepository = newsRepository;
        this.ashnaArticleAnalyzerService =
                ashnaArticleAnalyzerService;
        this.articleImageService = articleImageService;
        this.objectMapper = objectMapper;
    }

    public int importSection(NewsSection section) {

        if (section == null) {
            logger.warn("RSS import skipped: section is null.");
            return 0;
        }

        String sectionName = section.getDisplayName();

        logger.info("AgniPress RSS import started: {}", sectionName);

        List<RssNewsFetcherService.RssArticle> articles =
                multiSourceNewsFetcherService.fetchRssForSection(section);

        if (articles == null) {
            logger.warn("RSS fetch returned null for section: {}", sectionName);
            return 0;
        }

        int importedCount = 0;
        int duplicateCount = 0;
        int rejectedCount = 0;
        int failedCount = 0;
        int fallbackImageCount = 0;

        for (RssNewsFetcherService.RssArticle article : articles) {

            try {

                if (article == null) {
                    failedCount++;
                    logger.warn(
                            "RSS article failed: article object is null. Section={}",
                            sectionName
                    );
                    continue;
                }

                String title = clean(article.getTitle());
                String sourceUrl = clean(article.getUrl());

                if (title == null || title.isBlank()) {
                    rejectedCount++;
                    logger.warn(
                            "RSS article rejected: missing title. Section={}",
                            sectionName
                    );
                    continue;
                }

                if (sourceUrl == null || sourceUrl.isBlank()) {
                    rejectedCount++;
                    logger.warn(
                            "RSS article rejected: missing source URL. Title={}",
                            title
                    );
                    continue;
                }

                Optional<News> existingArticle =
                        newsRepository.findBySourceUrl(sourceUrl);

                if (existingArticle.isPresent()) {
                    duplicateCount++;
                    continue;
                }

                String sourceName = clean(article.getUrl());

                if (sourceName != null) {
                    try {
                        sourceName = java.net.URI.create(sourceName).getHost();
                    } catch (Exception ignored) {
                        // Keep the original URL when host parsing fails.
                    }
                }

                if (sourceName == null || sourceName.isBlank()) {
                    sourceName = "Unknown RSS Source";
                }

                String author = clean(article.getAuthor());

                if (author == null || author.isBlank()) {
                    author = "Unknown";
                }

                String rawContent = clean(article.getDescription());

                if (rawContent == null || rawContent.isBlank()) {
                    rawContent = "Article content is being prepared.";
                }

                String category = sectionName;
                String rssImageUrl = clean(article.getImageUrl());

                AshnaArticleAnalyzerService.ArticleAnalysis analysis = null;

                try {
                    analysis = ashnaArticleAnalyzerService.analyzeArticle(
                            title,
                            sourceName,
                            sourceUrl,
                            author,
                            article.getPublishedDate(),
                            rawContent
                    );
                } catch (Exception ashnaException) {
                    logger.warn(
                            "Ashna analysis unavailable. Continuing RSS import. Title={}",
                            title
                    );
                }

                if (analysis != null) {

                    JsonNode analysisNode =
                            objectMapper.valueToTree(analysis);

                    boolean newsworthy = getBoolean(
                            analysisNode,
                            "newsworthy",
                            true
                    );

                    int qualityScore = getInt(
                            analysisNode,
                            "qualityScore",
                            5
                    );

                    if (!newsworthy) {
                        rejectedCount++;
                        continue;
                    }

                    if (qualityScore < MIN_QUALITY_SCORE) {
                        rejectedCount++;
                        continue;
                    }

                    String ashnaHeadline = getText(
                            analysisNode,
                            "headline"
                    );

                    if (ashnaHeadline != null && !ashnaHeadline.isBlank()) {
                        title = ashnaHeadline.trim();
                    }

                    String ashnaContent = getText(
                            analysisNode,
                            "content"
                    );

                    if (ashnaContent != null && !ashnaContent.isBlank()) {
                        rawContent = ashnaContent.trim();
                    } else {
                        String summary = getText(
                                analysisNode,
                                "summary"
                        );

                        if (summary != null && !summary.isBlank()) {
                            rawContent = summary.trim();
                        }
                    }

                    String ashnaAuthor = getText(
                            analysisNode,
                            "author"
                    );

                    if (ashnaAuthor != null && !ashnaAuthor.isBlank()) {
                        author = ashnaAuthor.trim();
                    }

                    String ashnaSection = getText(
                            analysisNode,
                            "section"
                    );

                    NewsSection resolvedSection = resolveSection(
                            ashnaSection,
                            section
                    );

                    category = resolvedSection.getDisplayName();
                }

                String resolvedImage = articleImageService.resolveImage(
                        rssImageUrl,
                        sourceUrl,
                        category
                );

                if (resolvedImage == null || resolvedImage.isBlank()) {
                    resolvedImage = "/images/fallback?category=" + category;
                    fallbackImageCount++;
                }

                News news = new News();

                news.setTitle(title);
                news.setAuthor(author);
                news.setCategory(category);
                news.setContent(rawContent);
                news.setImageUrl(resolvedImage);
                news.setSourceUrl(sourceUrl);
                news.setSourceName(sourceName);
                news.setSourceType(NewsSourceType.EXTERNAL_API);
                news.setPublishedDate(
                        convertPublishedDate(article.getPublishedDate())
                );
                news.setViewCount(0L);

                News savedNews = newsRepository.saveAndFlush(news);

                Optional<News> persistedArticle =
                        newsRepository.findBySourceUrl(sourceUrl);

                if (persistedArticle.isEmpty()) {
                    failedCount++;
                    logger.error(
                            "RSS article save verification failed. Database ID={}",
                            savedNews.getId()
                    );
                    continue;
                }

                importedCount++;

            } catch (Exception e) {

                failedCount++;

                String failedTitle = article != null
                        ? clean(article.getTitle())
                        : "Unknown";

                logger.error(
                        "RSS article import failed. Title={}, Error={}",
                        failedTitle,
                        e.getMessage()
                );
            }
        }

        logger.info(
                "RSS import completed: section={}, received={}, imported={}, duplicates={}, rejected={}, failed={}, fallbackImages={}",
                sectionName,
                articles.size(),
                importedCount,
                duplicateCount,
                rejectedCount,
                failedCount,
                fallbackImageCount
        );

        return importedCount;
    }

    public int importAllRssSources() {

        int totalImported = 0;
        int totalFailed = 0;

        logger.info("AgniPress RSS import started for all sections.");

        for (NewsSection section : NewsSection.values()) {
            try {
                totalImported += importSection(section);
            } catch (Exception e) {
                totalFailed++;
                logger.error(
                        "RSS section failed: section={}, error={}",
                        section.getDisplayName(),
                        e.getMessage()
                );
            }
        }

        logger.info(
                "AgniPress RSS import all completed: imported={}, sectionFailures={}",
                totalImported,
                totalFailed
        );

        return totalImported;
    }

    private NewsSection resolveSection(String value, NewsSection fallback) {

        if (value == null || value.isBlank()) {
            return fallback;
        }

        String normalized = value.trim().toUpperCase(Locale.ENGLISH);

        for (NewsSection section : NewsSection.values()) {
            if (section.name().equalsIgnoreCase(normalized)) {
                return section;
            }

            if (section.getDisplayName().equalsIgnoreCase(value.trim())) {
                return section;
            }
        }

        return fallback;
    }

    private String getText(JsonNode node, String field) {

        if (node == null) {
            return null;
        }

        JsonNode value = node.get(field);

        if (value == null || value.isNull()) {
            return null;
        }

        String text = value.asText();

        if (text == null || text.isBlank()) {
            return null;
        }

        return text.trim();
    }

    private int getInt(JsonNode node, String field, int defaultValue) {

        if (node == null) {
            return defaultValue;
        }

        JsonNode value = node.get(field);

        if (value == null || !value.isNumber()) {
            return defaultValue;
        }

        return value.asInt(defaultValue);
    }

    private boolean getBoolean(
            JsonNode node,
            String field,
            boolean defaultValue) {

        if (node == null) {
            return defaultValue;
        }

        JsonNode value = node.get(field);

        if (value == null || !value.isBoolean()) {
            return defaultValue;
        }

        return value.asBoolean(defaultValue);
    }

    private String clean(String value) {

        if (value == null) {
            return null;
        }

        String cleaned = value.trim();

        if (cleaned.isBlank()) {
            return null;
        }

        return cleaned;
    }

    private LocalDate convertPublishedDate(String publishedDate) {

        if (publishedDate == null || publishedDate.isBlank()) {
            return LocalDate.now();
        }

        String value = publishedDate.trim();

        try {
            return OffsetDateTime.parse(value).toLocalDate();
        } catch (Exception ignored) {
        }

        try {
            return ZonedDateTime.parse(value).toLocalDate();
        } catch (Exception ignored) {
        }

        try {
            return ZonedDateTime.parse(
                    value,
                    DateTimeFormatter.RFC_1123_DATE_TIME
            ).toLocalDate();
        } catch (Exception ignored) {
        }

        List<DateTimeFormatter> formatters = Arrays.asList(
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

        for (DateTimeFormatter formatter : formatters) {
            try {
                if (formatter.toString().contains("yyyy-MM-dd")) {
                    return LocalDate.parse(value, formatter);
                }

                return ZonedDateTime.parse(value, formatter).toLocalDate();
            } catch (Exception ignored) {
            }
        }

        logger.warn("Could not parse RSS published date: {}", value);
        return LocalDate.now();
    }
}
