package com.newsportal.controller;

import com.newsportal.entity.Bookmark;
import com.newsportal.entity.News;
import com.newsportal.entity.ReadingHistory;
import com.newsportal.entity.User;

import com.newsportal.repository.BookmarkRepository;
import com.newsportal.repository.NewsRepository;
import com.newsportal.repository.ReadingHistoryRepository;
import com.newsportal.repository.UserRepository;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import org.springframework.security.core.Authentication;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;

import org.springframework.web.bind.annotation.GetMapping;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Controller
public class DashboardController {

    private final UserRepository userRepository;
    private final NewsRepository newsRepository;
    private final BookmarkRepository bookmarkRepository;
    private final ReadingHistoryRepository readingHistoryRepository;


    public DashboardController(
            UserRepository userRepository,
            NewsRepository newsRepository,
            BookmarkRepository bookmarkRepository,
            ReadingHistoryRepository readingHistoryRepository) {

        this.userRepository = userRepository;
        this.newsRepository = newsRepository;
        this.bookmarkRepository = bookmarkRepository;
        this.readingHistoryRepository = readingHistoryRepository;
    }


    // =====================================================
    // USER DASHBOARD
    // =====================================================

    @GetMapping("/dashboard")
    public String dashboard(
            Authentication authentication,
            Model model) {


        // =================================================
        // CURRENT USER
        // =================================================

        User user =
                userRepository
                        .findByEmail(
                                authentication.getName()
                        )
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Current user not found"
                                )
                        );


        // =================================================
        // TOTAL NEWS
        // =================================================

        long totalNews =
                newsRepository.count();


        // =================================================
        // BOOKMARKS
        // =================================================

        List<Bookmark> bookmarks =
                bookmarkRepository
                        .findByUserOrderByCreatedAtDesc(
                                user
                        );


        long savedNewsCount =
                bookmarks.size();


        List<Bookmark> recentBookmarks =
                bookmarks.stream()
                        .limit(5)
                        .toList();


        // =================================================
        // READING HISTORY
        // =================================================

        List<ReadingHistory> readingHistory =
                readingHistoryRepository
                        .findByUserOrderByLastReadAtDesc(
                                user
                        );


        long articlesReadCount =
                readingHistory.size();


        List<ReadingHistory> recentReadingHistory =
                readingHistory.stream()
                        .limit(5)
                        .toList();


        // =================================================
        // LATEST NEWS
        // =================================================

        List<News> latestNews =
                newsRepository
                        .findAll(
                                PageRequest.of(
                                        0,
                                        6,
                                        Sort.by(
                                                "publishedDate"
                                        ).descending()
                                )
                        )
                        .getContent();


        // =================================================
        // POPULAR NEWS
        // =================================================

        List<News> popularNews =
                newsRepository
                        .findAll(
                                PageRequest.of(
                                        0,
                                        5,
                                        Sort.by(
                                                "viewCount"
                                        ).descending()
                                )
                        )
                        .getContent();


        // =================================================
        // PERSONALIZED RECOMMENDATIONS
        // =================================================

        List<News> recommendedNews =
                buildRecommendations(
                        readingHistory,
                        bookmarks
                );


        // =================================================
        // MODEL
        // =================================================

        model.addAttribute(
                "user",
                user
        );

        model.addAttribute(
                "totalNews",
                totalNews
        );

        model.addAttribute(
                "savedNewsCount",
                savedNewsCount
        );

        model.addAttribute(
                "articlesReadCount",
                articlesReadCount
        );

        model.addAttribute(
                "recentBookmarks",
                recentBookmarks
        );

        model.addAttribute(
                "recentReadingHistory",
                recentReadingHistory
        );

        model.addAttribute(
                "latestNews",
                latestNews
        );

        model.addAttribute(
                "popularNews",
                popularNews
        );

        model.addAttribute(
                "recommendedNews",
                recommendedNews
        );


        return "dashboard";
    }


    // =====================================================
    // BUILD PERSONALIZED RECOMMENDATIONS
    // =====================================================

    private List<News> buildRecommendations(
            List<ReadingHistory> readingHistory,
            List<Bookmark> bookmarks) {


        // =================================================
        // ALL NEWS
        // =================================================

        List<News> allNews =
                newsRepository.findAll(
                        Sort.by(
                                "publishedDate"
                        ).descending()
                );


        if (allNews.isEmpty()) {

            return List.of();
        }


        // =================================================
        // FIND USER'S PREFERRED CATEGORIES
        // =================================================

        Map<String, Integer> categoryScores =
                new HashMap<>();


        // Reading history gets stronger weight

        for (ReadingHistory history : readingHistory) {

            News news =
                    history.getNews();


            if (news == null) {
                continue;
            }


            String category =
                    news.getCategory();


            if (category == null
                    || category.trim().isEmpty()) {

                continue;
            }


            String normalizedCategory =
                    category.trim().toLowerCase();


            categoryScores.merge(
                    normalizedCategory,
                    3,
                    Integer::sum
            );
        }


        // Bookmarks also indicate interest

        for (Bookmark bookmark : bookmarks) {

            News news =
                    bookmark.getNews();


            if (news == null) {
                continue;
            }


            String category =
                    news.getCategory();


            if (category == null
                    || category.trim().isEmpty()) {

                continue;
            }


            String normalizedCategory =
                    category.trim().toLowerCase();


            categoryScores.merge(
                    normalizedCategory,
                    2,
                    Integer::sum
            );
        }


        // =================================================
        // ARTICLES ALREADY READ
        // =================================================

        Set<Long> readNewsIds =
                new HashSet<>();


        for (ReadingHistory history : readingHistory) {

            if (history.getNews() != null
                    && history.getNews().getId() != null) {

                readNewsIds.add(
                        history.getNews().getId()
                );
            }
        }


        // =================================================
        // NEW USER FALLBACK
        // =================================================

        if (categoryScores.isEmpty()) {

            return allNews.stream()
                    .limit(6)
                    .toList();
        }


        // =================================================
        // SORT CATEGORIES BY USER INTEREST
        // =================================================

        List<String> preferredCategories =
                categoryScores.entrySet()
                        .stream()
                        .sorted(
                                Map.Entry
                                        .<String, Integer>
                                        comparingByValue()
                                        .reversed()
                        )
                        .map(
                                Map.Entry::getKey
                        )
                        .toList();


        // =================================================
        // BUILD RECOMMENDATIONS
        // =================================================

        List<News> recommendations =
                allNews.stream()
                        .filter(news -> {

                            if (news == null
                                    || news.getId() == null) {

                                return false;
                            }


                            // Don't recommend
                            // articles already read

                            if (readNewsIds.contains(
                                    news.getId())) {

                                return false;
                            }


                            String category =
                                    news.getCategory();


                            if (category == null
                                    || category.trim().isEmpty()) {

                                return false;
                            }


                            return preferredCategories
                                    .contains(
                                            category
                                                    .trim()
                                                    .toLowerCase()
                                    );
                        })
                        .limit(6)
                        .toList();


        // =================================================
        // FALLBACK IF NOT ENOUGH MATCHES
        // =================================================

        if (recommendations.size() < 6) {

            Set<Long> existingIds =
                    recommendations.stream()
                            .map(News::getId)
                            .collect(
                                    java.util.stream.Collectors
                                            .toSet()
                            );


            List<News> fallbackNews =
                    allNews.stream()
                            .filter(news ->
                                    news != null
                                            && news.getId() != null
                                            && !readNewsIds.contains(
                                                    news.getId()
                                            )
                                            && !existingIds.contains(
                                                    news.getId()
                                            )
                            )
                            .limit(
                                    6 - recommendations.size()
                            )
                            .toList();


            recommendations =
                    new java.util.ArrayList<>(
                            recommendations
                    );

            recommendations.addAll(
                    fallbackNews
            );
        }


        return recommendations;
    }
}