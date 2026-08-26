package com.newsportal.model;

import java.time.LocalDateTime;

public class LiveChannel {

    private String name;
    private String description;
    private String shortDescription;
    private String channelId;

    private boolean live;
    private String videoId;

    private String youtubeChannelUrl;

    private boolean cached;
    private LocalDateTime lastChecked;


    // =====================================================
    // DEFAULT CONSTRUCTOR
    // =====================================================

    public LiveChannel() {
    }


    // =====================================================
    // CONSTRUCTOR
    // =====================================================

    public LiveChannel(
            String name,
            String description,
            String shortDescription,
            String channelId
    ) {

        this.name = name;
        this.description = description;
        this.shortDescription = shortDescription;
        this.channelId = channelId;

        this.live = false;
        this.videoId = null;

        this.youtubeChannelUrl =
                "https://www.youtube.com/channel/" + channelId + "/live";

        this.cached = false;
        this.lastChecked = null;
    }


    // =====================================================
    // GETTERS / SETTERS
    // =====================================================

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }


    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }


    public String getShortDescription() {
        return shortDescription;
    }

    public void setShortDescription(String shortDescription) {
        this.shortDescription = shortDescription;
    }


    public String getChannelId() {
        return channelId;
    }

    public void setChannelId(String channelId) {
        this.channelId = channelId;

        if (channelId != null && !channelId.isBlank()) {

            this.youtubeChannelUrl =
                    "https://www.youtube.com/channel/"
                            + channelId
                            + "/live";
        }
    }


    public boolean isLive() {
        return live;
    }

    public void setLive(boolean live) {
        this.live = live;
    }


    public String getVideoId() {
        return videoId;
    }

    public void setVideoId(String videoId) {
        this.videoId = videoId;
    }


    public String getYoutubeChannelUrl() {
        return youtubeChannelUrl;
    }

    public void setYoutubeChannelUrl(String youtubeChannelUrl) {
        this.youtubeChannelUrl = youtubeChannelUrl;
    }


    public boolean isCached() {
        return cached;
    }

    public void setCached(boolean cached) {
        this.cached = cached;
    }


    public LocalDateTime getLastChecked() {
        return lastChecked;
    }

    public void setLastChecked(LocalDateTime lastChecked) {
        this.lastChecked = lastChecked;
    }
}