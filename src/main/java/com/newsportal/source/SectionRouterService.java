package com.newsportal.source;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SectionRouterService {

    private final NewsSourceRegistry sourceRegistry;


    // =====================================================
    // CONSTRUCTOR
    // =====================================================

    public SectionRouterService(
            NewsSourceRegistry sourceRegistry) {

        this.sourceRegistry =
                sourceRegistry;
    }


    // =====================================================
    // GET ALL SOURCES FOR SECTION
    // =====================================================

    public List<NewsSource> getSources(
            NewsSection section) {

        return sourceRegistry.getSources(
                section
        );
    }


    // =====================================================
    // GET USABLE SOURCES
    // =====================================================

    public List<NewsSource> getUsableSources(
            NewsSection section) {

        return sourceRegistry.getUsableSources(
                section
        );
    }


    // =====================================================
    // GET IMAGE-CAPABLE SOURCES
    // =====================================================

    public List<NewsSource> getImageCapableSources(
            NewsSection section) {

        return sourceRegistry
                .getImageCapableSources(
                        section
                );
    }


    // =====================================================
    // GET PRIMARY SOURCE
    // =====================================================

    public NewsSource getPrimarySource(
            NewsSection section) {

        return sourceRegistry.getPrimarySource(
                section
        );
    }


    // =====================================================
    // CHECK SOURCE
    // =====================================================

    public boolean hasSource(
            NewsSection section) {

        return sourceRegistry.hasUsableSource(
                section
        );
    }


    // =====================================================
    // CHECK IMAGE SOURCE
    // =====================================================

    public boolean hasImageSource(
            NewsSection section) {

        return sourceRegistry
                .hasImageCapableSource(
                        section
                );
    }


    // =====================================================
    // RESOLVE SECTION
    // =====================================================

    public NewsSection resolveSection(
            String category) {

        if (category == null ||
                category.isBlank()) {

            return NewsSection.WORLD;
        }


        String normalized =
                category
                        .trim()
                        .toLowerCase();


        switch (normalized) {

            case "india":
                return NewsSection.INDIA;

            case "world":
            case "general":
            case "international":
                return NewsSection.WORLD;

            case "sports":
            case "sport":
                return NewsSection.SPORTS;

            case "anime":
            case "manga":
                return NewsSection.ANIME;

            case "business":
            case "finance":
            case "economy":
                return NewsSection.BUSINESS;

            case "technology":
            case "tech":
            case "technology news":
                return NewsSection.TECHNOLOGY;

            case "entertainment":
            case "movies":
            case "music":
            case "celebrity":
                return NewsSection.ENTERTAINMENT;

            case "science":
            case "scientific":
                return NewsSection.SCIENCE;

            case "gaming":
            case "games":
            case "video games":
                return NewsSection.GAMING;

            default:
                return NewsSection.WORLD;
        }
    }
}