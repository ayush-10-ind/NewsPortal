package com.newsportal.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.newsportal.model.LiveChannel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class LiveChannelService {

    private static final Logger logger =
            LoggerFactory.getLogger(LiveChannelService.class);

    @Value("${youtube.api.key:}")
    private String youtubeApiKey;

    private static final long CACHE_DURATION_MINUTES = 30;
    private static final long QUOTA_COOLDOWN_MINUTES = 60;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    private volatile List<LiveChannel> cachedChannels;
    private volatile LocalDateTime lastChecked;
    private volatile LocalDateTime quotaExceededAt;

    private final AtomicBoolean refreshInProgress =
            new AtomicBoolean(false);

    public LiveChannelService(ObjectMapper objectMapper) {

        this.restClient = RestClient.builder()
                .baseUrl("https://www.googleapis.com")
                .build();

        this.objectMapper = objectMapper;
        this.cachedChannels = getConfiguredChannels();
        this.lastChecked = LocalDateTime.now().minusMinutes(
                CACHE_DURATION_MINUTES + 1
        );
    }

    private List<LiveChannel> getConfiguredChannels() {

        List<LiveChannel> channels = new ArrayList<>();

        channels.add(new LiveChannel(
                "ABP News",
                "Live news coverage from ABP News.",
                "Hindi news and current affairs from India and around the world.",
                "UCRWFSbif-RFENbBrSiez1DA"
        ));

        channels.add(new LiveChannel(
                "Aaj Tak",
                "Live Hindi news coverage from Aaj Tak.",
                "Breaking news and Hindi live television coverage.",
                "UCt4t-jeY85JegMlZ-E5UWtA"
        ));

        channels.add(new LiveChannel(
                "India TV",
                "24x7 Hindi news coverage from India TV.",
                "24x7 Hindi news, politics, sports, business and world news.",
                "UCttspZesZIDEwwpVIgoZtWQ"
        ));

        channels.add(new LiveChannel(
                "NDTV",
                "Live news and current affairs from NDTV.",
                "English news, live coverage and current affairs.",
                "UCZFMm1mMw0F81Z37aaEzTUA"
        ));

        channels.add(new LiveChannel(
                "CNBC-TV18",
                "Business and financial news from CNBC-TV18.",
                "Business, economy, markets, Sensex and Nifty coverage.",
                "UCmRbHAgG2k2vDUvb3xsEunQ"
        ));

        return channels;
    }

    public List<LiveChannel> getLiveChannels() {

        List<LiveChannel> result = copyChannels(cachedChannels);

        if (isCacheExpired()) {
            startBackgroundRefresh();
        }

        return result;
    }

    private boolean isCacheExpired() {

        if (lastChecked == null) {
            return true;
        }

        return lastChecked
                .plusMinutes(CACHE_DURATION_MINUTES)
                .isBefore(LocalDateTime.now());
    }

    private boolean isQuotaCooldownActive() {

        if (quotaExceededAt == null) {
            return false;
        }

        LocalDateTime retryTime =
                quotaExceededAt.plusMinutes(
                        QUOTA_COOLDOWN_MINUTES
                );

        if (LocalDateTime.now().isAfter(retryTime)) {

            quotaExceededAt = null;

            logger.info("YouTube quota cooldown finished; live status checks resumed");
            return false;
        }

        return true;
    }

    private void startBackgroundRefresh() {

        if (!refreshInProgress.compareAndSet(false, true)) {
            return;
        }

        CompletableFuture.runAsync(() -> {

            try {
                refreshLiveStatus();
            } finally {
                refreshInProgress.set(false);
            }
        });
    }

    @Scheduled(fixedDelay = 30 * 60 * 1000)
    public void scheduledRefresh() {
        startBackgroundRefresh();
    }

    private void refreshLiveStatus() {

        if (youtubeApiKey == null || youtubeApiKey.isBlank()) {

            logger.warn("YouTube API key is not configured");
            return;
        }

        if (isQuotaCooldownActive()) {

            logger.debug("YouTube quota cooldown active; using cached live status");
            return;
        }

        boolean successfulCheck = false;

        for (LiveChannel channel : cachedChannels) {

            try {

                boolean success = checkLiveStatus(channel);

                if (success) {
                    successfulCheck = true;
                }

            } catch (YouTubeQuotaException e) {

                quotaExceededAt = LocalDateTime.now();

                logger.warn(
                        "YouTube quota exceeded; keeping previous cached status"
                );

                break;

            } catch (Exception e) {

                logger.warn(
                        "YouTube live check failed: channel={}, errorType={}, message={}",
                        channel.getName(),
                        e.getClass().getSimpleName(),
                        e.getMessage()
                );
            }
        }

        if (successfulCheck) {

            lastChecked = LocalDateTime.now();

            for (LiveChannel channel : cachedChannels) {

                channel.setCached(true);
                channel.setLastChecked(lastChecked);
            }

            logger.info("YouTube live status cache updated");
        }
    }

    private boolean checkLiveStatus(LiveChannel channel) {

        String response;

        try {

            response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/youtube/v3/search")
                            .queryParam("part", "snippet")
                            .queryParam("channelId", channel.getChannelId())
                            .queryParam("eventType", "live")
                            .queryParam("type", "video")
                            .queryParam("videoEmbeddable", "true")
                            .queryParam("videoSyndicated", "true")
                            .queryParam("maxResults", "1")
                            .queryParam("key", youtubeApiKey)
                            .build())
                    .retrieve()
                    .body(String.class);

        } catch (Exception e) {

            String message = e.getMessage();

            if (message != null && (
                    message.contains("429") ||
                    message.contains("quotaExceeded") ||
                    message.contains("Quota exceeded") ||
                    message.contains("quota exceeded")
            )) {

                throw new YouTubeQuotaException(
                        "YouTube API quota exceeded."
                );
            }

            throw e;
        }

        if (response == null || response.isBlank()) {
            return false;
        }

        JsonNode root;

        try {
            root = objectMapper.readTree(response);
        } catch (Exception e) {
            return false;
        }

        JsonNode error = root.path("error");

        if (!error.isMissingNode() && !error.isNull()) {

            String reason = error
                    .path("errors")
                    .path(0)
                    .path("reason")
                    .asText("");

            if (reason.equalsIgnoreCase("quotaExceeded")) {
                throw new YouTubeQuotaException(
                        "YouTube API quota exceeded."
                );
            }

            return false;
        }

        JsonNode items = root.path("items");

        if (!items.isArray() || items.isEmpty()) {
            markOffline(channel);
            return true;
        }

        JsonNode firstItem = items.get(0);
        JsonNode id = firstItem.path("id");

        String videoId = id.path("videoId").asText("");

        if (videoId.isBlank()) {
            markOffline(channel);
            return true;
        }

        channel.setLive(true);
        channel.setVideoId(videoId);
        channel.setCached(true);
        channel.setLastChecked(LocalDateTime.now());

        logger.debug(
                "Live stream found: channel={}, videoId={}",
                channel.getName(),
                videoId
        );

        return true;
    }

    private void markOffline(LiveChannel channel) {

        channel.setLive(false);
        channel.setVideoId(null);
        channel.setCached(true);
        channel.setLastChecked(LocalDateTime.now());
    }

    private List<LiveChannel> copyChannels(List<LiveChannel> source) {

        List<LiveChannel> result = new ArrayList<>();

        for (LiveChannel channel : source) {

            LiveChannel copy = new LiveChannel(
                    channel.getName(),
                    channel.getDescription(),
                    channel.getShortDescription(),
                    channel.getChannelId()
            );

            copy.setLive(channel.isLive());
            copy.setVideoId(channel.getVideoId());
            copy.setYoutubeChannelUrl(channel.getYoutubeChannelUrl());
            copy.setCached(channel.isCached());
            copy.setLastChecked(channel.getLastChecked());

            result.add(copy);
        }

        return result;
    }

    private static class YouTubeQuotaException
            extends RuntimeException {

        public YouTubeQuotaException(String message) {
            super(message);
        }
    }
}
