package com.newsportal.scheduler;

import com.newsportal.service.NewsCleanupService;
import com.newsportal.service.NewsImportService;

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
    //
    // TEMPORARILY DISABLED
    //
    // The automatic scheduler was repeatedly calling
    // NewsAPI on Railway and receiving HTTP 429
    // "Too Many Requests" responses.
    //
    // We are keeping this method so the import logic
    // is not deleted. Automatic execution can be
    // re-enabled after the NewsAPI rate-limit handling
    // is fixed.
    //
    // =====================================================

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
                "========================================"
        );
    }
}