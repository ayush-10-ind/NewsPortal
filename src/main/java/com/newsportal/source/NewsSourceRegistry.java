package com.newsportal.source;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

@Component
public class NewsSourceRegistry {

    private final Map<NewsSection, List<NewsSource>> sources =
            new EnumMap<>(NewsSection.class);

    public NewsSourceRegistry() {
        registerSources();
    }

    private void registerSources() {

        // =========================================================
        // INDIA
        // =========================================================

        add(
            "NewsAPI India",
            NewsSection.INDIA,
            1,
            "NEWS_API",
            "india",
            "https://newsapi.org/v2/top-headlines?country=in",
            true,
            true,
            true,
            true
        );

        add(
            "Indian Express RSS",
            NewsSection.INDIA,
            2,
            "RSS",
            "indian-express",
            "https://indianexpress.com/rss/",
            true,
            true,
            true,
            true
        );

        add(
            "Press Information Bureau RSS",
            NewsSection.INDIA,
            3,
            "RSS",
            "pib",
            "https://www.pib.gov.in/ViewRss.aspx?lang=1&reg=1",
            true,
            true,
            true,
            true
        );


        // =========================================================
        // WORLD
        // =========================================================

        add(
            "NewsAPI World",
            NewsSection.WORLD,
            1,
            "NEWS_API",
            "world",
            "https://newsapi.org/v2/top-headlines",
            true,
            true,
            true,
            true
        );

        add(
            "Ars Technica RSS",
            NewsSection.WORLD,
            2,
            "RSS",
            "ars-technica",
            "https://feeds.arstechnica.com/arstechnica/index",
            true,
            true,
            true,
            true
        );

        add(
            "WIRED RSS",
            NewsSection.WORLD,
            3,
            "RSS",
            "wired",
            "https://www.wired.com/feed/rss",
            true,
            true,
            true,
            true
        );


        // =========================================================
        // SPORTS
        // =========================================================

        add(
            "NewsAPI Sports",
            NewsSection.SPORTS,
            1,
            "NEWS_API",
            "sports",
            "https://newsapi.org/v2/top-headlines",
            true,
            true,
            true,
            true
        );

        add(
            "Indian Express Sports RSS",
            NewsSection.SPORTS,
            2,
            "RSS",
            "indian-express-sports",
            "https://indianexpress.com/section/sports/feed/",
            true,
            true,
            true,
            true
        );


        // =========================================================
        // ANIME
        // =========================================================

        // No verified RSS endpoint is currently configured.
        // Keep these disabled until a real source is added.

        add(
            "Anime Provider",
            NewsSection.ANIME,
            1,
            "ANIME_API",
            "anime",
            "",
            false,
            false,
            false,
            false
        );

        add(
            "Anime Official Feed",
            NewsSection.ANIME,
            2,
            "RSS",
            "anime-official",
            "",
            false,
            false,
            false,
            false
        );


        // =========================================================
        // BUSINESS
        // =========================================================

        add(
            "NewsAPI Business",
            NewsSection.BUSINESS,
            1,
            "NEWS_API",
            "business",
            "https://newsapi.org/v2/top-headlines",
            true,
            true,
            true,
            true
        );

        add(
            "Indian Express Economy RSS",
            NewsSection.BUSINESS,
            2,
            "RSS",
            "indian-express-economy",
            "https://indianexpress.com/section/business/economy/feed/",
            true,
            true,
            true,
            true
        );

        add(
            "WIRED Business RSS",
            NewsSection.BUSINESS,
            3,
            "RSS",
            "wired-business",
            "https://www.wired.com/feed/category/business/latest/rss",
            true,
            true,
            true,
            true
        );


        // =========================================================
        // TECHNOLOGY
        // =========================================================

        add(
            "NewsAPI Technology",
            NewsSection.TECHNOLOGY,
            1,
            "NEWS_API",
            "technology",
            "https://newsapi.org/v2/top-headlines",
            true,
            true,
            true,
            true
        );

        add(
            "Ars Technica Technology RSS",
            NewsSection.TECHNOLOGY,
            2,
            "RSS",
            "ars-technica-technology",
            "https://feeds.arstechnica.com/arstechnica/index",
            true,
            true,
            true,
            true
        );

        add(
            "WIRED Technology RSS",
            NewsSection.TECHNOLOGY,
            3,
            "RSS",
            "wired-technology",
            "https://www.wired.com/feed/category/gear/latest/rss",
            true,
            true,
            true,
            true
        );


        // =========================================================
        // ENTERTAINMENT
        // =========================================================

        add(
            "NewsAPI Entertainment",
            NewsSection.ENTERTAINMENT,
            1,
            "NEWS_API",
            "entertainment",
            "https://newsapi.org/v2/top-headlines",
            true,
            true,
            true,
            true
        );

        add(
            "Indian Express Entertainment RSS",
            NewsSection.ENTERTAINMENT,
            2,
            "RSS",
            "indian-express-entertainment",
            "https://indianexpress.com/section/entertainment/feed/",
            true,
            true,
            true,
            true
        );

        add(
            "WIRED Culture RSS",
            NewsSection.ENTERTAINMENT,
            3,
            "RSS",
            "wired-culture",
            "https://www.wired.com/feed/category/culture/latest/rss",
            true,
            true,
            true,
            true
        );


        // =========================================================
        // SCIENCE
        // =========================================================

        add(
            "NewsAPI Science",
            NewsSection.SCIENCE,
            1,
            "NEWS_API",
            "science",
            "https://newsapi.org/v2/top-headlines",
            true,
            true,
            true,
            true
        );

        // NASA RSS is already proven to work in your project.
        add(
            "NASA RSS",
            NewsSection.SCIENCE,
            2,
            "RSS",
            "nasa",
            "https://www.nasa.gov/rss/dyn/breaking_news.rss",
            true,
            true,
            true,
            true
        );

        add(
            "Ars Technica Science RSS",
            NewsSection.SCIENCE,
            3,
            "RSS",
            "ars-technica-science",
            "https://feeds.arstechnica.com/arstechnica/science",
            true,
            true,
            true,
            true
        );

        add(
            "WIRED Science RSS",
            NewsSection.SCIENCE,
            4,
            "RSS",
            "wired-science",
            "https://www.wired.com/feed/category/science/latest/rss",
            true,
            true,
            true,
            true
        );


        // =========================================================
        // GAMING
        // =========================================================

        add(
            "Ars Technica Gaming RSS",
            NewsSection.GAMING,
            1,
            "RSS",
            "ars-technica-gaming",
            "https://feeds.arstechnica.com/arstechnica/gaming",
            true,
            true,
            true,
            true
        );

        add(
            "Gaming News Search",
            NewsSection.GAMING,
            2,
            "NEWS_API_SEARCH",
            "gaming-search",
            "https://newsapi.org/v2/everything?q=gaming",
            true,
            true,
            true,
            false
        );
    }


