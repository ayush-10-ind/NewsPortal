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
import java.util.Iterator;
import java.util.Locale;

@Service
public class NasaApodImageService {

    private static final Logger logger = LoggerFactory.getLogger(NasaApodImageService.class);

    private static final Duration API_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration PAGE_TIMEOUT = Duration.ofSeconds(8);
    private static final String APOD_API = "https://api.nasa.gov/planetary/apod";

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final String nasaApiKey;

    public NasaApodImageService(
            WebClient.Builder webClientBuilder,
            ObjectMapper objectMapper,
            @Value("${nasa.api-key:DEMO_KEY}") String nasaApiKey) {
        this.webClient = webClientBuilder.build();
        this.objectMapper = objectMapper;
        this.nasaApiKey = nasaApiKey;
    }

    /**
     * Resolves images for NASA articles.
     *
     * APOD articles use the structured NASA APOD API because the image is
     * tied directly to the publication date. Other NASA articles use the
     * publisher page's Open Graph/Twitter/JSON-LD image metadata.
     */
    public String resolveImage(String sourceUrl, String title, LocalDate publishedDate) {
        if (!isNasaArticle(sourceUrl)) {
            return null;
        }

        if (isApodArticle(title) && publishedDate != null) {
            String apodImage = resolveApodImage(publishedDate, title);
            if (isUsableUrl(apodImage)) {
                return apodImage;
            }
        }

        return resolveNasaPageImage(sourceUrl, title);
    }

    private String resolveApodImage(LocalDate publishedDate, String title) {
        try {
            String response = webClient.get()
                    .uri(APOD_API + "?api_key=" + nasaApiKey + "&date=" + publishedDate)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(API_TIMEOUT)
                    .onErrorResume(error -> Mono.empty())
                    .block();

            if (response == null || response.isBlank()) {
                return null;
            }

            JsonNode root = objectMapper.readTree(response);
            String mediaType = root.path("media_type").asText("");

            if (!"image".equalsIgnoreCase(mediaType)) {
                logger.debug("NASA APOD is not an image: date={}, title={}, mediaType={}",
                        publishedDate, title, mediaType);
                return null;
            }

            String hdUrl = root.path("hdurl").asText("");
            String standardUrl = root.path("url").asText("");
            String resolved = isUsableUrl(hdUrl) ? hdUrl : standardUrl;

            if (isUsableUrl(resolved)) {
                logger.info("NASA APOD image resolved: date={}, title={}", publishedDate, title);
                return resolved;
            }

        } catch (Exception ex) {
            logger.debug("NASA APOD image lookup failed: date={}, errorType={}, message={}",
                    publishedDate,
                    ex.getClass().getSimpleName(),
                    ex.getMessage());
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
                    .timeout(PAGE_TIMEOUT)
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
                if (element == null) continue;

                String content = element.attr("content").trim();
                if (isUsableUrl(content)) {
                    logger.info("NASA article image resolved from metadata: title={}, imageHost={}",
                            title, hostOf(content));
                    return content;
                }
            }

            String jsonLdImage = findJsonLdImage(document, sourceUrl);
            if (isUsableUrl(jsonLdImage)) {
                logger.info("NASA article image resolved from JSON-LD: title={}, imageHost={}",
                        title, hostOf(jsonLdImage));
                return jsonLdImage;
            }

        } catch (Exception ex) {
            logger.debug("NASA article image lookup failed: url={}, errorType={}, message={}",
                    sourceUrl,
                    ex.getClass().getSimpleName(),
                    ex.getMessage());
        }

        return null;
    }

    private String findJsonLdImage(Document document, String sourceUrl) {
        for (Element script : document.select("script[type=application/ld+json]")) {
            String json = script.data();
            if (json == null || json.isBlank()) {
                json = script.html();
            }

            if (json == null || json.isBlank()) continue;

            try {
                JsonNode root = objectMapper.readTree(json);
                String result = findImageNode(root, sourceUrl);
                if (isUsableUrl(result)) {
                    return result;
                }
            } catch (Exception ignored) {
                // A page may contain multiple JSON-LD blocks and some can be malformed.
            }
        }

        return null;
    }

    private String findImageNode(JsonNode node, String sourceUrl) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }

        if (node.isTextual()) {
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
            for (String fieldName : new String[]{"image", "thumbnailUrl", "contentUrl"}) {
                JsonNode imageNode = node.get(fieldName);
                String result = extractImageValue(imageNode, sourceUrl);
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
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }

        if (node.isTextual()) {
            return normalizeUrl(node.asText(), sourceUrl);
        }

        if (node.isArray()) {
            for (JsonNode child : node) {
                String result = extractImageValue(child, sourceUrl);
                if (isUsableUrl(result)) return result;
            }
            return null;
        }

        if (node.isObject()) {
            for (String key : new String[]{"url", "contentUrl"}) {
                JsonNode value = node.get(key);
                if (value != null && value.isTextual()) {
                    String result = normalizeUrl(value.asText(), sourceUrl);
                    if (isUsableUrl(result)) return result;
                }
            }
        }

        return null;
    }

    private boolean isNasaArticle(String sourceUrl) {
        if (sourceUrl == null || sourceUrl.isBlank()) return false;
        String normalized = sourceUrl.toLowerCase(Locale.ROOT);
        return normalized.contains("nasa.gov");
    }

    private boolean isApodArticle(String title) {
        return title != null && title.trim().toLowerCase(Locale.ROOT).startsWith("apod:");
    }

    private boolean isUsableUrl(String value) {
        if (value == null || value.isBlank()) return false;
        String normalized = value.trim();
        return normalized.startsWith("https://") || normalized.startsWith("http://");
    }

    private String normalizeUrl(String value, String sourceUrl) {
        if (value == null || value.isBlank()) return null;
        String trimmed = value.trim();

        try {
            URI candidate = URI.create(trimmed);
            if (candidate.isAbsolute()) {
                return trimmed;
            }

            URI base = URI.create(sourceUrl);
            return base.resolve(trimmed).toString();
        } catch (Exception ex) {
            return null;
        }
    }

    private String hostOf(String value) {
        try {
            URI uri = URI.create(value);
            return uri.getHost() == null ? "unknown" : uri.getHost();
        } catch (Exception ex) {
            return "unknown";
        }
    }
}
