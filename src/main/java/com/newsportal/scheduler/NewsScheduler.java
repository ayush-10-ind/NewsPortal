package com.newsportal.scheduler;

import com.newsportal.service.NewsRetentionService;
import com.newsportal.service.RssNewsImportService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class NewsScheduler {

    private static final Logger logger = LoggerFactory.getLogger(NewsScheduler.class);

    private final RssNewsImportService rssNewsImportService;
    private final NewsRetentionService newsRetentionService;

    public NewsScheduler(
            RssNewsImportService rssNewsImportService,
            NewsRetentionService newsRetentionService) {
        this.rssNewsImportService = rssNewsImportService;
        this.newsRetentionService = newsRetentionService;
    }

    // Runs once after startup and then every 30 minutes.
    // Each section contributes up to 12 genuinely new RSS articles.
    @Scheduled(
            initialDelay = 60000,
            fixedDelay = 1800000
    )
    public void automaticallyImportRssNews() {
        long startTime = System.currentTimeMillis();
        logger.info("AgniPress automatic RSS import started");

        try {
            int imported = rssNewsImportService.importAllRssSources();
            long duration = System.currentTimeMillis() - startTime;

            logger.info(
                    "AgniPress automatic RSS import completed: imported={}, durationMs={}",
                    imported,
                    duration
            );
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;

            logger.error(
                    "AgniPress automatic RSS import failed: durationMs={}, errorType={}, message={}",
                    duration,
                    e.getClass().getSimpleName(),
                    e.getMessage(),
                    e
            );
        }
    }

    // Runs one minute after startup and then every seven days.
    // Deletes articles whose published date is more than seven days old.
    @Scheduled(
            initialDelay = 60000,
            fixedDelay = 604800000
    )
    public void deleteExpiredNews() {
        logger.info("AgniPress seven-day news retention started");

        try {
            long deleted = newsRetentionService.deleteExpiredNews();
            logger.info("AgniPress seven-day news retention completed: deleted={}", deleted);
        } catch (Exception e) {
            logger.error(
                    "AgniPress seven-day news retention failed: errorType={}, message={}",
                    e.getClass().getSimpleName(),
                    e.getMessage(),
                    e
            );
        }
    }
}
