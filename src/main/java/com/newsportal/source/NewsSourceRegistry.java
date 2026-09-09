package com.newsportal.source;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Central registry for all AgniPress news sources.
 *
 * Architecture:
 *
 * AGNIPRESS
 *     |
 *     v
 * SOURCE REGISTRY
 *     |
 *     +--> INDIA
 *     +--> WORLD
 *     +--> SPORTS
 *     +--> ANIME
 *     +--> BUSINESS
 *     +--> TECHNOLOGY
 *     +--> ENTERTAINMENT
 *     +--> SCIENCE
 *     +--> GAMING
 *
 * Each section can have multiple sources with priorities.
 *
 * IMPORTANT:
 * - Only sources explicitly marked usable are returned by
 *   getUsableSources().
 * - Sources requiring additional verification remain disabled.
 * - No publisher scraping/bypass is performed here.
 * - Image handling remains the responsibility of ArticleImageService.
 */
@Component
public class NewsSourceRegistry {

    private final Map<NewsSection, List<NewsSource>> sourcesBySection =
            new EnumMap<>(NewsSection.class);


    // =====================================================
    // CONSTRUCTOR
    // =====================================================

    public NewsSourceRegistry() {

        initializeSections();

        registerSources();
    }


    // =====================================================
    // INITIALIZE SECTIONS
    // =====================================================

    private void initializeSections() {

        for (NewsSection section :
                NewsSection.values()) {

            sourcesBySection.put(
                    section,
                    new ArrayList<>()
            );
        }
    }


    // =====================================================
    // REGISTER SOURCES
    // =====================================================

