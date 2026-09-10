package com.newsportal.scheduler;

import com.newsportal.service.RssNewsImportService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class NewsScheduler {

    private static final Logger logger =
            LoggerFactory.getLogger(NewsScheduler.class);

    private final RssNewsImportService rssNewsImportService;

    public NewsScheduler(
            RssNewsImportService rssNewsImportService) {
        this.rssNewsImportService = rssNewsImportService;
    }

    // Runs once after application startup and then every 30 minutes.
    // RSS import is intentionally used instead of the old NewsAPI importer.
    @Scheduled(
            initialDelay = 60000,
            fixedDelay = 1800000
    )
    public void automaticallyImportRssNews() {

        long startTime = System.currentTimeMillis();

        logger.info("AgniPress automatic RSS import started");

        try {
            int imported =
                    rssNewsImportService.importAllRssSources();

            long duration =
                    System.currentTimeMillis() - startTime;

            logger.info(
                    "AgniPress automatic RSS import completed: imported={}, durationMs={}",
                    imported,
                    duration
            );

        } catch (Exception e) {

            long duration =
                    System.currentTimeMillis() - startTime;

            logger.error(
                    "AgniPress automatic RSS import failed: durationMs={}, errorType={}, message={}",
                    duration,
                    e.getClass().getSimpleName(),
                    e.getMessage(),
                    e
            );
        }
    }
}