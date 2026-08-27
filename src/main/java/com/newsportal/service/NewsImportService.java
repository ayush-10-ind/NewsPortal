package com.newsportal.service;

import com.newsportal.dto.NewsApiArticleDTO;
import com.newsportal.entity.News;
import com.newsportal.entity.NewsSourceType;
import com.newsportal.repository.NewsRepository;

import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class NewsImportService {

    private final NewsApiService newsApiService;

    private final NewsRepository newsRepository;

    private final NewsArticleGenerationService
            articleGenerationService;


    // =====================================================
    // CONSTRUCTOR
    // =====================================================

    public NewsImportService(
            NewsApiService newsApiService,
            NewsRepository newsRepository,
            NewsArticleGenerationService articleGenerationService) {

        this.newsApiService =
                newsApiService;

        this.newsRepository =
                newsRepository;

        this.articleGenerationService =
                articleGenerationService;
    }


    // =====================================================
    // IMPORT NEWS
    //
    // IMPORTANT:
    // Images are now stored as external URLs.
    // This avoids losing images when Railway redeploys.
    // =====================================================

    public int importNews() {

        System.out.println(
                "Fetching latest news from News API..."
        );


        List<NewsApiArticleDTO> articles =
                newsApiService.getAllTopHeadlines();


        if (articles == null ||
                articles.isEmpty()) {

            System.out.println(
                    "No articles received."
            );

            return 0;
        }


        int importedCount = 0;

        int repairedImages = 0;


        // =================================================
        // PROCESS ARTICLES
        // =================================================

        for (NewsApiArticleDTO article : articles) {

            try {

                // =============================================
                // VALIDATION
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
                // IMAGE URL
                // =============================================

                String externalImageUrl =
                        article.getUrlToImage();


                // =============================================
                // FIND EXISTING ARTICLE
                // =============================================

                Optional<News> existingArticle =
                        newsRepository.findBySourceUrl(
                                article.getUrl()
                        );


                // =============================================
                // REPAIR EXISTING ARTICLE IMAGE
                //
                // If imageUrl currently contains:
                //
                // /uploads/news/xxxx.jpg
                //
                // replace it with the original external
                // News API image URL.
                // =============================================

                if (existingArticle.isPresent()) {

                    News news =
                            existingArticle.get();


                    if (externalImageUrl != null &&
                            !externalImageUrl.isBlank()) {

                        String currentImage =
                                news.getImageUrl();


                        boolean needsRepair =
                                currentImage == null ||
                                currentImage.isBlank() ||
                                !currentImage.startsWith(
                                        "http://"
                                ) &&
                                !currentImage.startsWith(
                                        "https://"
                                );


                        if (needsRepair) {

                            news.setImageUrl(
                                    externalImageUrl
                            );

                            newsRepository.save(news);

                            repairedImages++;

                            System.out.println(
                                    "IMAGE REPAIRED: "
                                            + news.getTitle()
                            );

                            System.out.println(
                                    "External image: "
                                            + externalImageUrl
                            );
                        }
                    }


                    System.out.println(
                            "Skipping duplicate: "
                                    + article.getTitle()
                    );

                    continue;
                }


                // =============================================
                // NO IMAGE
                // =============================================

                if (externalImageUrl == null ||
                        externalImageUrl.isBlank()) {

                    System.out.println(
                            "Skipping article - no image: "
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
                // INITIAL CONTENT
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
                // EXTERNAL IMAGE URL
                //
                // DO NOT DOWNLOAD LOCALLY.
                // =============================================

                news.setImageUrl(
                        externalImageUrl
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
                            article.getSource().getName()
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
                // SAVE
                // =============================================

                News savedNews =
                        newsRepository.save(news);


                importedCount++;


                System.out.println(
                        "Imported immediately: "
                                + savedNews.getTitle()
                );

                System.out.println(
                        "External image: "
                                + savedNews.getImageUrl()
                );


                // =============================================
                // ASHNA GENERATION
                //
                // Run only after article is saved.
                // Failure must NOT stop the import.
                // =============================================

                
            } catch (Exception e) {

                System.out.println(
                        "Failed to import article."
                );

                System.out.println(
                        "Title: "
                                + (
                                article != null
                                        ? article.getTitle()
                                        : "null"
                        )
                );

                System.out.println(
                        "Error: "
                                + e.getMessage()
                );

                e.printStackTrace();
            }
        }


        // =================================================
        // SUMMARY
        // =================================================

        System.out.println();
        System.out.println(
                "========================================"
        );

        System.out.println(
                "NEWS IMPORT COMPLETED"
        );

        System.out.println(
                "New articles imported: "
                        + importedCount
        );

        System.out.println(
                "Existing images repaired: "
                        + repairedImages
        );

        System.out.println(
                "========================================"
        );


        return importedCount;
    }


    // =====================================================
    // DATE CONVERSION
    // =====================================================

    private LocalDate convertPublishedDate(
            String publishedAt) {

        try {

            if (publishedAt == null ||
                    publishedAt.isBlank()) {

                return LocalDate.now();
            }


            return OffsetDateTime
                    .parse(publishedAt)
                    .toLocalDate();

        } catch (Exception e) {

            System.out.println(
                    "Could not parse published date: "
                            + publishedAt
            );

            return LocalDate.now();
        }
    }
}