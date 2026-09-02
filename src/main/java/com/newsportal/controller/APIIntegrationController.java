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

        this.webClientService = webClientService;
        this.newsApiService = newsApiService;
        this.newsImportService = newsImportService;
        this.articleGenerationService = articleGenerationService;
        this.newsImageMigrationService = newsImageMigrationService;
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

        return webClientService.getAshnaModels();
    }

    // =====================================================
    // NEWS API TEST
    // =====================================================

    @GetMapping("/newsapi/test")
    public NewsApiResponseDTO testNewsApi() {

        return newsApiService.getTopHeadlines();
    }

    // =====================================================
    // FAST NEWS IMPORT
    // =====================================================

    @GetMapping("/newsapi/import")
    public String importNews() {

        int count =
                newsImportService.importNews();

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
    // BATCH IMAGE MIGRATION
    // =====================================================
    //
    // Migrates only a small number of old local image
    // records per HTTP request.
    //
    // Example:
    //
    // /api-integration/images/migrate?batchSize=10
    //
    // Maximum batch size is enforced inside the service.
    //
    // This endpoint is TEMPORARY.
    //
    // Remove it after the production migration is
    // completely finished and verified.
    //
    // =====================================================

    @GetMapping("/images/migrate")
    public String migrateOldImages(
            @RequestParam(
                    name = "batchSize",
                    defaultValue = "10"
            )
            int batchSize) {

        int migratedCount =
                newsImageMigrationService
                        .migrateOldImages(batchSize);

        return "Image migration batch completed. "
                + "Migrated "
                + migratedCount
                + " articles. "
                + "Requested batch size: "
                + batchSize
                + ".";
    }
}