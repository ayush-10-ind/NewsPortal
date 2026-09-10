package com.newsportal.source;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class MultiSourceNewsFetcherService {

    private static final Logger logger =
            LoggerFactory.getLogger(MultiSourceNewsFetcherService.class);

    private final NewsSourceRegistry sourceRegistry;

    private final RssNewsFetcherService rssNewsFetcherService;

    public MultiSourceNewsFetcherService(
            NewsSourceRegistry sourceRegistry,
            RssNewsFetcherService rssNewsFetcherService) {

        this.sourceRegistry = sourceRegistry;
        this.rssNewsFetcherService = rssNewsFetcherService;
    }

    // =====================================================
    // FETCH RSS SOURCES FOR SECTION
    // =====================================================

    public List<RssNewsFetcherService.RssArticle>
    fetchRssForSection(
            NewsSection section) {

        List<RssNewsFetcherService.RssArticle> results =
                new ArrayList<>();

        if (section == null) {
            logger.warn("RSS fetch skipped: section is null");
            return results;
        }

        List<NewsSource> sources =
                sourceRegistry.getUsableRssSources(section);

        logger.info(
                "Fetching RSS sources: section={}, sources={}",
                section.getDisplayName(),
                sources.size()
        );

        for (NewsSource source : sources) {

            try {

                List<RssNewsFetcherService.RssArticle> articles =
                        rssNewsFetcherService.fetchFeed(source);

                if (articles != null && !articles.isEmpty()) {
                    results.addAll(articles);
                }

            } catch (Exception e) {

                logger.error(
                        "RSS source failed: source={}, errorType={}, message={}",
                        source.getName(),
                        e.getClass().getSimpleName(),
                        e.getMessage()
                );
            }
        }

        logger.info(
                "RSS sources fetched: section={}, articles={}",
                section.getDisplayName(),
                results.size()
        );

        return results;
    }

    // =====================================================
    // FETCH ALL USABLE RSS SOURCES
    // =====================================================

    public List<RssNewsFetcherService.RssArticle>
    fetchAllRssSources() {

        List<RssNewsFetcherService.RssArticle> results =
                new ArrayList<>();

        for (NewsSection section : NewsSection.values()) {
            results.addAll(fetchRssForSection(section));
        }

        return results;
    }

    // =====================================================
    // GET USABLE SOURCES
    // =====================================================

    public List<NewsSource> getUsableSources(
            NewsSection section) {

        return sourceRegistry.getUsableSources(section);
    }

    // =====================================================
    // GET RSS SOURCES
    // =====================================================

    public List<NewsSource> getUsableRssSources(
            NewsSection section) {

        return sourceRegistry.getUsableRssSources(section);
    }

    // =====================================================
    // SOURCE STATUS
    // =====================================================

    public String getSourceStatus() {

        StringBuilder result = new StringBuilder();

        result.append("AGNIPRESS SOURCE STATUS\n");

        for (NewsSection section : NewsSection.values()) {

            List<NewsSource> sources =
                    sourceRegistry.getSources(section);

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
}