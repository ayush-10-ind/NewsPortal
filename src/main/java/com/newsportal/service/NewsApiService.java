package com.newsportal.service;

import com.newsportal.dto.NewsApiArticleDTO;
import com.newsportal.dto.NewsApiResponseDTO;
import com.newsportal.source.NewsSection;
import com.newsportal.source.NewsSource;
import com.newsportal.source.SectionRouterService;

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

    private final SectionRouterService sectionRouterService;

    @Value("${newsapi.api-key}")
    private String apiKey;


    // =====================================================
    // CONSTRUCTOR
    // =====================================================

    public NewsApiService(
            @Value("${newsapi.base-url}") String baseUrl,
            SectionRouterService sectionRouterService) {

        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .build();

        this.sectionRouterService =
                sectionRouterService;
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
    // SECTION-AWARE NEWS FETCH
    // =====================================================

    public List<NewsApiArticleDTO> getNewsForSection(
            NewsSection section) {

        if (section == null) {

            return List.of();
        }


        System.out.println();
        System.out.println(
                "========================================"
        );

        System.out.println(
                "FETCHING SECTION: "
                        + section.getDisplayName()
        );

        System.out.println(
                "========================================"
        );


        /*
         * Ask the SectionRouter which sources are
         * currently usable for this section.
         */

        List<NewsSource> usableSources =
                sectionRouterService
                        .getUsableSources(section);


        if (usableSources.isEmpty()) {

            System.out.println(
                    "No usable sources configured for: "
                            + section.getDisplayName()
            );

            return List.of();
        }


        Map<String, NewsApiArticleDTO>
                uniqueArticles =
                new LinkedHashMap<>();


        /*
         * Currently only NEWS_API sources are handled here.
         *
         * RSS and dedicated Anime/Gaming providers will
         * be implemented as separate services later.
         */

        for (NewsSource source :
                usableSources) {

            if (!"NEWS_API".equalsIgnoreCase(
                    source.getProviderType())) {

                continue;
            }


            try {

                List<NewsApiArticleDTO> fetched =
                        fetchFromNewsApi(
                                section,
                                source
                        );


                for (NewsApiArticleDTO article :
                        fetched) {

                    if (article == null ||
                            article.getUrl() == null ||
                            article.getUrl().isBlank()) {

                        continue;
                    }


                    /*
                     * Always use the AgniPress section,
                     * not the provider's category.
                     */

                    article.setCategory(
                            section.getDisplayName()
                    );


                    uniqueArticles.putIfAbsent(
                            article.getUrl(),
                            article
                    );
                }


            } catch (Exception e) {

                System.out.println(
                        "Failed source: "
                                + source.getName()
                );

                System.out.println(
                        "Error: "
                                + e.getMessage()
                );
            }
        }


        List<NewsApiArticleDTO> result =
                new ArrayList<>(
                        uniqueArticles.values()
                );


        System.out.println(
                "Section "
                        + section.getDisplayName()
                        + " returned "
                        + result.size()
                        + " unique articles."
        );


        return result;
    }


    // =====================================================
    // FETCH FROM NEWS API
    // =====================================================

    private List<NewsApiArticleDTO> fetchFromNewsApi(
            NewsSection section,
            NewsSource source) {

        String sourceId =
                source.getSourceId();


        /*
         * INDIA
         *
         * NewsAPI supports India through country=in.
         */

        if (section == NewsSection.INDIA) {

            return fetchTopHeadlines(
                    "in",
                    null,
                    null,
                    20
            );
        }


        /*
         * NewsAPI has dedicated categories for these
         * AgniPress sections.
         */

        switch (section) {

            case SPORTS:

                return fetchTopHeadlines(
                        null,
                        "sports",
                        null,
                        20
                );


            case BUSINESS:

                return fetchTopHeadlines(
                        null,
                        "business",
                        null,
                        20
                );


            case TECHNOLOGY:

                return fetchTopHeadlines(
                        null,
                        "technology",
                        null,
                        20
                );


            case ENTERTAINMENT:

                return fetchTopHeadlines(
                        null,
                        "entertainment",
                        null,
                        20
                );


            case SCIENCE:

                return fetchTopHeadlines(
                        null,
                        "science",
                        null,
                        20
                );


            /*
             * NewsAPI does not provide a dedicated
             * Anime category.
             *
             * Anime will be handled by an Anime-specific
             * provider later.
             */

            case ANIME:

                System.out.println(
                        "Anime requires a dedicated provider."
                );

                return List.of();


            /*
             * NewsAPI does not provide a dedicated
             * Gaming category.
             */

            case GAMING:

                System.out.println(
                        "Gaming requires a dedicated provider."
                );

                return List.of();


            /*
             * WORLD
             *
             * NewsAPI doesn't have a "world" category.
             * Use /everything with international topics.
             */

            case WORLD:

                return searchNews(
                        "international OR "
                                + "geopolitics OR "
                                + "diplomacy OR "
                                + "\"United Nations\" OR "
                                + "NATO"
                );


            default:

                return searchNews(
                        "latest news"
                );
        }
    }


    // =====================================================
    // TOP HEADLINES
    // =====================================================

    private List<NewsApiArticleDTO> fetchTopHeadlines(
            String country,
            String category,
            String sources,
            int pageSize) {

        NewsApiResponseDTO response =
                webClient
                        .get()
                        .uri(uriBuilder -> {

                            uriBuilder
                                    .path("/top-headlines");

                            if (country != null &&
                                    !country.isBlank()) {

                                uriBuilder.queryParam(
                                        "country",
                                        country
                                );
                            }


                            if (category != null &&
                                    !category.isBlank()) {

                                uriBuilder.queryParam(
                                        "category",
                                        category
                                );
                            }


                            if (sources != null &&
                                    !sources.isBlank()) {

                                uriBuilder.queryParam(
                                        "sources",
                                        sources
                                );
                            }


                            uriBuilder.queryParam(
                                    "pageSize",
                                    pageSize
                            );


                            return uriBuilder.build();
                        })
                        .header(
                                "X-Api-Key",
                                apiKey
                        )
                        .retrieve()
                        .bodyToMono(
                                NewsApiResponseDTO.class
                        )
                        .block();


        if (response == null ||
                response.getArticles() == null) {

            return List.of();
        }


        return response.getArticles();
    }


    // =====================================================
    // RECENT NEWS SEARCH
    // =====================================================

    private List<NewsApiArticleDTO> searchNews(
            String query) {

        String fromDate =
                OffsetDateTime.now(ZoneOffset.UTC)
                        .minusDays(3)
                        .toString();

        String toDate =
                OffsetDateTime.now(ZoneOffset.UTC)
                        .toString();


        NewsApiResponseDTO response =
                webClient
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
                                        20
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


        if (response == null ||
                response.getArticles() == null) {

            return List.of();
        }


        return response.getArticles();
    }


    // =====================================================
    // BACKWARD COMPATIBILITY
    // =====================================================

    /*
     * Your existing NewsImportService currently calls:
     *
     *     getAllTopHeadlines()
     *
     * Keep this method so the project remains compatible
     * while we transition the importer to section-aware
     * fetching.
     */

    public List<NewsApiArticleDTO> getAllTopHeadlines() {

        Map<String, NewsApiArticleDTO>
                uniqueArticles =
                new LinkedHashMap<>();


        /*
         * Fetch the sections that NewsAPI actually supports.
         */

        NewsSection[] sections = {

                NewsSection.INDIA,
                NewsSection.WORLD,
                NewsSection.SPORTS,
                NewsSection.BUSINESS,
                NewsSection.TECHNOLOGY,
                NewsSection.ENTERTAINMENT,
                NewsSection.SCIENCE
        };


        for (NewsSection section :
                sections) {

            try {

                List<NewsApiArticleDTO> articles =
                        getNewsForSection(
                                section
                        );


                for (NewsApiArticleDTO article :
                        articles) {

                    if (article == null ||
                            article.getUrl() == null ||
                            article.getUrl().isBlank()) {

                        continue;
                    }


                    uniqueArticles.putIfAbsent(
                            article.getUrl(),
                            article
                    );
                }


            } catch (Exception e) {

                System.out.println(
                        "Failed to fetch section: "
                                + section.getDisplayName()
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
                "Total unique section-aware articles: "
                        + articles.size()
        );


        return articles;
    }


    // =====================================================
    // OLD CATEGORY CLASSIFIER
    // =====================================================

    /*
     * We intentionally leave the old classifier out of
     * the new architecture.
     *
     * The SectionRouter is now responsible for determining
     * the AgniPress section.
     *
     * This avoids having two competing category systems.
     */
}