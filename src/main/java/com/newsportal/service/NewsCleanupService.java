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


    // =====================================================
    // CONSTRUCTOR
    // =====================================================

    public NewsCleanupService(
            NewsRepository newsRepository) {

        this.newsRepository =
                newsRepository;
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
        // DELETE OLD ARTICLES
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


                // =================================================
                // IMPORTANT
                // =================================================
                //
                // Images are no longer stored locally.
                //
                // We only store external image URLs in the
                // database. Therefore there is NO image file
                // to delete here.
                //
                // =================================================


                // =========================================
                // DELETE DATABASE ARTICLE
                // =========================================

                newsRepository.delete(news);

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
                "OLD NEWS CLEANUP COMPLETED"
        );

        System.out.println(
                "Articles deleted: "
                        + deletedCount
        );

        System.out.println(
                "Images deleted: 0"
        );

        System.out.println(
                "Reason: AgniPress does not store news images locally."
        );

        System.out.println(
                "========================================"
        );


        return deletedCount;
    }
}