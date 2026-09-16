package com.newsportal.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.Duration;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;

@Service
public class NasaApodImageService {

    private static final Logger logger = LoggerFactory.getLogger(NasaApodImageService.class);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(8);
    private static final String APOD_API = "https://api.nasa.gov/planetary/apod";

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final String nasaApiKey;

    public NasaApodImageService(
            WebClient.Builder webClientBuilder,
            ObjectMapper objectMapper,
            @Value("${nasa.api-key:}") String nasaApiKey) {
        this.webClient = webClientBuilder.build();
        this.objectMapper = objectMapper;
        this.nasaApiKey = nasaApiKey == null ? "" : nasaApiKey.trim();
    }

    public String resolveImage(String sourceUrl, String title, LocalDate publishedDate) {
        if (!isNasaArticle(sourceUrl)) {
            return null;
        }

        String pageImage = resolveNasaPageImage(sourceUrl, title);
        if (isUsableUrl(pageImage)) {
            return pageImage;
        }

        String libraryImage = resolveFromNasaImagesApi(title);
        if (isUsableUrl(libraryImage)) {
            return libraryImage;
        }

        if (isApodArticle(title) && publishedDate != null && hasUsableApiKey()) {
            return resolveApodImage(publishedDate, title);
        }

        return null;
    }

    private String resolveFromNasaImagesApi(String title) {
        String query = buildImageSearchQuery(title);
        if (query.isBlank()) {
            return null;
        }

        try {
            String response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .scheme("https")
                            .host("images-api.nasa.gov")
                            .path("/search")
                            .queryParam("q", query)
                            .queryParam("media_type", "image")
                            .queryParam("page_size", 10)
                            .build())
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(REQUEST_TIMEOUT)
                    .onErrorResume(error -> Mono.empty())
                    .block();

            if (response == null || response.isBlank()) {
                return null;
            }

            JsonNode items = objectMapper.readTree(response)
                    .path("collection")
                    .path("items");

            if (!items.isArray() || items.isEmpty()) {
                return null;
            }

            String bestImage = null;
            int bestScore = Integer.MIN_VALUE;

            for (JsonNode item : items) {
                JsonNode data = item.path("data");
                if (!data.isArray() || data.isEmpty()) {
                    continue;
                }

                String resultTitle = data.get(0).path("title").asText("");
                String preview = extractPreviewImage(item);
                if (!isUsableUrl(preview)) {
                    continue;
                }

                int score = scoreSearchResult(query, resultTitle);
                score += 5;

                if (score > bestScore) {
                    bestScore = score;
                    bestImage = preview;
                }
            }

            if (isUsableUrl(bestImage)) {
                logger.info("NASA Images API preview resolved: title={}", title);
                return bestImage;
            }
        } catch (Exception ex) {
            logger.debug("NASA Images API lookup failed: title={}, errorType={}, message={}",
                    title, ex.getClass().getSimpleName(), ex.getMessage());
        }

