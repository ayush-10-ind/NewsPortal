package com.newsportal.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.Duration;
import java.util.Locale;

@Service
public class WiredImageResolverService {

    private static final Duration TIMEOUT = Duration.ofSeconds(6);

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public WiredImageResolverService(WebClient.Builder webClientBuilder, ObjectMapper objectMapper) {
        this.webClient = webClientBuilder.build();
        this.objectMapper = objectMapper;
    }

    public String resolveImage(String sourceUrl, String title) {
        if (!isWired(sourceUrl)) {
            return null;
        }

        try {
            String html = webClient.get()
                    .uri(URI.create(sourceUrl.trim()))
                    .headers(headers -> {
                        headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/153 Safari/537.36");
                        headers.set("Accept", "text/html,application/xhtml+xml");
                        headers.set("Accept-Language", "en-US,en;q=0.9");
                    })
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(TIMEOUT)
                    .onErrorResume(error -> Mono.empty())
                    .block();

            if (html == null || html.isBlank()) {
                return null;
            }

            Document document = Jsoup.parse(html, sourceUrl);

            for (String selector : new String[]{
                    "meta[property=og:image]",
                    "meta[property=og:image:url]",
                    "meta[property=og:image:secure_url]",
                    "meta[name=twitter:image]",
                    "meta[name=twitter:image:src]"
            }) {
                Element element = document.selectFirst(selector);
                if (element == null) continue;
                String image = normalizeUrl(element.attr("content"), sourceUrl);
                if (isUsable(image)) return image;
            }

            for (Element script : document.select("script[type=application/ld+json]")) {
                String json = script.data();
                if (json == null || json.isBlank()) json = script.html();
                if (json == null || json.isBlank()) continue;

                try {
                    JsonNode root = objectMapper.readTree(json);
                    String image = findImage(root, sourceUrl);
                    if (isUsable(image)) return image;
                } catch (Exception ignored) {
                }
            }

            for (Element image : document.select("article img, main img, img")) {
                for (String attr : new String[]{"src", "data-src", "data-lazy-src", "data-original"}) {
                    String candidate = normalizeUrl(image.attr(attr), sourceUrl);
                    if (isUsable(candidate) && !isAssetNoise(candidate)) {
                        return candidate;
                    }
                }
            }
        } catch (Exception ignored) {
        }

        return null;
    }

    private String findImage(JsonNode node, String sourceUrl) {
        if (node == null || node.isNull() || node.isMissingNode()) return null;

        if (node.isTextual()) return null;

        if (node.isArray()) {
            for (JsonNode child : node) {
                String result = findImage(child, sourceUrl);
                if (isUsable(result)) return result;
            }
            return null;
        }

        if (node.isObject()) {
            for (String key : new String[]{"image", "thumbnailUrl", "contentUrl", "thumbnail"}) {
                JsonNode value = node.get(key);
                String result = extractValue(value, sourceUrl);
                if (isUsable(result)) return result;
            }

            var fields = node.elements();
            while (fields.hasNext()) {
                String result = findImage(fields.next(), sourceUrl);
                if (isUsable(result)) return result;
            }
        }

        return null;
    }

    private String extractValue(JsonNode node, String sourceUrl) {
        if (node == null || node.isNull() || node.isMissingNode()) return null;
        if (node.isTextual()) return normalizeUrl(node.asText(), sourceUrl);
        if (node.isArray()) {
            for (JsonNode child : node) {
                String result = extractValue(child, sourceUrl);
                if (isUsable(result)) return result;
            }
        }
        if (node.isObject()) {
            for (String key : new String[]{"url", "contentUrl"}) {
                JsonNode value = node.get(key);
                if (value != null && value.isTextual()) {
                    String result = normalizeUrl(value.asText(), sourceUrl);
                    if (isUsable(result)) return result;
                }
            }
        }
        return null;
    }

    private boolean isWired(String sourceUrl) {
        if (sourceUrl == null || sourceUrl.isBlank()) return false;
        String host = hostOf(sourceUrl);
        return host.equals("wired.com") || host.endsWith(".wired.com");
    }

    private boolean isUsable(String value) {
        return value != null && (value.startsWith("https://") || value.startsWith("http://"));
    }

    private String normalizeUrl(String value, String sourceUrl) {
        if (value == null || value.isBlank()) return null;
        try {
            URI candidate = URI.create(value.trim());
            return candidate.isAbsolute() ? candidate.toString() : URI.create(sourceUrl).resolve(candidate).toString();
        } catch (Exception ignored) {
            return null;
        }
    }

    private String hostOf(String value) {
        try {
            String host = URI.create(value).getHost();
            return host == null ? "" : host.toLowerCase(Locale.ROOT);
        } catch (Exception ignored) {
            return "";
        }
    }

    private boolean isAssetNoise(String url) {
        String lower = url.toLowerCase(Locale.ROOT);
        return lower.contains("logo") || lower.contains("favicon") || lower.contains("avatar") || lower.contains("icon");
    }
}
