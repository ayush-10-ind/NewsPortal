package com.newsportal.source;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class MultiSourceNewsFetcherService {

    private final NewsSourceRegistry sourceRegistry;

    private final RssNewsFetcherService rssNewsFetcherService;

    public MultiSourceNewsFetcherService(
            NewsSourceRegistry sourceRegistry,
            RssNewsFetcherService rssNewsFetcherService) {

        this.sourceRegistry =
                sourceRegistry;

        this.rssNewsFetcherService =
                rssNewsFetcherService;
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

            return results;
        }


        List<NewsSource> sources =
                sourceRegistry.getUsableRssSources(
                        section
                );


        System.out.println();
        System.out.println(
                "========================================"
        );

        System.out.println(
                "MULTI-SOURCE RSS FETCH"
        );

        System.out.println(
                "Section: "
                        + section.getDisplayName()
        );

        System.out.println(
                "Usable RSS sources: "
                        + sources.size()
        );


        for (NewsSource source :
                sources) {

            try {

                System.out.println();
                System.out.println(
                        "Processing RSS source: "
                                + source.getName()
                );


                List<RssNewsFetcherService.RssArticle>
                        articles =
                        rssNewsFetcherService
                                .fetchFeed(
                                        source
                                );


                if (articles != null &&
                        !articles.isEmpty()) {

                    results.addAll(
                            articles
                    );
                }


            } catch (Exception e) {

                System.out.println(
                        "RSS source failed: "
                                + source.getName()
                );

                System.out.println(
                        "Error: "
                                + e.getMessage()
                );
            }
        }


        System.out.println();
        System.out.println(
                "TOTAL RSS ARTICLES: "
                        + results.size()
        );

        System.out.println(
                "========================================"
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


        for (NewsSection section :
                NewsSection.values()) {

            results.addAll(
                    fetchRssForSection(
                            section
                    )
            );
        }


        return results;
    }


    // =====================================================
    // GET USABLE SOURCES
    // =====================================================

    public List<NewsSource> getUsableSources(
            NewsSection section) {

        return sourceRegistry
                .getUsableSources(
                        section
                );
    }


    // =====================================================
    // GET RSS SOURCES
    // =====================================================

    public List<NewsSource> getUsableRssSources(
            NewsSection section) {

        return sourceRegistry
                .getUsableRssSources(
                        section
                );
    }


    // =====================================================
    // SOURCE STATUS
    // =====================================================

    public String getSourceStatus() {

        StringBuilder result =
                new StringBuilder();


        result.append(
                "AGNIPRESS SOURCE STATUS\n"
        );


        for (NewsSection section :
                NewsSection.values()) {

            List<NewsSource> sources =
                    sourceRegistry
                            .getSources(
                                    section
                            );


            result.append(
                    "\n"
                            + section.getDisplayName()
                            + ":\n"
            );


            if (sources.isEmpty()) {

                result.append(
                        "  No sources configured.\n"
                );

                continue;
            }


            for (NewsSource source :
                    sources) {

                result.append(
                        "  - "
                                + source.getName()
                                + " | "
                                + source.getProviderType()
                                + " | enabled="
                                + source.isEnabled()
                                + " | usable="
                                + source.isUsable()
                                + "\n"
                );
            }
        }


        return result.toString();
    }
}