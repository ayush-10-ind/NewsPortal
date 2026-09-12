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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class RssArticleRepairService {

    private static final Logger logger = LoggerFactory.getLogger(RssArticleRepairService.class);
    private static final String PLACEHOLDER = "Article content is being prepared.";
    private static final int MAX_REPAIR_BATCH = 20;

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

    /*
     * Safety net for RSS articles that were imported without the final
     * Ashna generation step. Run shortly after startup and then every minute
     * so existing and newly imported RSS stories are processed quickly.
     */
    @Scheduled(initialDelay = 15000, fixedDelay = 60000)
    public void repairScheduled() {
        try {
            int repaired = repairArticles();
            if (repaired > 0) {
                logger.info("RSS repair queued {} article(s) for Ashna generation.", repaired);
            }
        } catch (Exception e) {
            logger.error("RSS article repair job failed: {}", e.getMessage(), e);
        }
    }

    public int repairArticles() {
        List<News> allNews = newsRepository.findAll();
        if (allNews == null || allNews.isEmpty()) return 0;

        Map<String, RssNewsFetcherService.RssArticle> feedArticles = fetchCurrentRssArticles();
        if (feedArticles.isEmpty()) {
            logger.warn("RSS repair found articles but no current RSS feed items were available.");
            return 0;
        }

        int repaired = 0;

        for (News news : allNews) {
            if (repaired >= MAX_REPAIR_BATCH) break;
            if (!isRssArticle(news) || !needsGeneration(news)) continue;

            String sourceUrl = normalizeUrl(news.getSourceUrl());
            if (sourceUrl == null) continue;

            RssNewsFetcherService.RssArticle rssArticle = feedArticles.get(sourceUrl);
            if (rssArticle == null) {
                rssArticle = feedArticles.get(removeTrailingSlash(sourceUrl));
            }

            if (rssArticle == null) {
                logger.debug("RSS repair could not match article id={} to a current feed item.", news.getId());
                continue;
            }

            String sourceContent = clean(rssArticle.getDescription());
            if (sourceContent == null) {
                logger.warn("RSS repair skipped article id={} because the feed has no usable description.", news.getId());
                continue;
            }

            try {
                news.setContent(sourceContent);

                String feedAuthor = clean(rssArticle.getAuthor());
                if (feedAuthor != null &&
                        (news.getAuthor() == null || news.getAuthor().isBlank() ||
                                "Unknown".equalsIgnoreCase(news.getAuthor().trim()))) {
                    news.setAuthor(feedAuthor);
                }

                News saved = newsRepository.saveAndFlush(news);

                logger.info("RSS article source restored. Queueing Ashna generation: id={}, title={}",
                        saved.getId(), saved.getTitle());

                articleGenerationService.generateArticleAsync(saved.getId());
                repaired++;

            } catch (Exception e) {
                logger.error("RSS article repair failed: id={}, error={}", news.getId(), e.getMessage(), e);
            }
        }

        return repaired;
    }

    private boolean isRssArticle(News news) {
        if (news == null) return false;

        String sourceName = clean(news.getSourceName());
        String sourceUrl = clean(news.getSourceUrl());

        if (sourceUrl == null) return false;

        // RSS articles do not always have "RSS" in their display source name.
        // For example, Yahoo Entertainment can be delivered through an RSS feed.
        // EXTERNAL_API is the source type used by the current RSS importer.
        if (news.getSourceType() == NewsSourceType.EXTERNAL_API) return true;

        return sourceName != null && sourceName.toLowerCase().contains("rss");
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

    private Map<String, RssNewsFetcherService.RssArticle> fetchCurrentRssArticles() {
        Map<String, RssNewsFetcherService.RssArticle> result = new HashMap<>();

        for (NewsSection section : NewsSection.values()) {
            try {
                List<RssNewsFetcherService.RssArticle> articles =
                        multiSourceNewsFetcherService.fetchRssForSection(section);

                if (articles == null) continue;

                for (RssNewsFetcherService.RssArticle article : articles) {
                    if (article == null) continue;

                    String url = normalizeUrl(article.getUrl());
                    if (url == null) continue;

                    result.put(url, article);
                    result.put(removeTrailingSlash(url), article);
                }
            } catch (Exception e) {
                logger.warn("RSS repair feed fetch failed for section={}: {}",
                        section.getDisplayName(), e.getMessage());
            }
        }

        return result;
    }

    private String normalizeUrl(String value) {
        String cleaned = clean(value);
        return cleaned == null ? null : removeTrailingSlash(cleaned);
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