    private void registerSources() {

        // =================================================
        // INDIA
        // =================================================

        registerSource(
                NewsSourceConfig.create(
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
                )
        );


        registerSource(
                NewsSourceConfig.create(
                        "Indian Express RSS",
                        NewsSection.INDIA,
                        2,
                        "RSS",
                        "indian-express",
                        "https://indianexpress.com/rss/",
                        true,
                        false,
                        true,
                        false
                )
        );


        registerSource(
                NewsSourceConfig.create(
                        "Press Information Bureau RSS",
                        NewsSection.INDIA,
                        3,
                        "RSS",
                        "pib",
                        "https://www.pib.gov.in/ViewRss.aspx?lang=1&reg=1",
                        true,
                        false,
                        true,
                        false
                )
        );


        // =================================================
        // WORLD
        // =================================================

        registerSource(
                NewsSourceConfig.create(
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
                )
        );


        registerSource(
                NewsSourceConfig.create(
                        "Ars Technica",
                        NewsSection.WORLD,
                        2,
                        "RSS",
                        "ars-technica",
                        "https://feeds.arstechnica.com/arstechnica/index",
                        true,
                        false,
                        true,
                        false
                )
        );


        registerSource(
                NewsSourceConfig.create(
                        "WIRED",
                        NewsSection.WORLD,
                        3,
                        "RSS",
                        "wired",
                        "https://www.wired.com/feed/rss",
                        true,
                        false,
                        true,
                        false
                )
        );


        // =================================================
        // SPORTS
        // =================================================

        registerSource(
                NewsSourceConfig.create(
                        "NewsAPI Sports",
                        NewsSection.SPORTS,
                        1,
                        "NEWS_API",
                        "sports",
                        "https://newsapi.org/v2/top-headlines?category=sports",
                        true,
                        true,
                        true,
                        true
                )
        );


        registerSource(
                NewsSourceConfig.create(
                        "Indian Express Sports RSS",
                        NewsSection.SPORTS,
                        2,
                        "RSS",
                        "indian-express-sports",
                        "https://indianexpress.com/section/sports/feed/",
                        true,
                        false,
                        true,
                        false
                )
        );


        // =================================================
        // ANIME
        // =================================================

        registerSource(
                NewsSourceConfig.create(
                        "Anime Provider",
                        NewsSection.ANIME,
                        1,
                        "ANIME_API",
                        "anime-primary",
                        "",
                        false,
                        false,
                        false,
                        false
                )
        );


        registerSource(
                NewsSourceConfig.create(
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
                )
        );


        // =================================================
        // BUSINESS
        // =================================================

        registerSource(
                NewsSourceConfig.create(
                        "NewsAPI Business",
                        NewsSection.BUSINESS,
                        1,
                        "NEWS_API",
                        "business",
                        "https://newsapi.org/v2/top-headlines?category=business",
                        true,
                        true,
                        true,
                        true
                )
        );


        registerSource(
                NewsSourceConfig.create(
                        "Indian Express Economy RSS",
                        NewsSection.BUSINESS,
                        2,
                        "RSS",
                        "indian-express-economy",
                        "https://indianexpress.com/section/business/economy/feed/",
                        true,
                        false,
                        true,
                        false
                )
        );


        registerSource(
                NewsSourceConfig.create(
                        "WIRED Business RSS",
                        NewsSection.BUSINESS,
                        3,
                        "RSS",
                        "wired-business",
                        "https://www.wired.com/feed/category/business/latest/rss",
                        true,
                        false,
                        true,
                        false
                )
        );


        // =================================================
        // TECHNOLOGY
        // =================================================

        registerSource(
                NewsSourceConfig.create(
                        "NewsAPI Technology",
                        NewsSection.TECHNOLOGY,
                        1,
                        "NEWS_API",
                        "technology",
                        "https://newsapi.org/v2/top-headlines?category=technology",
                        true,
                        true,
                        true,
                        true
                )
        );


        registerSource(
                NewsSourceConfig.create(
                        "Ars Technica Technology RSS",
                        NewsSection.TECHNOLOGY,
                        2,
                        "RSS",
                        "ars-technica-technology",
                        "https://feeds.arstechnica.com/arstechnica/index",
                        true,
                        false,
                        true,
                        false
                )
        );


        registerSource(
                NewsSourceConfig.create(
                        "WIRED Technology RSS",
                        NewsSection.TECHNOLOGY,
                        3,
                        "RSS",
                        "wired-technology",
                        "https://www.wired.com/feed/category/gear/latest/rss",
                        true,
                        false,
                        true,
                        false
                )
        );


        // =================================================
        // ENTERTAINMENT
        // =================================================

        registerSource(
                NewsSourceConfig.create(
                        "NewsAPI Entertainment",
                        NewsSection.ENTERTAINMENT,
                        1,
                        "NEWS_API",
                        "entertainment",
                        "https://newsapi.org/v2/top-headlines?category=entertainment",
                        true,
                        true,
                        true,
                        true
                )
        );


        registerSource(
                NewsSourceConfig.create(
                        "Indian Express Entertainment RSS",
                        NewsSection.ENTERTAINMENT,
                        2,
                        "RSS",
                        "indian-express-entertainment",
                        "https://indianexpress.com/section/entertainment/feed/",
                        true,
                        false,
                        true,
                        false
                )
        );


        registerSource(
                NewsSourceConfig.create(
                        "WIRED Culture RSS",
                        NewsSection.ENTERTAINMENT,
                        3,
                        "RSS",
                        "wired-culture",
                        "https://www.wired.com/feed/category/culture/latest/rss",
                        true,
                        false,
                        true,
                        false
                )
        );


        // =================================================
        // SCIENCE
        // =================================================

        registerSource(
                NewsSourceConfig.create(
                        "NewsAPI Science",
                        NewsSection.SCIENCE,
                        1,
                        "NEWS_API",
                        "science",
                        "https://newsapi.org/v2/top-headlines?category=science",
                        true,
                        true,
                        true,
                        true
                )
        );


        /*
         * NASA officially provides RSS feeds for its news releases
         * and topical content.
         *
         * This is the first RSS source enabled for AgniPress.
         *
         * Image handling remains subject to NASA's current media
         * guidelines and the specific asset's rights/credits.
         */
        registerSource(
                NewsSourceConfig.create(
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
                )
        );


        registerSource(
                NewsSourceConfig.create(
                        "Ars Technica Science RSS",
                        NewsSection.SCIENCE,
                        3,
                        "RSS",
                        "ars-technica-science",
                        "https://feeds.arstechnica.com/arstechnica/science",
                        true,
                        false,
                        true,
                        false
                )
        );


        registerSource(
                NewsSourceConfig.create(
                        "WIRED Science RSS",
                        NewsSection.SCIENCE,
                        4,
                        "RSS",
                        "wired-science",
                        "https://www.wired.com/feed/category/science/latest/rss",
                        true,
                        false,
                        true,
                        false
                )
        );


        // =================================================
        // GAMING
        // =================================================

        registerSource(
                NewsSourceConfig.create(
                        "Ars Technica Gaming RSS",
                        NewsSection.GAMING,
                        1,
                        "RSS",
                        "ars-technica-gaming",
                        "https://feeds.arstechnica.com/arstechnica/gaming",
                        true,
                        false,
                        true,
                        false
                )
        );


        registerSource(
                NewsSourceConfig.create(
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
                )
        );
    }


