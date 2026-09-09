package com.newsportal.controller;

import com.newsportal.dto.NewsApiResponseDTO;
import com.newsportal.entity.News;
import com.newsportal.repository.NewsRepository;
import com.newsportal.service.ArticleImageService;
import com.newsportal.service.AshnaArticleAnalyzerService;
import com.newsportal.service.NewsApiService;
import com.newsportal.service.NewsArticleGenerationService;
import com.newsportal.service.NewsImageMigrationService;
import com.newsportal.service.NewsImportService;
import com.newsportal.service.RssNewsImportService;
import com.newsportal.service.WebClientAPIService;
import com.newsportal.source.MultiSourceNewsFetcherService;
import com.newsportal.source.NewsSection;
import com.newsportal.source.RssNewsFetcherService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api-integration")
public class APIIntegrationController {

    private final WebClientAPIService webClientService;

    private final NewsApiService newsApiService;

    private final NewsImportService newsImportService;

    private final NewsArticleGenerationService articleGenerationService;

    private final NewsImageMigrationService newsImageMigrationService;

    private final ArticleImageService articleImageService;

    private final AshnaArticleAnalyzerService
            ashnaArticleAnalyzerService;

    private final NewsRepository newsRepository;

    private final MultiSourceNewsFetcherService
            multiSourceNewsFetcherService;

    private final RssNewsImportService
            rssNewsImportService;


    // =====================================================
    // CONSTRUCTOR
    // =====================================================

    @Autowired
    public APIIntegrationController(
            WebClientAPIService webClientService,
            NewsApiService newsApiService,
            NewsImportService newsImportService,
            NewsArticleGenerationService articleGenerationService,
            NewsImageMigrationService newsImageMigrationService,
            ArticleImageService articleImageService,
            AshnaArticleAnalyzerService ashnaArticleAnalyzerService,
            NewsRepository newsRepository,
            MultiSourceNewsFetcherService multiSourceNewsFetcherService,
            RssNewsImportService rssNewsImportService) {

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

        this.articleImageService =
                articleImageService;

        this.ashnaArticleAnalyzerService =
                ashnaArticleAnalyzerService;

        this.newsRepository =
                newsRepository;

        this.multiSourceNewsFetcherService =
                multiSourceNewsFetcherService;

        this.rssNewsImportService =
                rssNewsImportService;
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
    // ASHNA ARTICLE ANALYZER TEST
    //
    // Tests one EXISTING database article.
    //
    // Example:
    //
    // /api-integration/ashna/analyze/123
    //
    // This DOES NOT modify the database.
    // =====================================================

    @GetMapping("/ashna/analyze/{id}")
    public AshnaArticleAnalyzerService.ArticleAnalysis
    analyzeArticle(
            @PathVariable Long id) {

        News news =
                newsRepository
                        .findById(id)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "News article not found with id: "
                                                + id
                                )
                        );


        String sourceName =
                news.getSourceName() != null
                        ? news.getSourceName()
                        : "Unknown";


        String author =
                news.getAuthor() != null
                        ? news.getAuthor()
                        : "Unknown";


        String publishedDate =
                news.getPublishedDate() != null
                        ? news.getPublishedDate().toString()
                        : "Unknown";


        String content =
                news.getContent() != null
                        ? news.getContent()
                        : "";


        return ashnaArticleAnalyzerService
                .analyzeArticle(
                        news.getTitle(),
                        sourceName,
                        news.getSourceUrl(),
                        author,
                        publishedDate,
                        content
                );
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
    // RSS TEST
    //
    // Example:
    //
    // /api-integration/rss/test/SCIENCE
    //
    // This DOES NOT save anything to the database.
    // =====================================================

    @GetMapping("/rss/test/{section}")
    public List<RssNewsFetcherService.RssArticle>
    testRss(
            @PathVariable String section) {

        NewsSection newsSection;

        try {

            newsSection =
                    NewsSection.valueOf(
                            section.toUpperCase()
                    );

        } catch (IllegalArgumentException e) {

            throw new IllegalArgumentException(
                    "Invalid section: "
                            + section
                            + ". Valid sections are: "
                            + Arrays.toString(
                                    NewsSection.values()
                            )
            );
        }


        return multiSourceNewsFetcherService
                .fetchRssForSection(
                        newsSection
                );
    }


    // =====================================================
    // RSS SOURCE STATUS
    // =====================================================

    @GetMapping("/rss/status")
    public String rssSourceStatus() {

        return multiSourceNewsFetcherService
                .getSourceStatus();
    }


    // =====================================================
    // RSS IMPORT
    //
    // Example:
    //
    // /api-integration/rss/import/SCIENCE
    //
    // This imports RSS articles into MySQL.
    //
    // Currently NASA is the only enabled RSS source
    // for SCIENCE.
    // =====================================================

    @GetMapping("/rss/import/{section}")
    public String importRss(
            @PathVariable String section) {

        NewsSection newsSection;

        try {

            newsSection =
                    NewsSection.valueOf(
                            section.toUpperCase()
                    );

        } catch (IllegalArgumentException e) {

            throw new IllegalArgumentException(
                    "Invalid section: "
                            + section
                            + ". Valid sections are: "
                            + Arrays.toString(
                                    NewsSection.values()
                            )
            );
        }


        int count =
                rssNewsImportService
                        .importSection(
                                newsSection
                        );


        return "RSS import completed for "
                + newsSection.getDisplayName()
                + ". Imported "
                + count
                + " new articles.";
    }


    // =====================================================
    // IMAGE RESOLVER TEST
    // =====================================================

    @GetMapping("/images/test")
    public String testArticleImage(
            @RequestParam String articleUrl,

            @RequestParam(
                    defaultValue = "General"
            )
            String category) {

        String imageUrl =
                articleImageService.resolveImage(
                        null,
                        articleUrl,
                        category
                );

        return imageUrl;
    }


    // =====================================================
    // MANUAL IMAGE MIGRATION BATCH
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