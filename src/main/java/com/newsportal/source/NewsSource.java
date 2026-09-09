package com.newsportal.source;

/**
 * Describes a news source that AgniPress can use.
 *
 * This class does not perform API calls.
 * It only describes the source and its capabilities.
 */
public class NewsSource {

    private final String name;

    private final NewsSection section;

    private final int priority;

    private final String providerType;

    private final String sourceId;

    private final String endpoint;

    private final boolean newsAllowed;

    private final boolean imageAllowed;

    private final boolean automatedAccessAllowed;

    private final boolean enabled;


    public NewsSource(
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

        this.name = name;
        this.section = section;
        this.priority = priority;
        this.providerType = providerType;
        this.sourceId = sourceId;
        this.endpoint = endpoint;
        this.newsAllowed = newsAllowed;
        this.imageAllowed = imageAllowed;
        this.automatedAccessAllowed =
                automatedAccessAllowed;
        this.enabled = enabled;
    }


    public String getName() {
        return name;
    }


    public NewsSection getSection() {
        return section;
    }


    public int getPriority() {
        return priority;
    }


    public String getProviderType() {
        return providerType;
    }


    public String getSourceId() {
        return sourceId;
    }


    public String getEndpoint() {
        return endpoint;
    }


    public boolean isNewsAllowed() {
        return newsAllowed;
    }


    public boolean isImageAllowed() {
        return imageAllowed;
    }


    public boolean isAutomatedAccessAllowed() {
        return automatedAccessAllowed;
    }


    public boolean isEnabled() {
        return enabled;
    }


    /**
     * Returns true only when the source satisfies the
     * minimum requirements for AgniPress automated use.
     */
    public boolean isUsable() {

        return enabled
                && newsAllowed
                && automatedAccessAllowed;
    }


    /**
     * Returns true when the source can provide both
     * news and images under the configured permissions.
     */
    public boolean supportsNewsAndImages() {

        return isUsable()
                && imageAllowed;
    }


    @Override
    public String toString() {

        return "NewsSource{" +
                "name='" + name + '\'' +
                ", section=" + section +
                ", priority=" + priority +
                ", providerType='" + providerType + '\'' +
                ", sourceId='" + sourceId + '\'' +
                ", endpoint='" + endpoint + '\'' +
                ", newsAllowed=" + newsAllowed +
                ", imageAllowed=" + imageAllowed +
                ", automatedAccessAllowed=" +
                automatedAccessAllowed +
                ", enabled=" + enabled +
                '}';
    }
}