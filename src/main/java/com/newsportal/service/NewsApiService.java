package com.newsportal.service;

import com.newsportal.dto.NewsApiArticleDTO;
import com.newsportal.dto.NewsApiResponseDTO;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class NewsApiService {

    private final WebClient webClient;

    @Value("${newsapi.api-key}")
    private String apiKey;


    // =====================================================
    // CONSTRUCTOR
    // =====================================================

    public NewsApiService(
            @Value("${newsapi.base-url}") String baseUrl) {

        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .build();
    }


    // =====================================================
    // OLD TEST METHOD
    // =====================================================

    public NewsApiResponseDTO getTopHeadlines() {

        return webClient
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/top-headlines")
                        .queryParam("country", "us")
                        .queryParam("category", "technology")
                        .queryParam("pageSize", 10)
                        .build())
                .header("X-Api-Key", apiKey)
                .retrieve()
                .bodyToMono(NewsApiResponseDTO.class)
                .block();
    }


    // =====================================================
    // FETCH RECENT ARTICLES FOR ONE TOPIC
    // =====================================================

    private NewsApiResponseDTO searchNews(String query) {

        /*
         * Search the last 3 days.
         *
         * This gives us a reasonable window for fresh news
         * while allowing some articles to appear late.
         */

        String fromDate =
                OffsetDateTime.now(ZoneOffset.UTC)
                        .minusDays(3)
                        .toString();

        String toDate =
                OffsetDateTime.now(ZoneOffset.UTC)
                        .toString();


        return webClient
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/everything")

                        .queryParam(
                                "q",
                                query
                        )

                        .queryParam(
                                "from",
                                fromDate
                        )

                        .queryParam(
                                "to",
                                toDate
                        )

                        .queryParam(
                                "language",
                                "en"
                        )

                        .queryParam(
                                "sortBy",
                                "publishedAt"
                        )

                        .queryParam(
                                "pageSize",
                                8
                        )

                        .build())

                .header(
                        "X-Api-Key",
                        apiKey
                )

                .retrieve()

                .bodyToMono(
                        NewsApiResponseDTO.class
                )

                .block();
    }


    // =====================================================
    // FETCH FRESH NEWS FROM MULTIPLE CATEGORIES
    // =====================================================

    public List<NewsApiArticleDTO> getAllTopHeadlines() {

        /*
         * Each NewsAPI query is associated with a category.
         *
         * Instead of guessing the category later using
         * keywords, we assign it here.
         */

        Map<String, String> topics =
                new LinkedHashMap<>();


        // =================================================
        // TECHNOLOGY
        // =================================================

        topics.put(
                "Technology",
                "technology OR artificial intelligence OR software"
        );


        // =================================================
        // BUSINESS
        // =================================================

        topics.put(
                "Business",
                "business OR economy OR stocks OR finance"
        );


        // =================================================
        // SPORTS
        // =================================================

        topics.put(
                "Sports",
                "sports OR cricket OR football OR soccer"
        );


        // =================================================
        // ENTERTAINMENT
        // =================================================

        topics.put(
                "Entertainment",
                "entertainment OR movies OR music OR celebrity"
        );


        // =================================================
        // SCIENCE
        // =================================================

        topics.put(
                "Science",
                "science OR space OR NASA OR research"
        );


        // =================================================
        // WORLD
        // =================================================

        topics.put(
                "World",
                "world news OR international news"
        );


        // =================================================
        // INDIA
        // =================================================

        topics.put(
                "India",
                "India OR Indian news"
        );


        /*
         * URL is our unique identifier.
         *
         * LinkedHashMap:
         * - removes duplicate URLs
         * - preserves discovery order
         */

        Map<String, NewsApiArticleDTO> uniqueArticles =
                new LinkedHashMap<>();


        // =================================================
        // FETCH EACH CATEGORY
        // =================================================

        for (Map.Entry<String, String> topic :
                topics.entrySet()) {

            String category = topic.getKey();

            String query = topic.getValue();


            try {

                System.out.println(
                        "Fetching "
                                + category
                                + " news..."
                );


                NewsApiResponseDTO response =
                        searchNews(query);


                if (response == null ||
                        response.getArticles() == null) {

                    System.out.println(
                            "No articles received for "
                                    + category
                    );

                    continue;
                }


                // =========================================
                // PROCESS ARTICLES
                // =========================================

                for (NewsApiArticleDTO article :
                        response.getArticles()) {

                    if (article == null) {
                        continue;
                    }


                    if (article.getUrl() == null ||
                            article.getUrl().isBlank()) {

                        continue;
                    }


                    /*
                     * Assign category directly from the
                     * query that produced this article.
                     */

                    article.setCategory(category);


                    /*
                     * Add only if this URL hasn't already
                     * been discovered during this import.
                     */

                    uniqueArticles.putIfAbsent(
                            article.getUrl(),
                            article
                    );
                }


            } catch (Exception e) {

                System.out.println(
                        "Failed to fetch "
                                + category
                                + " news."
                );

                System.out.println(
                        "Error: "
                                + e.getMessage()
                );
            }
        }


        // =================================================
        // CONVERT MAP TO LIST
        // =================================================

        List<NewsApiArticleDTO> articles =
                new ArrayList<>(
                        uniqueArticles.values()
                );


        System.out.println(
                "Total unique articles fetched: "
                        + articles.size()
        );


        return articles;
    }
}