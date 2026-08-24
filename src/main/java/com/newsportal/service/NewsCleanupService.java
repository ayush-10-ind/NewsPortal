package com.newsportal.service;

import com.newsportal.entity.News;
import com.newsportal.repository.NewsRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class NewsCleanupService {

    private final NewsRepository newsRepository;

    private final ImageStorageService imageStorageService;


    // =====================================================
    // CONSTRUCTOR
    // =====================================================

    public NewsCleanupService(
            NewsRepository newsRepository,
            ImageStorageService imageStorageService) {

        this.newsRepository =
                newsRepository;

        this.imageStorageService =
                imageStorageService;
    }


    // =====================================================
    // DELETE NEWS OLDER THAN 7 DAYS
    // =====================================================

    @Transactional
    public int deleteOldNews() {

        LocalDate cutoffDate =
                LocalDate.now().minusDays(7);


        System.out.println();
        System.out.println(
                "========================================"
        );
        System.out.println(
                "OLD NEWS CLEANUP STARTED"
        );
        System.out.println(
                "Cutoff date: "
                        + cutoffDate
        );
        System.out.println(
                "========================================"
        );


        // =================================================
        // FIND OLD ARTICLES
        // =================================================

        List<News> oldNews =
                newsRepository
                        .findByPublishedDateBefore(
                                cutoffDate
                        );


        if (oldNews == null ||
                oldNews.isEmpty()) {

            System.out.println(
                    "No articles older than 7 days."
            );

            System.out.println(
                    "OLD NEWS CLEANUP COMPLETED"
            );

            System.out.println(
                    "========================================"
            );

            return 0;
        }


        int deletedCount = 0;


        // =================================================
        // DELETE EACH ARTICLE
        // =================================================

        for (News news : oldNews) {

            try {

                System.out.println();
                System.out.println(
                        "Removing old article:"
                );

                System.out.println(
                        "ID: "
                                + news.getId()
                );

                System.out.println(
                        "Title: "
                                + news.getTitle()
                );

                System.out.println(
                        "Published: "
                                + news.getPublishedDate()
                );


                // =========================================
                // DELETE LOCAL IMAGE FIRST
                // =========================================

                String imageUrl =
                        news.getImageUrl();


                if (imageUrl != null &&
                        !imageUrl.isBlank()) {

                    imageStorageService
                            .deleteLocalImage(
                                    imageUrl
                            );
                }


                // =========================================
                // DELETE DATABASE ARTICLE
                // =========================================

                newsRepository.delete(
                        news
                );


                deletedCount++;


                System.out.println(
                        "Article deleted successfully."
                );


            } catch (Exception e) {

                System.out.println(
                        "Failed to delete article: "
                                + news.getTitle()
                );

                System.out.println(
                        "Error: "
                                + e.getMessage()
                );
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
                "OLD NEWS CLEANUP COMPLETED"
        );

        System.out.println(
                "Articles deleted: "
                        + deletedCount
        );

        System.out.println(
                "========================================"
        );


        return deletedCount;
    }
}