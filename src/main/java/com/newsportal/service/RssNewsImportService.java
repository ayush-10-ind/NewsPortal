package com.newsportal.service;

import com.newsportal.entity.News;
import com.newsportal.entity.NewsSourceType;
import com.newsportal.repository.NewsRepository;
import com.newsportal.source.MultiSourceNewsFetcherService;
import com.newsportal.source.NewsSection;
import com.newsportal.source.RssNewsFetcherService;
import com.newsportal.source.WebArticleContentExtractorService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
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

    private static final Logger logger = LoggerFactory.getLogger(RssNewsImportService.class);
    private static final String PLACEHOLDER = "Article content is being prepared.";
    private static final int MIN_SOURCE_CONTENT_LENGTH = 80;

    private final MultiSourceNewsFetcherService multiSourceNewsFetcherService;
    private final NewsRepository newsRepository;
    private final ArticleImageService articleImageService;
    private final NasaApodImageService nasaApodImageService;
    private final NewsArticleGenerationService articleGenerationService;
    private final WebArticleContentExtractorService webArticleContentExtractorService;

    public RssNewsImportService(
            MultiSourceNewsFetcherService multiSourceNewsFetcherService,
            NewsRepository newsRepository,
            ArticleImageService articleImageService,
            NasaApodImageService nasaApodImageService,
            NewsArticleGenerationService articleGenerationService,
            WebArticleContentExtractorService webArticleContentExtractorService) {
        this.multiSourceNewsFetcherService = multiSourceNewsFetcherService;
        this.newsRepository = newsRepository;
        this.articleImageService = articleImageService;
        this.nasaApodImageService = nasaApodImageService;
        this.articleGenerationService = articleGenerationService;
        this.webArticleContentExtractorService = webArticleContentExtractorService;
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
        int webFallbackCount = 0;
        int nasaImageCount = 0;
        int generationQueuedCount = 0;

        for (RssNewsFetcherService.RssArticle article : articles) {
            try {
                if (article == null) {
                    failedCount++;
                    logger.warn("RSS article failed: article object is null. Section={}", sectionName);
                    continue;
                }

                String title = clean(article.getTitle());
                String sourceUrl = clean(article.getUrl());
                String rawContent = clean(article.getDescription());

                if (title == null) {
                    rejectedCount++;
                    logger.warn("RSS article rejected: missing title. Section={}", sectionName);
                    continue;
                }

                if (sourceUrl == null) {
                    rejectedCount++;
                    logger.warn("RSS article rejected: missing source URL. Title={}", title);
                    continue;
                }

                Optional<News> existingArticle = newsRepository.findBySourceUrl(sourceUrl);
                if (existingArticle.isPresent()) {
                    duplicateCount++;
                    continue;
                }

                String sourceContent = rawContent;
                boolean usedWebFallback = false;

                if (!isUsableSourceContent(sourceContent)) {
                    logger.info(
                            "RSS description unusable. Trying publisher page fallback: title={}, url={}",
                            title,
                            sourceUrl
                    );

                    sourceContent = clean(
                            webArticleContentExtractorService.fetchArticleText(sourceUrl)
                    );
                    usedWebFallback = isUsableSourceContent(sourceContent);

                    if (usedWebFallback) {
                        webFallbackCount++;
                        logger.info(
                                "Publisher page fallback succeeded: title={}, extractedChars={}",
                                title,
                                sourceContent.length()
                        );
                    }
                }

                if (!isUsableSourceContent(sourceContent)) {
                    rejectedCount++;
                    logger.warn(
                            "RSS article rejected: no usable source content after RSS + web fallback. Title={}, contentLength={}",
                            title,
                            sourceContent == null ? 0 : sourceContent.length()
                    );
                    continue;
                }

                String sourceName = resolveSourceName(sourceUrl);
                String author = clean(article.getAuthor());
                if (author == null) {
                    author = "Unknown";
                }

                String category = sectionName;
                String rssImageUrl = clean(article.getImageUrl());
                LocalDate publishedDate = convertPublishedDate(article.getPublishedDate());

                String resolvedImage = rssImageUrl;
                if (resolvedImage == null) {
                    resolvedImage = articleImageService.resolveImage(
                            null,
                            sourceUrl,
                            category
                    );
                }

                if (isFallbackImage(resolvedImage)) {
                    String nasaImage = nasaApodImageService.resolveImage(
                            sourceUrl,
                            title,
                            publishedDate
                    );

                    if (nasaImage != null && !nasaImage.isBlank()) {
                        resolvedImage = nasaImage;
                        nasaImageCount++;
                        logger.info("NASA APOD image fallback succeeded: title={}, date={}",
                                title, publishedDate);
                    }
                }

                if (resolvedImage == null || resolvedImage.isBlank()) {
                    resolvedImage = "/images/fallback?category=" + category;
                    fallbackImageCount++;
                } else if (isFallbackImage(resolvedImage)) {
                    fallbackImageCount++;
                }

                News news = new News();
                news.setTitle(title);
                news.setAuthor(author);
                news.setCategory(category);
                news.setContent(sourceContent);
                news.setImageUrl(resolvedImage);
                news.setSourceUrl(sourceUrl);
                news.setSourceName(sourceName);
                news.setSourceType(NewsSourceType.EXTERNAL_API);
                news.setPublishedDate(publishedDate);
                news.setViewCount(0L);

                News savedNews = newsRepository.save(news);

                importedCount++;

                articleGenerationService.generateArticleAsync(savedNews.getId());
                generationQueuedCount++;

                logger.info(
                        "RSS article persisted and Ashna generation queued: id={}, title={}, source={}, webFallback={}, nasaImage={}",
                        savedNews.getId(),
                        savedNews.getTitle(),
                        sourceName,
                        usedWebFallback,
                        !isFallbackImage(resolvedImage)
                );

            } catch (Exception e) {
                failedCount++;
                String failedTitle = article != null ? clean(article.getTitle()) : "Unknown";
                logger.error(
                        "RSS article import failed. Title={}, ErrorType={}, Error={}",
                        failedTitle,
                        e.getClass().getSimpleName(),
                        e.getMessage(),
                        e
                );
            }
        }

        logger.info(
                "RSS import completed: section={}, received={}, imported={}, duplicates={}, rejected={}, failed={}, fallbackImages={}, webFallbacks={}, nasaImages={}, generationQueued={}",
                sectionName,
                articles.size(),
                importedCount,
                duplicateCount,
                rejectedCount,
                failedCount,
                fallbackImageCount,
                webFallbackCount,
                nasaImageCount,
                generationQueuedCount
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
                        "RSS section failed: section={}, errorType={}, error={}",
                        section.getDisplayName(),
                        e.getClass().getSimpleName(),
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

    private boolean isUsableSourceContent(String content) {
        return content != null
                && content.length() >= MIN_SOURCE_CONTENT_LENGTH
                && !PLACEHOLDER.equalsIgnoreCase(content);
    }

    private boolean isFallbackImage(String imageUrl) {
        return imageUrl != null && imageUrl.startsWith("/images/fallback");
    }

    private String resolveSourceName(String sourceUrl) {
        try {
            URI uri = URI.create(sourceUrl);
            String host = uri.getHost();
            if (host != null && !host.isBlank()) {
                return host.replaceFirst("^www\\.", "");
            }
        } catch (Exception ignored) {
        }
        return "RSS Source";
    }

    private String clean(String value) {
        if (value == null) return null;
        String cleaned = value.trim();
        return cleaned.isBlank() ? null : cleaned;
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
            return ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toLocalDate();
        } catch (Exception ignored) {
        }

        List<DateTimeFormatter> formatters = Arrays.asList(
                DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss Z", Locale.ENGLISH),
                DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm Z", Locale.ENGLISH),
                DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm:ss Z", Locale.ENGLISH),
                DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ENGLISH)
        );

        for (DateTimeFormatter formatter : formatters) {
            try {
                if ("yyyy-MM-dd".equals(formatter.toString())) {
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
