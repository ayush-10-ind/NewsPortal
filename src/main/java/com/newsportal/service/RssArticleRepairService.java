package com.newsportal.service;

import com.newsportal.entity.News;
import com.newsportal.entity.NewsSourceType;
import com.newsportal.repository.NewsRepository;
import com.newsportal.source.MultiSourceNewsFetcherService;
import com.newsportal.source.NewsSection;
import com.newsportal.source.RssNewsFetcherService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class RssArticleRepairService {

    private static final Logger logger = LoggerFactory.getLogger(RssArticleRepairService.class);
    private static final String PLACEHOLDER = "Article content is being prepared.";
    private static final int MAX_REPAIR_BATCH = 50;
    private static final int MIN_USABLE_SOURCE_LENGTH = 80;

    private final NewsRepository newsRepository;
    private final MultiSourceNewsFetcherService multiSourceNewsFetcherService;
    private final NewsArticleGenerationService articleGenerationService;

    public RssArticleRepairService(
            NewsRepository newsRepository,
            MultiSourceNewsFetcherService multiSourceNewsFetcherService,
            NewsArticleGenerationService articleGenerationService) {
        this.newsRepository = newsRepository;
        this.multiSourceNewsFetcherService = multiSourceNewsFetcherService;
        this.articleGenerationService = articleGenerationService;
    }

    @Scheduled(initialDelay = 20000, fixedDelay = 120000)
    public void repairScheduled() {
        try {
            int repaired = repairArticles();
            if (repaired > 0) {
                logger.info("RSS repair queued {} existing article(s) for Ashna generation.", repaired);
            }
        } catch (Exception e) {
            logger.error("RSS article repair job failed: {}", e.getMessage(), e);
        }
    }

    public int repairArticles() {
        List<News> allNews = newsRepository.findAll();
        if (allNews == null || allNews.isEmpty()) return 0;

        Map<String, RssNewsFetcherService.RssArticle> feedByUrl = new HashMap<>();
        Map<String, RssNewsFetcherService.RssArticle> feedByTitle = new HashMap<>();
        fetchCurrentRssArticles(feedByUrl, feedByTitle);

        int repaired = 0;

        for (News news : allNews) {
            if (repaired >= MAX_REPAIR_BATCH) break;
            if (!isRssArticle(news) || !needsGeneration(news)) continue;

            RssNewsFetcherService.RssArticle rssArticle = findMatchingFeedArticle(
                    news, feedByUrl, feedByTitle);

            String sourceContent = rssArticle != null
                    ? clean(rssArticle.getDescription())
                    : clean(news.getContent());

            boolean usingExistingContent = rssArticle == null;

            if (sourceContent == null || sourceContent.length() < MIN_USABLE_SOURCE_LENGTH ||
                    PLACEHOLDER.equalsIgnoreCase(sourceContent)) {
                logger.debug("RSS repair skipped article id={} because no usable source content was found.",
                        news.getId());
                continue;
            }

            try {
                news.setContent(sourceContent);

                if (rssArticle != null) {
                    String feedAuthor = clean(rssArticle.getAuthor());
                    if (feedAuthor != null &&
                            (news.getAuthor() == null || news.getAuthor().isBlank() ||
                                    "Unknown".equalsIgnoreCase(news.getAuthor().trim()))) {
                        news.setAuthor(feedAuthor);
                    }
                }

                News saved = newsRepository.saveAndFlush(news);

                logger.info("RSS article source ready. Queueing Ashna generation: id={}, title={}, source={}",
                        saved.getId(), saved.getTitle(), usingExistingContent ? "database" : "live-rss");

                articleGenerationService.generateArticleAsync(saved.getId());
                repaired++;

            } catch (Exception e) {
                logger.error("RSS article repair failed: id={}, errorType={}, error={}",
                        news.getId(), e.getClass().getSimpleName(), e.getMessage(), e);
            }
        }

        return repaired;
    }

    private boolean isRssArticle(News news) {
        if (news == null) return false;

        String sourceName = clean(news.getSourceName());
        String sourceUrl = clean(news.getSourceUrl());

        if (sourceUrl == null) return false;
        if (news.getSourceType() == NewsSourceType.EXTERNAL_API) return true;

        return sourceName != null && sourceName.toLowerCase(Locale.ENGLISH).contains("rss");
    }

    private boolean needsGeneration(News news) {
        String content = clean(news.getContent());
        if (content == null || content.isBlank()) return true;
        if (PLACEHOLDER.equalsIgnoreCase(content)) return true;
        if (content.length() < 1500) return true;
        return countParagraphBreaks(content) < 3;
    }

    private int countParagraphBreaks(String content) {
        int count = 0;
        for (int i = 0; i < content.length() - 1; i++) {
            if (content.charAt(i) == '\n' && content.charAt(i + 1) == '\n') count++;
        }
        return count;
    }

    private void fetchCurrentRssArticles(
            Map<String, RssNewsFetcherService.RssArticle> feedByUrl,
            Map<String, RssNewsFetcherService.RssArticle> feedByTitle) {

        for (NewsSection section : NewsSection.values()) {
            try {
                List<RssNewsFetcherService.RssArticle> articles =
                        multiSourceNewsFetcherService.fetchRssForSection(section);

                if (articles == null) continue;

                for (RssNewsFetcherService.RssArticle article : articles) {
                    if (article == null) continue;

                    String url = normalizeUrl(article.getUrl());
                    if (url != null) feedByUrl.put(url, article);

                    String title = normalizeTitle(article.getTitle());
                    if (title != null) feedByTitle.put(title, article);
                }
            } catch (Exception e) {
                logger.warn("RSS repair feed fetch failed for section={}: {}",
                        section.getDisplayName(), e.getMessage());
            }
        }
    }

    private RssNewsFetcherService.RssArticle findMatchingFeedArticle(
            News news,
            Map<String, RssNewsFetcherService.RssArticle> feedByUrl,
            Map<String, RssNewsFetcherService.RssArticle> feedByTitle) {

        String sourceUrl = normalizeUrl(news.getSourceUrl());
        if (sourceUrl != null) {
            RssNewsFetcherService.RssArticle article = feedByUrl.get(sourceUrl);
            if (article != null) return article;
        }

        String title = normalizeTitle(news.getTitle());
        if (title != null) return feedByTitle.get(title);

        return null;
    }

    private String normalizeUrl(String value) {
        String cleaned = clean(value);
        if (cleaned == null) return null;

        try {
            URI uri = URI.create(cleaned);
            String host = uri.getHost();
            if (host == null || host.isBlank()) return removeTrailingSlash(cleaned);

            String path = uri.getPath() == null ? "" : uri.getPath();
            return host.toLowerCase(Locale.ENGLISH) + removeTrailingSlash(path);
        } catch (Exception ignored) {
            return removeTrailingSlash(cleaned).toLowerCase(Locale.ENGLISH);
        }
    }

    private String normalizeTitle(String value) {
        String cleaned = clean(value);
        if (cleaned == null) return null;
        return cleaned.toLowerCase(Locale.ENGLISH).replaceAll("\\s+", " ").trim();
    }

    private String removeTrailingSlash(String value) {
        if (value == null) return null;
        String result = value.trim();
        while (result.length() > 1 && result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private String clean(String value) {
        if (value == null) return null;
        String cleaned = value.trim();
        return cleaned.isBlank() ? null : cleaned;
    }
}
