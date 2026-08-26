package com.newsportal.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.newsportal.model.LiveChannel;

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

    // =====================================================
    // YOUTUBE API KEY
    // =====================================================

    @Value("${youtube.api.key:}")
    private String youtubeApiKey;

    // =====================================================
    // CACHE SETTINGS
    // =====================================================

    /*
     * Users never call YouTube directly.
     *
     * The application uses the cached result.
     */
    private static final long CACHE_DURATION_MINUTES = 30;

    /*
     * If YouTube says quota exceeded, wait before trying
     * again instead of continuously making requests.
     */
    private static final long QUOTA_COOLDOWN_MINUTES = 60;

    // =====================================================
    // HTTP CLIENT
    // =====================================================

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    // =====================================================
    // CACHE
    // =====================================================

    private volatile List<LiveChannel> cachedChannels;

    private volatile LocalDateTime lastChecked;

    private volatile LocalDateTime quotaExceededAt;

    private final AtomicBoolean refreshInProgress =
            new AtomicBoolean(false);

    // =====================================================
    // CONSTRUCTOR
    // =====================================================

    public LiveChannelService(ObjectMapper objectMapper) {

        this.restClient = RestClient.builder()
                .baseUrl("https://www.googleapis.com")
                .build();

        this.objectMapper = objectMapper;

        /*
         * Create the channels immediately.
         *
         * The browser never has to wait for YouTube.
         */
        this.cachedChannels = getConfiguredChannels();

        /*
         * Start with an old timestamp so that the first
         * request triggers a background refresh.
         */
        this.lastChecked =
                LocalDateTime.now().minusMinutes(
                        CACHE_DURATION_MINUTES + 1
                );
    }

    // =====================================================
    // CONFIGURED CHANNELS
    // =====================================================

    private List<LiveChannel> getConfiguredChannels() {

        List<LiveChannel> channels =
                new ArrayList<>();

        channels.add(
                new LiveChannel(
                        "ABP News",
                        "Live news coverage from ABP News.",
                        "Hindi news and current affairs from India and around the world.",
                        "UCRWFSbif-RFENbBrSiez1DA"
                )
        );

        channels.add(
                new LiveChannel(
                        "Aaj Tak",
                        "Live Hindi news coverage from Aaj Tak.",
                        "Breaking news and Hindi live television coverage.",
                        "UCt4t-jeY85JegMlZ-E5UWtA"
                )
        );

        channels.add(
                new LiveChannel(
                        "India TV",
                        "24x7 Hindi news coverage from India TV.",
                        "24x7 Hindi news, politics, sports, business and world news.",
                        "UCttspZesZIDEwwpVIgoZtWQ"
                )
        );

        channels.add(
                new LiveChannel(
                        "NDTV",
                        "Live news and current affairs from NDTV.",
                        "English news, live coverage and current affairs.",
                        "UCZFMm1mMw0F81Z37aaEzTUA"
                )
        );

        channels.add(
                new LiveChannel(
                        "CNBC-TV18",
                        "Business and financial news from CNBC-TV18.",
                        "Business, economy, markets, Sensex and Nifty coverage.",
                        "UCmRbHAgG2k2vDUvb3xsEunQ"
                )
        );

        return channels;
    }

    // =====================================================
    // PUBLIC API
    // =====================================================

    public List<LiveChannel> getLiveChannels() {

        /*
         * IMPORTANT:
         *
         * Never call YouTube synchronously from the browser
         * request.
         */
        List<LiveChannel> result =
                copyChannels(cachedChannels);

        /*
         * If cache is old, refresh in background.
         */
        if (isCacheExpired()) {
            startBackgroundRefresh();
        }

        return result;
    }

    // =====================================================
    // CACHE EXPIRY
    // =====================================================

    private boolean isCacheExpired() {

        if (lastChecked == null) {
            return true;
        }

        return lastChecked
                .plusMinutes(CACHE_DURATION_MINUTES)
                .isBefore(LocalDateTime.now());
    }

    // =====================================================
    // QUOTA COOLDOWN
    // =====================================================

    private boolean isQuotaCooldownActive() {

        if (quotaExceededAt == null) {
            return false;
        }

        LocalDateTime retryTime =
                quotaExceededAt.plusMinutes(
                        QUOTA_COOLDOWN_MINUTES
                );

        /*
         * Cooldown finished.
         */
        if (LocalDateTime.now().isAfter(retryTime)) {

            quotaExceededAt = null;

            System.out.println(
                    "YouTube quota cooldown finished. "
                    + "Live status checking will resume."
            );

            return false;
        }

        return true;
    }

    // =====================================================
    // BACKGROUND REFRESH
    // =====================================================

    private void startBackgroundRefresh() {

        /*
         * Prevent multiple simultaneous refreshes.
         */
        if (!refreshInProgress.compareAndSet(
                false,
                true
        )) {
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

    // =====================================================
    // SCHEDULED REFRESH
    // =====================================================

    /*
     * Refresh every 30 minutes.
     *
     * This does NOT block browser requests.
     */
    @Scheduled(fixedDelay = 30 * 60 * 1000)
    public void scheduledRefresh() {

        startBackgroundRefresh();
    }

    // =====================================================
    // REFRESH LIVE STATUS
    // =====================================================

    private void refreshLiveStatus() {

        /*
         * No API key.
         */
        if (youtubeApiKey == null ||
                youtubeApiKey.isBlank()) {

            System.err.println(
                    "YouTube API key is not configured."
            );

            return;
        }

        /*
         * Don't waste quota during cooldown.
         */
        if (isQuotaCooldownActive()) {

            System.out.println(
                    "YouTube quota cooldown active. "
                    + "Using cached live status."
            );

            return;
        }

        boolean successfulCheck = false;

        /*
         * Check channels one by one.
         */
        for (LiveChannel channel : cachedChannels) {

            try {

                boolean success =
                        checkLiveStatus(channel);

                if (success) {
                    successfulCheck = true;
                }

            } catch (YouTubeQuotaException e) {

                /*
                 * IMPORTANT:
                 *
                 * Stop immediately when quota is exceeded.
                 */
                quotaExceededAt =
                        LocalDateTime.now();

                System.err.println(
                        "YouTube quota exceeded. "
                        + "Keeping previous cached status."
                );

                break;

            } catch (Exception e) {

                /*
                 * Do NOT destroy a previously known live
                 * stream because of a temporary API error.
                 */
                System.err.println(
                        "YouTube live check failed for "
                                + channel.getName()
                                + ": "
                                + e.getMessage()
                );
            }
        }

        /*
         * Only update global timestamp if at least one
         * request actually succeeded.
         */
        if (successfulCheck) {

            lastChecked =
                    LocalDateTime.now();

            for (LiveChannel channel :
                    cachedChannels) {

                channel.setCached(true);

                channel.setLastChecked(
                        lastChecked
                );
            }

            System.out.println(
                    "YouTube live status cache updated."
            );
        }
    }

    // =====================================================
    // CHECK ONE CHANNEL
    // =====================================================

    private boolean checkLiveStatus(
            LiveChannel channel
    ) {

        String response;

        try {

            response =
                    restClient.get()
                            .uri(
                                    uriBuilder ->
                                            uriBuilder
                                                    .path(
                                                            "/youtube/v3/search"
                                                    )
                                                    .queryParam(
                                                            "part",
                                                            "snippet"
                                                    )
                                                    .queryParam(
                                                            "channelId",
                                                            channel.getChannelId()
                                                    )
                                                    .queryParam(
                                                            "eventType",
                                                            "live"
                                                    )
                                                    .queryParam(
                                                            "type",
                                                            "video"
                                                    )
                                                    .queryParam(
                                                            "videoEmbeddable",
                                                            "true"
                                                    )
                                                    .queryParam(
                                                            "videoSyndicated",
                                                            "true"
                                                    )
                                                    .queryParam(
                                                            "maxResults",
                                                            "1"
                                                    )
                                                    .queryParam(
                                                            "key",
                                                            youtubeApiKey
                                                    )
                                                    .build()
                            )
                            .retrieve()
                            .body(String.class);

        } catch (Exception e) {

            String message =
                    e.getMessage();

            if (message != null &&
                    (
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

        if (response == null ||
                response.isBlank()) {

            return false;
        }

        JsonNode root;

        try {

            root =
                    objectMapper.readTree(
                            response
                    );

        } catch (Exception e) {

            return false;
        }

        // =================================================
        // YOUTUBE ERROR
        // =================================================

        JsonNode error =
                root.path("error");

        if (!error.isMissingNode() &&
                !error.isNull()) {

            String reason =
                    error
                            .path("errors")
                            .path(0)
                            .path("reason")
                            .asText("");

            if (reason.equalsIgnoreCase(
                    "quotaExceeded"
            )) {

                throw new YouTubeQuotaException(
                        "YouTube API quota exceeded."
                );
            }

            return false;
        }

        // =================================================
        // LIVE VIDEOS
        // =================================================

        JsonNode items =
                root.path("items");

        /*
         * No live video currently found.
         */
        if (!items.isArray() ||
                items.isEmpty()) {

            markOffline(channel);

            return true;
        }

        JsonNode firstItem =
                items.get(0);

        JsonNode id =
                firstItem.path("id");

        String videoId =
                id.path("videoId")
                        .asText("");

        /*
         * Invalid result.
         */
        if (videoId.isBlank()) {

            markOffline(channel);

            return true;
        }

        // =================================================
        // LIVE FOUND
        // =================================================

        channel.setLive(true);

        channel.setVideoId(videoId);

        channel.setCached(true);

        channel.setLastChecked(
                LocalDateTime.now()
        );

        System.out.println(
                "LIVE FOUND: "
                        + channel.getName()
                        + " -> "
                        + videoId
        );

        return true;
    }

    // =====================================================
    // OFFLINE
    // =====================================================

    private void markOffline(
            LiveChannel channel
    ) {

        channel.setLive(false);

        channel.setVideoId(null);

        channel.setCached(true);

        channel.setLastChecked(
                LocalDateTime.now()
        );
    }

    // =====================================================
    // COPY CHANNELS
    // =====================================================

    private List<LiveChannel> copyChannels(
            List<LiveChannel> source
    ) {

        List<LiveChannel> result =
                new ArrayList<>();

        for (LiveChannel channel : source) {

            LiveChannel copy =
                    new LiveChannel(
                            channel.getName(),
                            channel.getDescription(),
                            channel.getShortDescription(),
                            channel.getChannelId()
                    );

            copy.setLive(
                    channel.isLive()
            );

            copy.setVideoId(
                    channel.getVideoId()
            );

            copy.setYoutubeChannelUrl(
                    channel.getYoutubeChannelUrl()
            );

            copy.setCached(
                    channel.isCached()
            );

            copy.setLastChecked(
                    channel.getLastChecked()
            );

            result.add(copy);
        }

        return result;
    }

    // =====================================================
    // QUOTA EXCEPTION
    // =====================================================

    private static class YouTubeQuotaException
            extends RuntimeException {

        public YouTubeQuotaException(
                String message
        ) {

            super(message);
        }
    }
}