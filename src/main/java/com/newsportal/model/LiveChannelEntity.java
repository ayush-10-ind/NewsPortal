package com.newsportal.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "live_channels")
public class LiveChannelEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    @Column(nullable = false, unique = true)
    private String channelId;


    @Column(nullable = false)
    private String name;


    @Column(length = 1000)
    private String description;


    @Column(length = 1000)
    private String shortDescription;


    private boolean live;


    private String videoId;


    @Column(length = 500)
    private String youtubeChannelUrl;


    private LocalDateTime lastChecked;


    public LiveChannelEntity() {

    }


    public LiveChannelEntity(
            String channelId,
            String name,
            String description,
            String shortDescription,
            String youtubeChannelUrl
    ) {

        this.channelId =
                channelId;

        this.name =
                name;

        this.description =
                description;

        this.shortDescription =
                shortDescription;

        this.youtubeChannelUrl =
                youtubeChannelUrl;

        this.live = false;

        this.videoId = null;

        this.lastChecked = null;
    }


    public Long getId() {
        return id;
    }


    public String getChannelId() {
        return channelId;
    }


    public void setChannelId(
            String channelId
    ) {

        this.channelId =
                channelId;
    }


    public String getName() {
        return name;
    }


    public void setName(String name) {
        this.name = name;
    }


    public String getDescription() {
        return description;
    }


    public void setDescription(
            String description
    ) {

        this.description =
                description;
    }


    public String getShortDescription() {
        return shortDescription;
    }


    public void setShortDescription(
            String shortDescription
    ) {

        this.shortDescription =
                shortDescription;
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


    public void setYoutubeChannelUrl(
            String youtubeChannelUrl
    ) {

        this.youtubeChannelUrl =
                youtubeChannelUrl;
    }


    public LocalDateTime getLastChecked() {
        return lastChecked;
    }


    public void setLastChecked(
            LocalDateTime lastChecked
    ) {

        this.lastChecked =
                lastChecked;
    }
}