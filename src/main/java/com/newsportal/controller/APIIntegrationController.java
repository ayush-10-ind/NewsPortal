package com.newsportal.controller;

import com.newsportal.dto.NewsApiResponseDTO;
import com.newsportal.service.NewsApiService;
import com.newsportal.service.NewsArticleGenerationService;
import com.newsportal.service.NewsImportService;
import com.newsportal.service.WebClientAPIService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api-integration")
public class APIIntegrationController {

    private final WebClientAPIService webClientService;
    private final NewsApiService newsApiService;
    private final NewsImportService newsImportService;
    private final NewsArticleGenerationService articleGenerationService;


    @Autowired
    public APIIntegrationController(
            WebClientAPIService webClientService,
            NewsApiService newsApiService,
            NewsImportService newsImportService,
            NewsArticleGenerationService articleGenerationService) {

        this.webClientService = webClientService;
        this.newsApiService = newsApiService;
        this.newsImportService = newsImportService;
        this.articleGenerationService = articleGenerationService;
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
}