    // =============================================================
    // REGISTER SOURCE
    // =============================================================

    private void add(
            String name,
            NewsSection section,
            int priority,
            String providerType,
            String sourceId,
            String endpoint,
            boolean newsAllowed,
            boolean imageAllowed,
            boolean automatedAccessAllowed,
            boolean enabled) {

        NewsSource source = NewsSourceConfig.create(
            name,
            section,
            priority,
            providerType,
            sourceId,
            endpoint,
            newsAllowed,
            imageAllowed,
            automatedAccessAllowed,
            enabled
        );

        sources
            .computeIfAbsent(section, key -> new ArrayList<>())
            .add(source);
    }


    // =============================================================
    // GET ALL SOURCES
    // =============================================================

    public List<NewsSource> getSources(NewsSection section) {

        return sources.getOrDefault(
                section,
                List.of()
        );
    }


    // =============================================================
    // GET USABLE SOURCES
    // =============================================================

    public List<NewsSource> getUsableSources(NewsSection section) {

        return getSources(section)
                .stream()
                .filter(NewsSource::isUsable)
                .sorted(
                    Comparator.comparingInt(
                        NewsSource::getPriority
                    )
                )
                .toList();
    }


    // =============================================================
    // GET ALL SOURCES
    // =============================================================

    public List<NewsSource> getAllSources() {

        return sources.values()
                .stream()
                .flatMap(List::stream)
                .sorted(
                    Comparator.comparing(
                        (NewsSource source) ->
                            source.getSection().name()
                    ).thenComparingInt(
                        NewsSource::getPriority
                    )
                )
                .toList();
    }


    // =============================================================
    // GET RSS SOURCES
    // =============================================================

    public List<NewsSource> getUsableRssSources(
            NewsSection section) {

        return getUsableSources(section)
                .stream()
                .filter(source ->
                    "RSS".equalsIgnoreCase(
                        source.getProviderType()
                    )
                )
                .toList();
    }
}