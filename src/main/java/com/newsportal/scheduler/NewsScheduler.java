package com.newsportal.scheduler;

import com.newsportal.service.NewsCleanupService;
import com.newsportal.service.NewsImportService;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class NewsScheduler {

    private final NewsImportService newsImportService;

    private final NewsCleanupService newsCleanupService;


    // =====================================================
    // CONSTRUCTOR
    // =====================================================

    public NewsScheduler(
            NewsImportService newsImportService,
            NewsCleanupService newsCleanupService) {

        this.newsImportService =
                newsImportService;

        this.newsCleanupService =
                newsCleanupService;
    }


    // =====================================================
    // AUTOMATIC DAILY NEWS UPDATE
    // =====================================================

    @Scheduled(
            initialDelay = 60000,
            fixedRate = 86400000
    )
    public void automaticallyUpdateNews() {

        System.out.println();
        System.out.println(
                "========================================"
        );

        System.out.println(
                "AUTOMATIC NEWS UPDATE STARTED"
        );

        System.out.println(
                "========================================"
        );


        // =================================================
        // STEP 1 — IMPORT NEW NEWS
        // =================================================

        try {

            System.out.println(
                    "Fetching latest news..."
            );


            int imported =
                    newsImportService.importNews();


            System.out.println(
                    "New articles imported: "
                            + imported
            );


        } catch (Exception e) {

            System.out.println(
                    "Automatic news import failed."
            );

            System.out.println(
                    "Error: "
                            + e.getMessage()
            );

            e.printStackTrace();
        }


        // =================================================
        // STEP 2 — DELETE NEWS OLDER THAN 7 DAYS
        // =================================================

        try {

            System.out.println(
                    "Starting 7-day cleanup..."
            );


            int deleted =
                    newsCleanupService.deleteOldNews();


            System.out.println(
                    "Old articles deleted: "
                            + deleted
            );


        } catch (Exception e) {

            System.out.println(
                    "Automatic news cleanup failed."
            );

            System.out.println(
                    "Error: "
                            + e.getMessage()
            );

            e.printStackTrace();
        }


        // =================================================
        // COMPLETE
        // =================================================

        System.out.println();
        System.out.println(
                "========================================"
        );

        System.out.println(
                "AUTOMATIC NEWS UPDATE COMPLETED"
        );

        System.out.println(
                "Next update in approximately 24 hours."
        );

        System.out.println(
                "========================================"
        );
    }
}