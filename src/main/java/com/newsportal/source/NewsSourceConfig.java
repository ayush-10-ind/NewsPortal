package com.newsportal.source;

/**
 * Factory/helper class used to create NewsSource definitions.
 *
 * Actual source choices will be added after we verify
 * their APIs/feeds and image usage permissions.
 */
public final class NewsSourceConfig {

    private NewsSourceConfig() {
        // Utility class.
    }


    public static NewsSource create(
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

        return new NewsSource(
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
    }
}