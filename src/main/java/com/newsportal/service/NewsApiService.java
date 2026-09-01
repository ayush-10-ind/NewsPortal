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
import java.util.Locale;
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

    private NewsApiResponseDTO searchNews(
            String query) {

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
                        .queryParam("q", query)
                        .queryParam("from", fromDate)
                        .queryParam("to", toDate)
                        .queryParam("language", "en")
                        .queryParam("sortBy", "publishedAt")
                        .queryParam("pageSize", 8)
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

        Map<String, String> topics =
                new LinkedHashMap<>();


        // =================================================
        // TECHNOLOGY
        // =================================================

        topics.put(
                "Technology",
                "\"artificial intelligence\" OR " +
                "cybersecurity OR " +
                "software OR " +
                "smartphone OR " +
                "semiconductor OR " +
                "\"cloud computing\" OR " +
                "robotics OR " +
                "Apple OR " +
                "Google OR " +
                "Microsoft OR " +
                "Nvidia"
        );


        // =================================================
        // BUSINESS
        // =================================================

        topics.put(
                "Business",
                "economy OR " +
                "\"stock market\" OR " +
                "stocks OR " +
                "earnings OR " +
                "banking OR " +
                "investment OR " +
                "markets OR " +
                "companies OR " +
                "corporate"
        );


        // =================================================
        // SPORTS
        // =================================================

        topics.put(
                "Sports",
                "cricket OR " +
                "football OR " +
                "soccer OR " +
                "tennis OR " +
                "basketball OR " +
                "\"Formula 1\" OR " +
                "Olympics OR " +
                "athlete OR " +
                "championship"
        );


        // =================================================
        // ENTERTAINMENT
        // =================================================

        topics.put(
                "Entertainment",
                "movies OR " +
                "film OR " +
                "television OR " +
                "music OR " +
                "streaming OR " +
                "actor OR " +
                "actress OR " +
                "celebrity OR " +
                "Hollywood"
        );


        // =================================================
        // SCIENCE
        // =================================================

        topics.put(
                "Science",
                "scientific discovery OR " +
                "space OR " +
                "NASA OR " +
                "astronomy OR " +
                "physics OR " +
                "biology OR " +
                "\"clinical research\" OR " +
                "\"scientific research\""
        );


        // =================================================
        // WORLD
        // =================================================

        topics.put(
                "World",
                "\"world news\" OR " +
                "\"international news\" OR " +
                "geopolitics OR " +
                "diplomacy OR " +
                "\"international relations\" OR " +
                "conflict OR " +
                "war OR " +
                "NATO OR " +
                "United Nations"
        );


        // =================================================
        // INDIA
        // =================================================

        topics.put(
                "India",
                "\"India\" OR " +
                "\"Indian government\" OR " +
                "\"Indian economy\" OR " +
                "\"Indian politics\" OR " +
                "\"India technology\" OR " +
                "\"India business\" OR " +
                "\"India sports\""
        );


        // =================================================
        // POLITICS
        // =================================================

        topics.put(
                "Politics",
                "politics OR " +
                "parliament OR " +
                "government OR " +
                "election OR " +
                "president OR " +
                "\"prime minister\" OR " +
                "minister OR " +
                "legislation OR " +
                "\"political policy\" OR " +
                "senate OR " +
                "congress"
        );


        /*
         * URL is the unique identifier.
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

            String discoveredCategory =
                    topic.getKey();

            String query =
                    topic.getValue();


            try {

                System.out.println();
                System.out.println(
                        "Fetching "
                                + discoveredCategory
                                + " news..."
                );


                NewsApiResponseDTO response =
                        searchNews(query);


                if (response == null ||
                        response.getArticles() == null) {

                    System.out.println(
                            "No articles received for "
                                    + discoveredCategory
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
                     * We no longer blindly trust the category
                     * of the query that discovered the article.
                     */

                    String actualCategory =
                            classifyCategory(
                                    article,
                                    discoveredCategory
                            );


                    article.setCategory(
                            actualCategory
                    );


                    /*
                     * Add only once by URL.
                     */

                    uniqueArticles.putIfAbsent(
                            article.getUrl(),
                            article
                    );
                }


            } catch (Exception e) {

                System.out.println(
                        "Failed to fetch "
                                + discoveredCategory
                                + " news."
                );

                System.out.println(
                        "Error: "
                                + e.getMessage()
                );
            }
        }


        List<NewsApiArticleDTO> articles =
                new ArrayList<>(
                        uniqueArticles.values()
                );


        System.out.println();
        System.out.println(
                "Total unique articles fetched: "
                        + articles.size()
        );


        return articles;
    }


    // =====================================================
    // CATEGORY CLASSIFICATION
    // =====================================================

    private String classifyCategory(
            NewsApiArticleDTO article,
            String discoveredCategory) {

        String text =
                buildArticleText(article);


        String lower =
                text.toLowerCase(Locale.ENGLISH);


        Map<String, Integer> scores =
                new LinkedHashMap<>();


        scores.put("Technology", 0);
        scores.put("Business", 0);
        scores.put("Sports", 0);
        scores.put("Entertainment", 0);
        scores.put("Science", 0);
        scores.put("World", 0);
        scores.put("India", 0);
        scores.put("Politics", 0);


        // =================================================
        // TECHNOLOGY
        // =================================================

        addScore(
                scores,
                "Technology",
                lower,
                4,
                "artificial intelligence",
                "cybersecurity",
                "semiconductor",
                "cloud computing",
                "machine learning",
                "smartphone",
                "software",
                "robotics",
                "nvidia",
                "microsoft",
                "google",
                "apple"
        );


        // =================================================
        // BUSINESS
        // =================================================

        addScore(
                scores,
                "Business",
                lower,
                4,
                "stock market",
                "stocks",
                "earnings",
                "share price",
                "financial markets",
                "banking",
                "investment",
                "investors",
                "economy",
                "inflation",
                "revenue",
                "profit",
                "merger",
                "acquisition"
        );


        // =================================================
        // SPORTS
        // =================================================

        addScore(
                scores,
                "Sports",
                lower,
                4,
                "cricket",
                "football",
                "soccer",
                "tennis",
                "basketball",
                "formula 1",
                "olympics",
                "championship",
                "athlete",
                "match",
                "tournament",
                "league"
        );


        // =================================================
        // ENTERTAINMENT
        // =================================================

        addScore(
                scores,
                "Entertainment",
                lower,
                4,
                "movie",
                "film",
                "television",
                "tv series",
                "music",
                "actor",
                "actress",
                "celebrity",
                "hollywood",
                "netflix",
                "streaming",
                "box office"
        );


        // =================================================
        // SCIENCE
        // =================================================

        addScore(
                scores,
                "Science",
                lower,
                4,
                "scientific discovery",
                "scientific research",
                "space",
                "nasa",
                "astronomy",
                "physics",
                "biology",
                "genetics",
                "researchers",
                "research study",
                "climate science"
        );


        // =================================================
        // WORLD
        // =================================================

        addScore(
                scores,
                "World",
                lower,
                4,
                "geopolitics",
                "diplomacy",
                "international relations",
                "united nations",
                "nato",
                "international conflict",
                "foreign affairs",
                "war",
                "ceasefire",
                "sanctions"
        );


        // =================================================
        // INDIA
        // =================================================

        addScore(
                scores,
                "India",
                lower,
                4,
                "india",
                "indian government",
                "indian economy",
                "indian politics",
                "new delhi",
                "mumbai",
                "bengaluru",
                "bangalore",
                "kolkata",
                "hyderabad",
                "chennai"
        );


        // =================================================
        // POLITICS
        // =================================================

        addScore(
                scores,
                "Politics",
                lower,
                5,
                "politics",
                "political",
                "parliament",
                "government",
                "election",
                "president",
                "prime minister",
                "minister",
                "legislation",
                "senate",
                "congress",
                "political party",
                "opposition",
                "lawmakers"
        );


        /*
         * Give the discovered category a small bonus.
         *
         * This prevents random category changes when
         * two categories have similar scores.
         */

        if (scores.containsKey(discoveredCategory)) {

            scores.put(
                    discoveredCategory,
                    scores.get(discoveredCategory) + 2
            );
        }


        // =================================================
        // FIND HIGHEST SCORE
        // =================================================

        String bestCategory =
                discoveredCategory;

        int bestScore =
                scores.getOrDefault(
                        discoveredCategory,
                        0
                );


        for (Map.Entry<String, Integer> entry :
                scores.entrySet()) {

            if (entry.getValue() > bestScore) {

                bestCategory =
                        entry.getKey();

                bestScore =
                        entry.getValue();
            }
        }


        /*
         * If there is almost no evidence for the category,
         * keep the original discovery category.
         */

        if (bestScore < 4) {

            return discoveredCategory;
        }


        if (!bestCategory.equals(
                discoveredCategory)) {

            System.out.println(
                    "CATEGORY CORRECTED: "
                            + discoveredCategory
                            + " -> "
                            + bestCategory
                            + " | "
                            + article.getTitle()
            );
        }


        return bestCategory;
    }


    // =====================================================
    // BUILD ARTICLE TEXT
    // =====================================================

    private String buildArticleText(
            NewsApiArticleDTO article) {

        StringBuilder text =
                new StringBuilder();


        if (article.getTitle() != null) {

            text.append(
                    article.getTitle()
            ).append(" ");
        }


        if (article.getDescription() != null) {

            text.append(
                    article.getDescription()
            ).append(" ");
        }


        if (article.getContent() != null) {

            text.append(
                    article.getContent()
            ).append(" ");
        }


        if (article.getSource() != null &&
                article.getSource().getName() != null) {

            text.append(
                    article.getSource().getName()
            );
        }


        return text.toString();
    }


    // =====================================================
    // ADD CATEGORY SCORE
    // =====================================================

    private void addScore(
            Map<String, Integer> scores,
            String category,
            String text,
            int points,
            String... keywords) {

        for (String keyword : keywords) {

            if (containsKeyword(
                    text,
                    keyword
            )) {

                scores.put(
                        category,
                        scores.get(category)
                                + points
                );
            }
        }
    }


    // =====================================================
    // KEYWORD MATCH
    // =====================================================

    private boolean containsKeyword(
            String text,
            String keyword) {

        String normalized =
                keyword
                        .toLowerCase(Locale.ENGLISH)
                        .trim();


        /*
         * Multi-word phrases can simply use contains().
         */

        if (normalized.contains(" ")) {

            return text.contains(
                    normalized
            );
        }


        /*
         * Single words use word boundaries so we don't
         * accidentally match pieces of other words.
         */

        return text.matches(
                "(?s).*\\b"
                        + java.util.regex.Pattern.quote(
                                normalized
                        )
                        + "\\b.*"
        );
    }
}