        return null;
    }

    private String extractPreviewImage(JsonNode item) {
        JsonNode links = item.path("links");
        if (!links.isArray()) {
            return null;
        }

        for (JsonNode link : links) {
            String href = link.path("href").asText("");
            String rel = link.path("rel").asText("");
            if ("preview".equalsIgnoreCase(rel) && isUsableUrl(href)) {
                return href;
            }
        }

        for (JsonNode link : links) {
            String href = link.path("href").asText("");
            if (isUsableUrl(href) && looksLikeImageAsset(href)) {
                return href;
            }
        }

        return null;
    }

    private int scoreSearchResult(String query, String resultTitle) {
        Set<String> queryTokens = tokenize(query);
        Set<String> resultTokens = tokenize(resultTitle);

        int score = 0;
        for (String token : queryTokens) {
            if (resultTokens.contains(token)) {
                score += 10;
            }
        }

        if (!resultTitle.isBlank() && query.equalsIgnoreCase(resultTitle.trim())) {
            score += 100;
        }

        return score;
    }

    private Set<String> tokenize(String value) {
        Set<String> tokens = new HashSet<>();
        if (value == null) {
            return tokens;
        }

        String normalized = value
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .trim();

        if (normalized.isBlank()) {
            return tokens;
        }

        for (String token : normalized.split("\\s+")) {
            if (token.length() >= 3) {
                tokens.add(token);
            }
        }

        return tokens;
    }

    private String buildImageSearchQuery(String title) {
        if (title == null) {
            return "";
        }

        String normalized = title
                .replaceFirst("(?i)^apod\\s*:\\s*", "")
                .replaceAll("(?i)\\b20\\d{2}[-/]\\d{1,2}[-/]\\d{1,2}\\b", " ")
                .replaceAll("(?i)\\b20\\d{2}\\s+[A-Za-z]+\\s+\\d{1,2}\\b", " ")
                .replaceAll("[^A-Za-z0-9]+", " ")
                .trim();

        if (normalized.length() > 100) {
            normalized = normalized.substring(0, 100).trim();
        }

        return normalized;
    }

    private String resolveApodImage(LocalDate publishedDate, String title) {
        try {
            String response = webClient.get()
                    .uri(APOD_API + "?api_key=" + nasaApiKey + "&date=" + publishedDate)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(REQUEST_TIMEOUT)
                    .onErrorResume(error -> Mono.empty())
                    .block();

            if (response == null || response.isBlank()) {
                return null;
            }

            JsonNode root = objectMapper.readTree(response);
            if (!"image".equalsIgnoreCase(root.path("media_type").asText(""))) {
                return null;
            }

            String hdUrl = root.path("hdurl").asText("");
            String standardUrl = root.path("url").asText("");
            String resolved = isUsableUrl(hdUrl) ? hdUrl : standardUrl;

            if (isUsableUrl(resolved)) {
                logger.info("NASA APOD API image resolved: date={}, title={}", publishedDate, title);
                return resolved;
            }
        } catch (Exception ex) {
            logger.debug("NASA APOD API lookup failed: date={}, errorType={}, message={}",
                    publishedDate, ex.getClass().getSimpleName(), ex.getMessage());
        }

        return null;
    }

    private String resolveNasaPageImage(String sourceUrl, String title) {
        try {
            String html = webClient.get()
                    .uri(URI.create(sourceUrl.trim()))
                    .headers(headers -> {
                        headers.set("User-Agent", "Mozilla/5.0 (compatible; AgniPress/1.0)");
                        headers.set("Accept", "text/html,application/xhtml+xml");
                    })
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(REQUEST_TIMEOUT)
                    .onErrorResume(error -> Mono.empty())
                    .block();

            if (html == null || html.isBlank()) {
                return null;
            }

            Document document = Jsoup.parse(html, sourceUrl);

            String[] selectors = {
                    "meta[property=og:image]",
                    "meta[property=og:image:url]",
                    "meta[property=og:image:secure_url]",
                    "meta[name=twitter:image]",
                    "meta[name=twitter:image:src]"
            };

            for (String selector : selectors) {
                Element element = document.selectFirst(selector);
                if (element == null) {
                    continue;
                }

                String imageUrl = normalizeUrl(element.attr("content"), sourceUrl);
                if (isUsableUrl(imageUrl)) {
                    logger.info("NASA page image resolved: title={}, host={}", title, hostOf(imageUrl));
                    return imageUrl;
                }
            }

            String jsonLdImage = findJsonLdImage(document, sourceUrl);
            if (isUsableUrl(jsonLdImage)) {
                return jsonLdImage;
            }

            for (Element image : document.select("article img[src], main img[src], img[data-src], img[data-lazy-src], img[data-original]")) {
                for (String attribute : new String[]{"src", "data-src", "data-lazy-src", "data-original"}) {
                    String imageUrl = normalizeUrl(image.attr(attribute), sourceUrl);
                    if (isUsableUrl(imageUrl) && !looksLikeNonArticleAsset(imageUrl)) {
                        return imageUrl;
                    }
                }
            }
        } catch (Exception ex) {
            logger.debug("NASA page lookup failed: url={}, errorType={}, message={}",
                    sourceUrl, ex.getClass().getSimpleName(), ex.getMessage());
        }

        return null;
    }

    private String findJsonLdImage(Document document, String sourceUrl) {
        for (Element script : document.select("script[type=application/ld+json]")) {
            String json = script.data();
            if (json == null || json.isBlank()) {
                json = script.html();
            }
            if (json == null || json.isBlank()) {
                continue;
            }

            try {
                JsonNode root = objectMapper.readTree(json);
                String result = findImageNode(root, sourceUrl);
                if (isUsableUrl(result)) {
                    return result;
                }
            } catch (Exception ignored) {
                // Try other JSON-LD blocks.
            }
        }

        return null;
    }

    private String findImageNode(JsonNode node, String sourceUrl) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }

        if (node.isArray()) {
            for (JsonNode child : node) {
                String result = findImageNode(child, sourceUrl);
                if (isUsableUrl(result)) {
                    return result;
                }
            }
            return null;
        }

        if (node.isObject()) {
            for (String key : new String[]{"image", "thumbnailUrl", "contentUrl", "thumbnail"}) {
                String result = extractImageValue(node.get(key), sourceUrl);
                if (isUsableUrl(result)) {
                    return result;
                }
            }

            Iterator<JsonNode> values = node.elements();
            while (values.hasNext()) {
                String result = findImageNode(values.next(), sourceUrl);
                if (isUsableUrl(result)) {
                    return result;
                }
            }
        }

        return null;
    }

    private String extractImageValue(JsonNode node, String sourceUrl) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }

        if (node.isTextual()) {
            return normalizeUrl(node.asText(), sourceUrl);
        }

        if (node.isArray()) {
            for (JsonNode child : node) {
                String result = extractImageValue(child, sourceUrl);
                if (isUsableUrl(result)) {
                    return result;
                }
            }
            return null;
        }

        if (node.isObject()) {
            for (String key : new String[]{"url", "contentUrl"}) {
                JsonNode value = node.get(key);
                if (value != null && value.isTextual()) {
                    String result = normalizeUrl(value.asText(), sourceUrl);
                    if (isUsableUrl(result)) {
                        return result;
                    }
                }
            }
        }

        return null;
    }

    private boolean looksLikeImageAsset(String value) {
        if (!isUsableUrl(value)) {
            return false;
        }

        String normalized = value.toLowerCase(Locale.ROOT);
        return normalized.matches(".*\\.(jpg|jpeg|png|webp|gif|avif)(?:[?#].*)?$")
                || normalized.contains("/image/")
                || normalized.contains("/images/");
    }

    private boolean looksLikeNonArticleAsset(String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        return normalized.contains("logo")
                || normalized.contains("icon")
                || normalized.contains("avatar")
                || normalized.contains("favicon")
                || normalized.contains("sprite");
    }

    private boolean isNasaArticle(String sourceUrl) {
        return sourceUrl != null && sourceUrl.toLowerCase(Locale.ROOT).contains("nasa.gov");
    }

    private boolean isApodArticle(String title) {
        return title != null && title.trim().toLowerCase(Locale.ROOT).startsWith("apod:");
    }

    private boolean hasUsableApiKey() {
        return nasaApiKey != null
                && !nasaApiKey.isBlank()
                && !"DEMO_KEY".equalsIgnoreCase(nasaApiKey);
    }

    private boolean isUsableUrl(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }

        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return normalized.startsWith("https://") || normalized.startsWith("http://");
    }

    private String normalizeUrl(String value, String sourceUrl) {
        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            URI candidate = URI.create(value.trim());
            if (candidate.isAbsolute()) {
                return candidate.toString();
            }

            return URI.create(sourceUrl).resolve(candidate).toString();
        } catch (Exception ex) {
            return null;
        }
    }

    private String hostOf(String value) {
        try {
            String host = URI.create(value).getHost();
            return host == null ? "unknown" : host;
        } catch (Exception ex) {
            return "unknown";
        }
    }
}
