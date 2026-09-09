package com.newsportal.scheduler;

import com.newsportal.service.RssNewsImportService;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class NewsScheduler {

    private final RssNewsImportService rssNewsImportService;

    public NewsScheduler(
            RssNewsImportService rssNewsImportService) {
        this.rssNewsImportService = rssNewsImportService;
    }

    // ============================================================
    // AUTOMATIC RSS NEWS IMPORT
    // ============================================================
    //
    // Runs once after application startup and then every 30 minutes.
    //
    // Flow:
    //
    // RSS Sources
    //      ↓
    // MultiSourceNewsFetcherService
    //      ↓
    // Duplicate Check
    //      ↓
    // Ashna AI Analysis
    //      ↓
    // Image Resolution
    //      ↓
    // MySQL
    //
    // IMPORTANT:
    // This intentionally does NOT call the old NewsAPI importer.
    // This avoids the NewsAPI 429/quota problem.
    //
    // ============================================================

    @Scheduled(
            initialDelay = 60000,
            fixedDelay = 1800000
    )
    public void automaticallyImportRssNews() {

        System.out.println();
        System.out.println("================================================");
        System.out.println("AGNIPRESS AUTOMATIC RSS NEWS IMPORT STARTED");
        System.out.println("================================================");

        long startTime = System.currentTimeMillis();

        try {

            int imported =
                    rssNewsImportService.importAllRssSources();

            long duration =
                    System.currentTimeMillis() - startTime;

            System.out.println();
            System.out.println("================================================");
            System.out.println("AGNIPRESS AUTOMATIC RSS IMPORT COMPLETED");
            System.out.println("New articles imported: " + imported);
            System.out.println("Execution time: " + duration + " ms");
            System.out.println("================================================");

        } catch (Exception e) {

            long duration =
                    System.currentTimeMillis() - startTime;

            System.err.println();
            System.err.println("================================================");
            System.err.println("AGNIPRESS AUTOMATIC RSS IMPORT FAILED");
            System.err.println("Execution time: " + duration + " ms");
            System.err.println("Error: " + e.getMessage());
            System.err.println("================================================");

            e.printStackTrace();
        }
    }
}