    // =====================================================
    // REGISTER SOURCE
    // =====================================================

    public void registerSource(
            NewsSource source) {

        if (source == null ||
                source.getSection() == null) {

            return;
        }


        sourcesBySection
                .computeIfAbsent(
                        source.getSection(),
                        key -> new ArrayList<>()
                )
                .add(source);


        /*
         * Lower priority number means higher priority.
         */
        sourcesBySection
                .get(source.getSection())
                .sort(
                        (a, b) ->
                                Integer.compare(
                                        a.getPriority(),
                                        b.getPriority()
                                )
                );
    }


    // =====================================================
    // ALL SOURCES FOR SECTION
    // =====================================================

    public List<NewsSource> getSources(
            NewsSection section) {

        if (section == null) {

            return Collections.emptyList();
        }


        return Collections.unmodifiableList(
                sourcesBySection.getOrDefault(
                        section,
                        Collections.emptyList()
                )
        );
    }


    // =====================================================
    // USABLE SOURCES
    // =====================================================

    public List<NewsSource> getUsableSources(
            NewsSection section) {

        return getSources(section)
                .stream()
                .filter(
                        NewsSource::isUsable
                )
                .toList();
    }


    // =====================================================
    // USABLE SOURCES BY PROVIDER
    // =====================================================

    /**
     * Returns usable sources for a section that belong
     * to the requested provider type.
     *
     * Examples:
     *
     * NEWS_API
     * RSS
     * NEWS_API_SEARCH
     */
    public List<NewsSource> getUsableSources(
            NewsSection section,
            String providerType) {

        if (providerType == null ||
                providerType.isBlank()) {

            return Collections.emptyList();
        }


        String requestedProvider =
                providerType
                        .trim()
                        .toUpperCase(
                                Locale.ENGLISH
                        );


        return getUsableSources(section)
                .stream()
                .filter(source -> {

                    String actualProvider =
                            source.getProviderType();


                    return actualProvider != null &&
                            actualProvider
                                    .trim()
                                    .toUpperCase(
                                            Locale.ENGLISH
                                    )
                                    .equals(
                                            requestedProvider
                                    );
                })
                .toList();
    }


    // =====================================================
    // RSS SOURCES
    // =====================================================

    public List<NewsSource> getUsableRssSources(
            NewsSection section) {

        return getUsableSources(
                section,
                "RSS"
        );
    }


    // =====================================================
    // NEWS API SOURCES
    // =====================================================

    public List<NewsSource> getUsableNewsApiSources(
            NewsSection section) {

        return getUsableSources(
                section,
                "NEWS_API"
        );
    }


    // =====================================================
    // IMAGE-CAPABLE SOURCES
    // =====================================================

    public List<NewsSource> getImageCapableSources(
            NewsSection section) {

        return getSources(section)
                .stream()
                .filter(
                        NewsSource::supportsNewsAndImages
                )
                .toList();
    }


    // =====================================================
    // PRIMARY SOURCE
    // =====================================================

    public NewsSource getPrimarySource(
            NewsSection section) {

        return getUsableSources(section)
                .stream()
                .findFirst()
                .orElse(null);
    }


    // =====================================================
    // CHECK USABLE SOURCE
    // =====================================================

    public boolean hasUsableSource(
            NewsSection section) {

        return !getUsableSources(section)
                .isEmpty();
    }


    // =====================================================
    // CHECK IMAGE SOURCE
    // =====================================================

    public boolean hasImageCapableSource(
            NewsSection section) {

        return !getImageCapableSources(section)
                .isEmpty();
    }


    // =====================================================
    // ALL SOURCES
    // =====================================================

    public List<NewsSource> getAllSources() {

        return sourcesBySection
                .values()
                .stream()
                .flatMap(
                        List::stream
                )
                .toList();
    }


    // =====================================================
    // ALL USABLE SOURCES
    // =====================================================

    public List<NewsSource> getAllUsableSources() {

        return getAllSources()
                .stream()
                .filter(
                        NewsSource::isUsable
                )
                .toList();
    }


    // =====================================================
    // SOURCE COUNTS
    // =====================================================

    public int getSourceCount(
            NewsSection section) {

        return getSources(section)
                .size();
    }


    public int getUsableSourceCount(
            NewsSection section) {

        return getUsableSources(section)
                .size();
    }


    // =====================================================
    // PROVIDER COUNT
    // =====================================================

    public int getUsableSourceCount(
            NewsSection section,
            String providerType) {

        return getUsableSources(
                section,
                providerType
        ).size();
    }
}