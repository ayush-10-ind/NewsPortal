package com.newsportal.service;

import com.newsportal.entity.News;
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

    private static final Logger logger =
            LoggerFactory.getLogger(RssArticleRepairService.class);

    private static final String PLACEHOLDER =
            "Article content is being prepared.";

    private static final int MAX_REPAIR_BATCH = 10;

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

    /**
     * Safety net for RSS articles.
     *
     * The current RSS importer saves the source/analysis content but
     * does not always reach the final Ashna full-article generation
     * stage. This job repairs incomplete RSS rows and queues Ashna
     * generation without changing the existing import pipeline.
     */
    @Scheduled(initialDelay = 30000, fixedDelay = 600000)
    public void repairScheduled() {

        try {
            repairArticles();
        } catch (Exception e) {
            logger.error(
                    "RSS article repair job failed: {}",
                    e.getMessage(),
                    e
            );
        }
    }

    public int repairArticles() {

        List<News> allNews = newsRepository.findAll();

        if (allNews == null || allNews.isEmpty()) {
            return 0;
        }

        int candidates = 0;

        for (News news : allNews) {

            if (candidates >= MAX_REPAIR_BATCH) {
                break;
            }

            if (!isRssArticle(news)) {
                continue;
            }

            if (!needsGeneration(news)) {
                continue;
            }

            candidates++;
        }

        if (candidates == 0) {
            return 0;
        }

        Map<String, RssNewsFetcherService.RssArticle> feedArticles =
                fetchCurrentRssArticles();

        if (feedArticles.isEmpty()) {
            logger.warn("RSS repair found candidates but no current RSS feed items.");
            return 0;
        }

        int repaired = 0;

        for (News news : allNews) {

            if (repaired >= MAX_REPAIR_BATCH) {
                break;
            }

            if (!isRssArticle(news) || !needsGeneration(news)) {
                continue;
            }

            String sourceUrl = normalizeUrl(news.getSourceUrl());

            if (sourceUrl == null) {
                continue;
            }

            RssNewsFetcherService.RssArticle rssArticle =
                    feedArticles.get(sourceUrl);

            if (rssArticle == null) {
                rssArticle = feedArticles.get(removeTrailingSlash(sourceUrl));
            }

            if (rssArticle == null) {
                logger.warn(
                        "RSS repair could not match article id={} to a current feed item.",
                        news.getId()
                );
                continue;
            }

            String sourceContent = clean(rssArticle.getDescription());

            if (sourceContent == null || sourceContent.isBlank()) {
                logger.warn(
                        "RSS repair skipped article id={} because the feed has no description/content.",
                        news.getId()
                );
                continue;
            }

            try {

                news.setContent(sourceContent);

                String feedAuthor = clean(rssArticle.getAuthor());
                if (feedAuthor != null &&
                        (news.getAuthor() == null ||
                                news.getAuthor().isBlank() ||
                                "Unknown".equalsIgnoreCase(news.getAuthor().trim()))) {
                    news.setAuthor(feedAuthor);
                }

                News saved = newsRepository.saveAndFlush(news);

                articleGenerationService.generateArticleAsync(saved.getId());

                repaired++;

                logger.info(
                        "RSS article repaired and Ashna generation queued: id={}, title={}",
                        saved.getId(),
                        saved.getTitle()
                );

            } catch (Exception e) {
                logger.error(
                        "RSS article repair failed: id={}, error={}",
                        news.getId(),
                        e.getMessage(),
                        e
                );
            }
        }

        logger.info(
                "RSS article repair completed: repaired={}",
                repaired
        );

        return repaired;
    }

    private boolean isRssArticle(News news) {

        if (news == null) {
            return false;
        }

        String sourceName = clean(news.getSourceName());
        String sourceUrl = clean(news.getSourceUrl());

        return sourceUrl != null &&
                sourceName != null &&
                sourceName.toLowerCase().contains("rss");
    }

    private boolean needsGeneration(News news) {

        String content = clean(news.getContent());

        if (content == null || content.isBlank()) {
            return true;
        }

        if (PLACEHOLDER.equalsIgnoreCase(content)) {
            return true;
        }

        /*
         * NewsArticleGenerationService produces a multi-paragraph
         * article. Short RSS summaries are treated as source/analysis
         * content and are sent through the final generation stage.
         */
        if (content.length() < 1200) {
            return true;
        }

        int paragraphBreaks = countParagraphBreaks(content);

        return paragraphBreaks < 2;
    }

    private int countParagraphBreaks(String content) {

        int count = 0;

        for (int i = 0; i < content.length() - 1; i++) {
            if (content.charAt(i) == '\n' &&
                    content.charAt(i + 1) == '\n') {
                count++;
            }
        }

        return count;
    }

    private Map<String, RssNewsFetcherService.RssArticle>
    fetchCurrentRssArticles() {

        Map<String, RssNewsFetcherService.RssArticle> result =
                new HashMap<>();

        for (NewsSection section : NewsSection.values()) {

            try {

                List<RssNewsFetcherService.RssArticle> articles =
                        multiSourceNewsFetcherService.fetchRssForSection(section);

                if (articles == null) {
                    continue;
                }

                for (RssNewsFetcherService.RssArticle article : articles) {

                    if (article == null) {
                        continue;
                    }

                    String url = normalizeUrl(article.getUrl());

                    if (url == null) {
                        continue;
                    }

                    result.put(url, article);
                    result.put(removeTrailingSlash(url), article);
                }

            } catch (Exception e) {

                logger.warn(
                        "RSS repair feed fetch failed for section={}: {}",
                        section.getDisplayName(),
                        e.getMessage()
                );
            }
        }

        return result;
    }

    private String normalizeUrl(String value) {

        String cleaned = clean(value);

        if (cleaned == null) {
            return null;
        }

        return removeTrailingSlash(cleaned);
    }

    private String removeTrailingSlash(String value) {

        if (value == null) {
            return null;
        }

        String result = value.trim();

        while (result.length() > 1 && result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }

        return result;
    }

    private String clean(String value) {

        if (value == null) {
            return null;
        }

        String cleaned = value.trim();

        return cleaned.isBlank() ? null : cleaned;
    }
}
