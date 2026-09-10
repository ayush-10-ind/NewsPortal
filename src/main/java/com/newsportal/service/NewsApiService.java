package com.newsportal.service;

import com.newsportal.dto.NewsApiArticleDTO;
import com.newsportal.dto.NewsApiResponseDTO;
import com.newsportal.source.NewsSection;
import com.newsportal.source.NewsSource;
import com.newsportal.source.SectionRouterService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger logger =
            LoggerFactory.getLogger(NewsApiService.class);

    private final WebClient webClient;
    private final SectionRouterService sectionRouterService;

    @Value("${newsapi.api-key}")
    private String apiKey;

    public NewsApiService(
            @Value("${newsapi.base-url}") String baseUrl,
            SectionRouterService sectionRouterService) {
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .build();
        this.sectionRouterService = sectionRouterService;
    }

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

    public List<NewsApiArticleDTO> getNewsForSection(
            NewsSection section) {

        if (section == null) {
            logger.warn("NewsAPI fetch skipped: section is null");
            return List.of();
        }

        logger.info("Fetching NewsAPI section: {}", section.getDisplayName());

        List<NewsSource> usableSources =
                sectionRouterService.getUsableSources(section);

        if (usableSources.isEmpty()) {
            logger.warn(
                    "No usable NewsAPI sources configured: section={}",
                    section.getDisplayName()
            );
            return List.of();
        }

        Map<String, NewsApiArticleDTO> uniqueArticles =
                new LinkedHashMap<>();

        for (NewsSource source : usableSources) {

            if (!"NEWS_API".equalsIgnoreCase(source.getProviderType())) {
                continue;
            }

            try {
                List<NewsApiArticleDTO> fetched =
                        fetchFromNewsApi(section, source);

                for (NewsApiArticleDTO article : fetched) {
                    if (article == null ||
                            article.getUrl() == null ||
                            article.getUrl().isBlank()) {
                        continue;
                    }

                    article.setCategory(section.getDisplayName());
                    uniqueArticles.putIfAbsent(article.getUrl(), article);
                }

            } catch (Exception e) {
                logger.warn(
                        "NewsAPI source failed: source={}, errorType={}, message={}",
                        source.getName(),
                        e.getClass().getSimpleName(),
                        e.getMessage()
                );
            }
        }

        List<NewsApiArticleDTO> result =
                new ArrayList<>(uniqueArticles.values());

        logger.info(
                "NewsAPI section fetched: section={}, uniqueArticles={}",
                section.getDisplayName(),
                result.size()
        );

        return result;
    }

    private List<NewsApiArticleDTO> fetchFromNewsApi(
            NewsSection section,
            NewsSource source) {

        String sourceId = source.getSourceId();

        if (section == NewsSection.INDIA) {
            return fetchTopHeadlines("in", null, null, 20);
        }

        switch (section) {
            case SPORTS:
                return fetchTopHeadlines(null, "sports", null, 20);

            case BUSINESS:
                return fetchTopHeadlines(null, "business", null, 20);

            case TECHNOLOGY:
                return fetchTopHeadlines(null, "technology", null, 20);

            case ENTERTAINMENT:
                return fetchTopHeadlines(null, "entertainment", null, 20);

            case SCIENCE:
                return fetchTopHeadlines(null, "science", null, 20);

            case ANIME:
                logger.debug("Anime requires a dedicated provider");
                return List.of();

            case GAMING:
                logger.debug("Gaming requires a dedicated provider");
                return List.of();

            case WORLD:
                return searchNews(
                        "international OR " +
                                "geopolitics OR " +
                                "diplomacy OR " +
                                "\"United Nations\" OR " +
                                "NATO"
                );

            default:
                return searchNews("latest news");
        }
    }

    private List<NewsApiArticleDTO> fetchTopHeadlines(
            String country,
            String category,
            String sources,
            int pageSize) {

        NewsApiResponseDTO response =
                webClient
                        .get()
                        .uri(uriBuilder -> {
                            uriBuilder.path("/top-headlines");

                            if (country != null && !country.isBlank()) {
                                uriBuilder.queryParam("country", country);
                            }

                            if (category != null && !category.isBlank()) {
                                uriBuilder.queryParam("category", category);
                            }

                            if (sources != null && !sources.isBlank()) {
                                uriBuilder.queryParam("sources", sources);
                            }

                            uriBuilder.queryParam("pageSize", pageSize);
                            return uriBuilder.build();
                        })
                        .header("X-Api-Key", apiKey)
                        .retrieve()
                        .bodyToMono(NewsApiResponseDTO.class)
                        .block();

        if (response == null || response.getArticles() == null) {
            return List.of();
        }

        return response.getArticles();
    }

    private List<NewsApiArticleDTO> searchNews(String query) {

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
                                .queryParam("q", query)
                                .queryParam("from", fromDate)
                                .queryParam("to", toDate)
                                .queryParam("language", "en")
                                .queryParam("sortBy", "publishedAt")
                                .queryParam("pageSize", 20)
                                .build())
                        .header("X-Api-Key", apiKey)
                        .retrieve()
                        .bodyToMono(NewsApiResponseDTO.class)
                        .block();

        if (response == null || response.getArticles() == null) {
            return List.of();
        }

        return response.getArticles();
    }

    public List<NewsApiArticleDTO> getAllTopHeadlines() {

        Map<String, NewsApiArticleDTO> uniqueArticles =
                new LinkedHashMap<>();

        NewsSection[] sections = {
                NewsSection.INDIA,
                NewsSection.WORLD,
                NewsSection.SPORTS,
                NewsSection.BUSINESS,
                NewsSection.TECHNOLOGY,
                NewsSection.ENTERTAINMENT,
                NewsSection.SCIENCE
        };

        for (NewsSection section : sections) {
            try {
                List<NewsApiArticleDTO> articles =
                        getNewsForSection(section);

                for (NewsApiArticleDTO article : articles) {
                    if (article == null ||
                            article.getUrl() == null ||
                            article.getUrl().isBlank()) {
                        continue;
                    }

                    uniqueArticles.putIfAbsent(article.getUrl(), article);
                }

            } catch (Exception e) {
                logger.warn(
                        "NewsAPI section fetch failed: section={}, errorType={}, message={}",
                        section.getDisplayName(),
                        e.getClass().getSimpleName(),
                        e.getMessage()
                );
            }
        }

        List<NewsApiArticleDTO> articles =
                new ArrayList<>(uniqueArticles.values());

        logger.info(
                "NewsAPI section-aware fetch completed: uniqueArticles={}",
                articles.size()
        );

        return articles;
    }

    // The SectionRouter is responsible for AgniPress section classification.
}
