package com.newsportal.source;

/**
 * Sections available in AgniPress.
 *
 * These sections belong to AgniPress itself and are intentionally
 * independent from any particular news provider's category system.
 */
public enum NewsSection {

    INDIA("India"),

    WORLD("World"),

    SPORTS("Sports"),

    ANIME("Anime"),

    BUSINESS("Business"),

    TECHNOLOGY("Technology"),

    ENTERTAINMENT("Entertainment"),

    SCIENCE("Science"),

    GAMING("Gaming");


    private final String displayName;


    NewsSection(String displayName) {
        this.displayName = displayName;
    }


    public String getDisplayName() {
        return displayName;
    }


    @Override
    public String toString() {
        return displayName;
    }
}