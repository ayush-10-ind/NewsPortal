package com.newsportal.service;

import com.newsportal.entity.News;
import com.newsportal.entity.NewsSourceType;
import com.newsportal.repository.NewsRepository;
import com.newsportal.source.WebArticleContentExtractorService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

@Service
public class RssArticleRepairService {

    private static final Logger logger = LoggerFactory.getLogger(RssArticleRepairService.class);
    private static final String PLACEHOLDER = "Article content is being prepared.";

    // Historical repair is a safety net only. New RSS articles use the normal
    // import -> Ashna pipeline, so this worker must not fetch every RSS feed.
    private static final int MAX_REPAIR_BATCH = 3;
    private static final int MIN_USABLE_SOURCE_LENGTH = 80;

    private final NewsRepository newsRepository;
    private final NewsArticleGenerationService articleGenerationService;
    private final WebArticleContentExtractorService webArticleContentExtractorService;

    public RssArticleRepairService(
            NewsRepository newsRepository,
            NewsArticleGenerationService articleGenerationService,
            WebArticleContentExtractorService webArticleContentExtractorService) {
        this.newsRepository = newsRepository;
        this.articleGenerationService = articleGenerationService;
        this.webArticleContentExtractorService = webArticleContentExtractorService;
    }

    // Run only occasionally. Historical repair uses publisher-page requests,
    // so it should stay well away from the normal RSS import path.
    @Scheduled(initialDelay = 600000, fixedDelay = 3600000)
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

        int repaired = 0;

        for (News news : allNews) {
            if (repaired >= MAX_REPAIR_BATCH) break;
            if (!isRssArticle(news) || !needsGeneration(news)) continue;

            String sourceContent = clean(news.getContent());

            if (!isUsableSourceContent(sourceContent)) {
                logger.info(
                        "RSS repair source incomplete. Trying publisher page fallback: id={}, title={}, url={}",
                        news.getId(),
                        news.getTitle(),
                        news.getSourceUrl()
                );

                sourceContent = clean(
                        webArticleContentExtractorService.fetchArticleText(news.getSourceUrl())
                );

                if (isUsableSourceContent(sourceContent)) {
                    logger.info(
                            "Publisher page fallback succeeded: id={}, extractedChars={}",
                            news.getId(),
                            sourceContent.length()
                    );
                }
            }

            if (!isUsableSourceContent(sourceContent)) {
                logger.debug(
                        "RSS repair skipped article id={} because no usable source content was found.",
                        news.getId()
                );
                continue;
            }

            try {
                news.setContent(sourceContent);
                News saved = newsRepository.save(news);

                logger.info(
                        "RSS article source ready. Queueing Ashna generation: id={}, title={}",
                        saved.getId(),
                        saved.getTitle()
                );

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

    private boolean isUsableSourceContent(String content) {
        return content != null
                && content.length() >= MIN_USABLE_SOURCE_LENGTH
                && !PLACEHOLDER.equalsIgnoreCase(content);
    }

    private int countParagraphBreaks(String content) {
        int count = 0;
        for (int i = 0; i < content.length() - 1; i++) {
            if (content.charAt(i) == '\n' && content.charAt(i + 1) == '\n') count++;
        }
        return count;
    }

    private String clean(String value) {
        if (value == null) return null;
        String cleaned = value.trim();
        return cleaned.isBlank() ? null : cleaned;
    }
}
