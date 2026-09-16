package com.newsportal.source;

import com.newsportal.repository.NewsRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class MultiSourceNewsFetcherService {

    private static final Logger logger = LoggerFactory.getLogger(MultiSourceNewsFetcherService.class);
    private static final int MAX_NEW_ARTICLES_PER_SECTION = 12;

    private final NewsSourceRegistry sourceRegistry;
    private final RssNewsFetcherService rssNewsFetcherService;
    private final NewsRepository newsRepository;

    public MultiSourceNewsFetcherService(
            NewsSourceRegistry sourceRegistry,
            RssNewsFetcherService rssNewsFetcherService,
            NewsRepository newsRepository) {
        this.sourceRegistry = sourceRegistry;
        this.rssNewsFetcherService = rssNewsFetcherService;
        this.newsRepository = newsRepository;
    }

    public List<RssNewsFetcherService.RssArticle> fetchRssForSection(NewsSection section) {
        List<RssNewsFetcherService.RssArticle> results = new ArrayList<>();

        if (section == null) {
            logger.warn("RSS fetch skipped: section is null");
            return results;
        }

        List<NewsSource> sources = sourceRegistry.getUsableRssSources(section);

        logger.info("Fetching RSS sources: section={}, sources={}, maxNewArticles={}",
                section.getDisplayName(), sources.size(), MAX_NEW_ARTICLES_PER_SECTION);

        List<List<RssNewsFetcherService.RssArticle>> sourceResults = new ArrayList<>();

        for (NewsSource source : sources) {
            try {
                List<RssNewsFetcherService.RssArticle> articles =
                        rssNewsFetcherService.fetchFeed(source);
                sourceResults.add(articles == null ? List.of() : articles);
            } catch (Exception e) {
                sourceResults.add(List.of());
                logger.error("RSS source failed: source={}, errorType={}, message={}",
                        source.getName(),
                        e.getClass().getSimpleName(),
                        e.getMessage());
            }
        }

        /*
         * Select up to 12 genuinely new editorial articles for the section.
         * The wider per-feed window allows promotional/coupon noise to be
         * filtered without unnecessarily reducing the useful section quota.
         */
        Set<String> seenSourceUrls = new HashSet<>();
        Set<String> seenTitles = new HashSet<>();

        int maxSourceLength = maxSourceLength(sourceResults);

        for (int index = 0; index < maxSourceLength && results.size() < MAX_NEW_ARTICLES_PER_SECTION; index++) {
            for (List<RssNewsFetcherService.RssArticle> articles : sourceResults) {
                if (index >= articles.size() || results.size() >= MAX_NEW_ARTICLES_PER_SECTION) {
                    continue;
                }

                RssNewsFetcherService.RssArticle article = articles.get(index);
                if (article == null) continue;

                String sourceUrl = clean(article.getUrl());
                String title = clean(article.getTitle());
                if (sourceUrl == null || title == null) continue;

                if (isPromotionalNoise(title)) {
                    logger.debug("RSS article skipped as promotional noise: section={}, title={}",
                            section.getDisplayName(), title);
                    continue;
                }

                String urlKey = sourceUrl.toLowerCase(Locale.ROOT);
                String titleKey = title.toLowerCase(Locale.ROOT);

                if (!seenSourceUrls.add(urlKey)) continue;
                if (!seenTitles.add(titleKey)) continue;

                if (newsRepository.findBySourceUrl(sourceUrl).isPresent()) {
                    continue;
                }

                if (newsRepository.existsByTitleAndCategoryIgnoreCase(
                        title,
                        section.getDisplayName())) {
                    continue;
                }

                results.add(article);
            }
        }

        logger.info("RSS sources fetched: section={}, newCandidates={}, maxPerSection={}",
                section.getDisplayName(), results.size(), MAX_NEW_ARTICLES_PER_SECTION);

        return results;
    }

    public List<RssNewsFetcherService.RssArticle> fetchAllRssSources() {
        List<RssNewsFetcherService.RssArticle> results = new ArrayList<>();

        for (NewsSection section : NewsSection.values()) {
            results.addAll(fetchRssForSection(section));
        }

        return results;
    }

    public List<NewsSource> getUsableSources(NewsSection section) {
        return sourceRegistry.getUsableSources(section);
    }

    public List<NewsSource> getUsableRssSources(NewsSection section) {
        return sourceRegistry.getUsableRssSources(section);
    }

    public String getSourceStatus() {
        StringBuilder result = new StringBuilder();
        result.append("AGNIPRESS SOURCE STATUS\n");

        for (NewsSection section : NewsSection.values()) {
            List<NewsSource> sources = sourceRegistry.getSources(section);

            result.append("\n")
                    .append(section.getDisplayName())
                    .append(":\n");

            if (sources.isEmpty()) {
                result.append("  No sources configured.\n");
                continue;
            }

            for (NewsSource source : sources) {
                result.append("  - ")
                        .append(source.getName())
                        .append(" | ")
                        .append(source.getProviderType())
                        .append(" | enabled=")
                        .append(source.isEnabled())
                        .append(" | usable=")
                        .append(source.isUsable())
                        .append("\n");
            }
        }

        return result.toString();
    }

    private int maxSourceLength(List<List<RssNewsFetcherService.RssArticle>> sourceResults) {
        int maximum = 0;
        for (List<RssNewsFetcherService.RssArticle> articles : sourceResults) {
            maximum = Math.max(maximum, articles.size());
        }
        return maximum;
    }

    private boolean isPromotionalNoise(String title) {
        String normalized = title
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .trim();

        if (normalized.isBlank()) return false;

        String[] phrases = {
                "coupon",
                "coupon codes",
                "promo code",
                "promo codes",
                "promotion code",
                "discount code",
                "discount codes",
                "discount deal",
                "discount deals",
                "shop now",
                "save 20",
                "save 30",
                "save 40",
                "save 50",
                "save 60",
                "save 70",
                "get percent off"
        };

        for (String phrase : phrases) {
            if (normalized.contains(phrase)) {
                return true;
            }
        }

        return normalized.startsWith("promo ")
                || normalized.startsWith("discount ")
                || normalized.endsWith(" promo code")
                || normalized.endsWith(" coupon code");
    }

    private String clean(String value) {
        if (value == null) return null;
        String cleaned = value.trim();
        return cleaned.isBlank() ? null : cleaned;
    }
}
