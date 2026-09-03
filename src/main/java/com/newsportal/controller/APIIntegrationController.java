package com.newsportal.controller;

import com.newsportal.dto.NewsApiResponseDTO;
import com.newsportal.service.NewsApiService;
import com.newsportal.service.NewsArticleGenerationService;
import com.newsportal.service.NewsImageMigrationService;
import com.newsportal.service.NewsImportService;
import com.newsportal.service.WebClientAPIService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api-integration")
public class APIIntegrationController {

    private final WebClientAPIService webClientService;

    private final NewsApiService newsApiService;

    private final NewsImportService newsImportService;

    private final NewsArticleGenerationService articleGenerationService;

    private final NewsImageMigrationService newsImageMigrationService;


    @Autowired
    public APIIntegrationController(
            WebClientAPIService webClientService,
            NewsApiService newsApiService,
            NewsImportService newsImportService,
            NewsArticleGenerationService articleGenerationService,
            NewsImageMigrationService newsImageMigrationService) {

        this.webClientService =
                webClientService;

        this.newsApiService =
                newsApiService;

        this.newsImportService =
                newsImportService;

        this.articleGenerationService =
                articleGenerationService;

        this.newsImageMigrationService =
                newsImageMigrationService;
    }


    // =====================================================
    // WEBCLIENT TEST
    // =====================================================

    @GetMapping("/test-webclient")
    public String testWebClient() {

        return webClientService
                .getDataFromAPI("/posts/1");
    }


    // =====================================================
    // ASHNA TEST
    // =====================================================

    @GetMapping("/ashna/test")
    public String testAshna() {

        return webClientService.askAshna(
                "Tell me one interesting technology news story in a short paragraph."
        );
    }


    // =====================================================
    // ASHNA MODELS
    // =====================================================

    @GetMapping("/ashna/models")
    public String getAshnaModels() {

        return webClientService
                .getAshnaModels();
    }


    // =====================================================
    // NEWS API TEST
    // =====================================================

    @GetMapping("/newsapi/test")
    public NewsApiResponseDTO testNewsApi() {

        return newsApiService
                .getTopHeadlines();
    }


    // =====================================================
    // FAST NEWS IMPORT
    // =====================================================

    @GetMapping("/newsapi/import")
    public String importNews() {

        int count =
                newsImportService
                        .importNews();

        return "Imported "
                + count
                + " new articles.";
    }


    // =====================================================
    // MANUAL ASHNA ARTICLE GENERATION
    // =====================================================

    @GetMapping("/newsapi/generate/{id}")
    public String generateArticle(
            @PathVariable Long id) {

        return articleGenerationService
                .generateArticle(id);
    }


    // =====================================================
    // MANUAL IMAGE MIGRATION BATCH
    //
    // Example:
    //
    // /api-integration/images/migrate?batchSize=5
    //
    // The monitor automatically continues afterward.
    // =====================================================

    @GetMapping("/images/migrate")
    public String migrateOldImages(
            @RequestParam(
                    name = "batchSize",
                    defaultValue = "5"
            )
            int batchSize) {

        return newsImageMigrationService
                .startMigration(batchSize);
    }


    // =====================================================
    // FULL IMAGE MIGRATION
    //
    // Example:
    //
    // /api-integration/images/migrate/all
    //
    // The migration monitor processes small batches
    // automatically until remaining = 0.
    // =====================================================

    @GetMapping("/images/migrate/all")
    public String migrateAllOldImages() {

        return newsImageMigrationService
                .startFullMigration();
    }


    // =====================================================
    // IMAGE MIGRATION STATUS
    // =====================================================

    @GetMapping("/images/migrate/status")
    public String migrationStatus() {

        return newsImageMigrationService
                .getStatus();
    }
}