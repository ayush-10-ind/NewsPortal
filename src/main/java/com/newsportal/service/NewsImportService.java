package com.newsportal.service;

import com.newsportal.dto.NewsApiArticleDTO;
import com.newsportal.entity.News;
import com.newsportal.entity.NewsSourceType;
import com.newsportal.repository.NewsRepository;

import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

@Service
public class NewsImportService {

    private final NewsApiService newsApiService;

    private final NewsRepository newsRepository;

    private final NewsArticleGenerationService
            articleGenerationService;

    private final ImageStorageService
            imageStorageService;

    private final NotificationService
            notificationService;


    // =====================================================
    // CONSTRUCTOR
    // =====================================================

    public NewsImportService(
            NewsApiService newsApiService,
            NewsRepository newsRepository,
            NewsArticleGenerationService articleGenerationService,
            ImageStorageService imageStorageService,
            NotificationService notificationService) {

        this.newsApiService =
                newsApiService;

        this.newsRepository =
                newsRepository;

        this.articleGenerationService =
                articleGenerationService;

        this.imageStorageService =
                imageStorageService;

        this.notificationService =
                notificationService;
    }


    // =====================================================
    // FAST NEWS IMPORT
    // =====================================================

    public int importNews() {

        System.out.println(
                "Fetching latest news from News API..."
        );


        List<NewsApiArticleDTO> articles =
                newsApiService.getAllTopHeadlines();


        // =================================================
        // NO ARTICLES
        // =================================================

        if (articles == null ||
                articles.isEmpty()) {

            System.out.println(
                    "No articles received."
            );

            return 0;
        }


        int importedCount = 0;


        // =================================================
        // IMPORT EACH ARTICLE
        // =================================================

        for (NewsApiArticleDTO article : articles) {

            try {

                // =============================================
                // BASIC VALIDATION
                // =============================================

                if (article == null ||
                        article.getTitle() == null ||
                        article.getTitle().isBlank() ||
                        article.getUrl() == null ||
                        article.getUrl().isBlank()) {

                    System.out.println(
                            "Skipping invalid article."
                    );

                    continue;
                }


                // =============================================
                // DUPLICATE CHECK
                // =============================================

                if (newsRepository
                        .findBySourceUrl(
                                article.getUrl()
                        )
                        .isPresent()) {

                    System.out.println(
                            "Skipping duplicate: "
                                    + article.getTitle()
                    );

                    continue;
                }


                // =============================================
                // IMAGE URL
                // =============================================

                String externalImageUrl =
                        article.getUrlToImage();


                if (externalImageUrl == null ||
                        externalImageUrl.isBlank()) {

                    System.out.println(
                            "Skipping article - no image: "
                                    + article.getTitle()
                    );

                    continue;
                }


                // =============================================
                // DOWNLOAD IMAGE LOCALLY
                // =============================================

                String localImageUrl =
                        imageStorageService
                                .downloadAndSaveImage(
                                        externalImageUrl
                                );


                if (localImageUrl == null ||
                        localImageUrl.isBlank()) {

                    System.out.println(
                            "Skipping article - "
                                    + "image could not be downloaded: "
                                    + article.getTitle()
                    );

                    continue;
                }


                // =============================================
                // CREATE NEWS ENTITY
                // =============================================

                News news = new News();


                // =============================================
                // TITLE
                // =============================================

                news.setTitle(
                        article.getTitle()
                );


                // =============================================
                // AUTHOR
                // =============================================

                news.setAuthor(
                        article.getAuthor()
                );


                // =============================================
                // CATEGORY
                // =============================================

                String category =
                        article.getCategory();


                if (category == null ||
                        category.isBlank()) {

                    category = "General";
                }


                news.setCategory(
                        category
                );


                // =============================================
                // TEMPORARY CONTENT
                // =============================================

                String initialContent =
                        article.getDescription();


                if (initialContent == null ||
                        initialContent.isBlank()) {

                    initialContent =
                            article.getContent();
                }


                if (initialContent == null ||
                        initialContent.isBlank()) {

                    initialContent =
                            "Article content is being prepared.";
                }


                news.setContent(
                        initialContent
                );


                // =============================================
                // LOCAL IMAGE
                // =============================================

                news.setImageUrl(
                        localImageUrl
                );


                // =============================================
                // SOURCE URL
                // =============================================

                news.setSourceUrl(
                        article.getUrl()
                );


                // =============================================
                // SOURCE NAME
                // =============================================

                if (article.getSource() != null) {

                    news.setSourceName(
                            article
                                    .getSource()
                                    .getName()
                    );
                }


                // =============================================
                // SOURCE TYPE
                // =============================================

                news.setSourceType(
                        NewsSourceType.EXTERNAL_API
                );


                // =============================================
                // PUBLISHED DATE
                // =============================================

                news.setPublishedDate(
                        convertPublishedDate(
                                article.getPublishedAt()
                        )
                );


                // =============================================
                // SAVE NEWS
                // =============================================

                News savedNews =
                        newsRepository.save(news);


                importedCount++;


                System.out.println(
                        "Imported immediately: "
                                + savedNews.getTitle()
                );


                System.out.println(
                        "Local image: "
                                + localImageUrl
                );


                // =============================================
                // ASHNA BACKGROUND GENERATION
                // =============================================

                articleGenerationService
                        .generateArticleAsync(
                                savedNews.getId()
                        );


            } catch (Exception e) {

                System.out.println(
                        "Failed to import article: "
                                + (
                                article != null
                                        ? article.getTitle()
                                        : "Unknown article"
                        )
                );


                System.out.println(
                        "Error: "
                                + e.getMessage()
                );
            }
        }


        // =====================================================
        // SMART NOTIFICATION
        // =====================================================

        if (importedCount > 0) {

            System.out.println(
                    "Creating notification for "
                            + importedCount
                            + " newly imported articles..."
            );


            notificationService
                    .notifyUsersAboutImportedNews(
                            importedCount
                    );
        }


        // =====================================================
        // IMPORT SUMMARY
        // =====================================================

        System.out.println(
                "Fast import completed."
        );


        System.out.println(
                "Articles imported: "
                        + importedCount
        );


        return importedCount;
    }


    // =====================================================
    // DATE CONVERSION
    // =====================================================

    private LocalDate convertPublishedDate(
            String publishedAt) {

        if (publishedAt == null ||
                publishedAt.isBlank()) {

            return LocalDate.now();
        }


        try {

            return OffsetDateTime
                    .parse(publishedAt)
                    .toLocalDate();

        } catch (Exception e) {

            return LocalDate.now();
        }
    }
}