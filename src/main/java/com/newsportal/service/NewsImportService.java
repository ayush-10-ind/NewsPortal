package com.newsportal.service;

import com.newsportal.dto.NewsApiArticleDTO;
import com.newsportal.entity.News;
import com.newsportal.entity.NewsSourceType;
import com.newsportal.repository.NewsRepository;
import com.newsportal.source.NewsSection;
import com.newsportal.source.SectionRouterService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class NewsImportService {

    private static final Logger logger =
            LoggerFactory.getLogger(NewsImportService.class);

    private final NewsApiService newsApiService;
    private final NewsRepository newsRepository;
    private final NewsArticleGenerationService articleGenerationService;
    private final ArticleImageService articleImageService;
    private final SectionRouterService sectionRouterService;
    private final AshnaArticleAnalyzerService ashnaArticleAnalyzerService;

    public NewsImportService(
            NewsApiService newsApiService,
            NewsRepository newsRepository,
            NewsArticleGenerationService articleGenerationService,
            ArticleImageService articleImageService,
            SectionRouterService sectionRouterService,
            AshnaArticleAnalyzerService ashnaArticleAnalyzerService) {
        this.newsApiService = newsApiService;
        this.newsRepository = newsRepository;
        this.articleGenerationService = articleGenerationService;
        this.articleImageService = articleImageService;
        this.sectionRouterService = sectionRouterService;
        this.ashnaArticleAnalyzerService = ashnaArticleAnalyzerService;
    }

    public int importNews() {
        logger.info("AgniPress NewsAPI import started");

        List<NewsApiArticleDTO> articles =
                newsApiService.getAllTopHeadlines();

        logger.info(
                "NewsAPI returned {} articles",
                articles == null ? 0 : articles.size()
        );

        if (articles == null || articles.isEmpty()) {
            logger.info("NewsAPI import completed: no articles received");
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

        for (NewsApiArticleDTO article : articles) {
            try {
                if (article == null ||
                        article.getTitle() == null ||
                        article.getTitle().isBlank() ||
                        article.getUrl() == null ||
                        article.getUrl().isBlank()) {
                    rejectedCount++;
                    continue;
                }

                String title = article.getTitle().trim();
                String sourceUrl = article.getUrl().trim();

                if (isLowQualityArticle(article)) {
                    rejectedCount++;
                    continue;
                }

                Optional<News> existingArticle =
                        newsRepository.findBySourceUrl(sourceUrl);

                if (existingArticle.isPresent()) {
                    existingCount++;
                    continue;
                }

                String sourceName = "Unknown";
                if (article.getSource() != null &&
                        article.getSource().getName() != null &&
                        !article.getSource().getName().isBlank()) {
                    sourceName = article.getSource().getName().trim();
                }

                String author = article.getAuthor();
                String publishedDate = article.getPublishedAt();

                String initialContent = article.getDescription();
                if (initialContent == null || initialContent.isBlank()) {
                    initialContent = article.getContent();
                }
                if (initialContent == null || initialContent.isBlank()) {
                    initialContent = "Article content is being prepared.";
                }

                String rawCategory = article.getCategory();
                if (rawCategory == null || rawCategory.isBlank()) {
                    rawCategory = "General";
                }

                NewsSection initialSection =
                        sectionRouterService.resolveSection(rawCategory);

                AshnaArticleAnalyzerService.ArticleAnalysis analysis = null;

                try {
                    analysis = ashnaArticleAnalyzerService.analyzeArticle(
                            title,
                            sourceName,
                            sourceUrl,
                            author,
                            publishedDate,
                            initialContent
                    );
                    ashnaSuccessCount++;
                } catch (Exception ashnaException) {
                    ashnaFailureCount++;
                    logger.warn(
                            "Ashna analysis failed: errorType={}, message={}",
                            ashnaException.getClass().getSimpleName(),
                            ashnaException.getMessage()
                    );
                }

                NewsSection section = initialSection;
                if (analysis != null &&
                        analysis.getSection() != null &&
                        !analysis.getSection().isBlank()) {
                    section = sectionRouterService.resolveSection(
                            analysis.getSection()
                    );
                }

                String category = section.getDisplayName();

                if (analysis != null) {
                    if (!analysis.isNewsworthy()) {
                        ashnaRejectedCount++;
                        rejectedCount++;
                        continue;
                    }
                    if (analysis.getQualityScore() < 4) {
                        ashnaRejectedCount++;
                        rejectedCount++;
                        continue;
                    }
                }

                if (!sectionRouterService.hasSource(section)) {
                    noSourceCount++;
                    continue;
                }

                String finalTitle = title;
                if (analysis != null &&
                        analysis.getHeadline() != null &&
                        !analysis.getHeadline().isBlank()) {
                    finalTitle = analysis.getHeadline().trim();
                }

                // =================================================
                // DUPLICATE STORY CHECK
                // =================================================
                // A story can arrive from different providers with
                // different URLs. Source URL alone is therefore not
                // enough. Collapse identical normalized titles inside
                // the same category before saving.
                if (newsRepository.existsByTitleAndCategoryIgnoreCase(
                        finalTitle,
                        category
                )) {
                    existingCount++;
                    continue;
                }

                String finalContent = initialContent;
                if (analysis != null &&
                        analysis.getContent() != null &&
                        !analysis.getContent().isBlank()) {
                    finalContent = analysis.getContent().trim();
                }

                String finalAuthor = author;
                if (analysis != null &&
                        analysis.getAuthor() != null &&
                        !analysis.getAuthor().isBlank()) {
                    finalAuthor = analysis.getAuthor().trim();
                }

                String newsApiImage = article.getUrlToImage();
                String resolvedImage = articleImageService.resolveImage(
                        newsApiImage,
                        sourceUrl,
                        category
                );

                if (resolvedImage == null || resolvedImage.isBlank()) {
                    resolvedImage = "/images/fallback?category=" + category;
                }

                if (resolvedImage.startsWith("/images/fallback")) {
                    imageLevel3Count++;
                } else if (newsApiImage != null &&
                        !newsApiImage.isBlank() &&
                        resolvedImage.equals(newsApiImage.trim())) {
                    imageLevel1Count++;
                } else {
                    imageLevel2Count++;
                }

                News news = new News();
                news.setTitle(finalTitle);
                news.setAuthor(finalAuthor);
                news.setCategory(category);
                news.setContent(finalContent);
                news.setImageUrl(resolvedImage);
                news.setSourceUrl(sourceUrl);
                news.setSourceName(sourceName);
                news.setSourceType(NewsSourceType.EXTERNAL_API);
                news.setPublishedDate(convertPublishedDate(publishedDate));

                News savedNews = newsRepository.save(news);
                importedCount++;

                articleGenerationService.generateArticleAsync(
                        savedNews.getId()
                );

            } catch (Exception e) {
                logger.warn(
                        "NewsAPI article import failed: errorType={}, message={}",
                        e.getClass().getSimpleName(),
                        e.getMessage()
                );
            }
        }

        logger.info(
                "AgniPress NewsAPI import completed: received={}, imported={}, duplicates={}, rejected={}, noSource={}, ashnaSuccess={}, ashnaFailures={}, ashnaRejected={}, imageL1={}, imageL2={}, imageL3={}",
                articles.size(),
                importedCount,
                existingCount,
                rejectedCount,
                noSourceCount,
                ashnaSuccessCount,
                ashnaFailureCount,
                ashnaRejectedCount,
                imageLevel1Count,
                imageLevel2Count,
                imageLevel3Count
        );

        return importedCount;
    }

    private boolean isLowQualityArticle(NewsApiArticleDTO article) {
        String title = article.getTitle() == null ? "" : article.getTitle();
        String description = article.getDescription() == null ? "" : article.getDescription();
        String source = article.getSource() == null ||
                article.getSource().getName() == null
                ? ""
                : article.getSource().getName();

        String text = (title + " " + description + " " + source)
                .toLowerCase(Locale.ENGLISH);

        if (title.trim().length() < 20) {
            return true;
        }

        String[] blockedPhrases = {
                "press release", "sponsored", "advertorial", "paid content",
                "promotional content", "promotion", "coupon", "promo code",
                "discount code", "buy now", "limited time offer", "special offer",
                "giveaway", "sweepstakes", "shopping deal", "deal alert",
                "best deals", "product deals", "affiliate", "partner content",
                "brand partnership", "sponsored content", "advertisement"
        };

        for (String phrase : blockedPhrases) {
            if (text.contains(phrase)) {
                return true;
            }
        }

        String[] corporatePressReleasePhrases = {
                "announces retirement of", "announces appointment of",
                "announces the appointment of", "appoints as senior",
                "appoints as chief", "appointed as chief", "new managing director",
                "new senior managing director", "joins as chief", "named as chief",
                "corporate announcement"
        };

        String lowerTitle = title.toLowerCase(Locale.ENGLISH);
        for (String phrase : corporatePressReleasePhrases) {
            if (lowerTitle.contains(phrase)) {
                return true;
            }
        }

        String[] commercialTitleSignals = {
                "best ", "top deals", "where to buy", "how to buy", "buying guide",
                "gift guide", "shopping guide", "review:", "deal:", "sale:", "discount:"
        };

        for (String signal : commercialTitleSignals) {
            if (lowerTitle.startsWith(signal)) {
                return true;
            }
        }

        return false;
    }

    private LocalDate convertPublishedDate(String publishedAt) {
        if (publishedAt == null || publishedAt.isBlank()) {
            return LocalDate.now();
        }

        try {
            return OffsetDateTime.parse(publishedAt).toLocalDate();
        } catch (Exception e) {
            logger.debug("Published date parse failed: value={}", publishedAt);
            return LocalDate.now();
        }
    }
}
