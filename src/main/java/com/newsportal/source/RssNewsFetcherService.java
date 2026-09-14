package com.newsportal.source;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

@Service
public class RssNewsFetcherService {

    private static final Logger logger = LoggerFactory.getLogger(RssNewsFetcherService.class);

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(8);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);

    // RSS feeds often expose dozens of historical entries. AgniPress only
    // needs the newest entries on each scheduled import, so cap the parsed
    // result to keep memory and downstream work bounded.
    private static final int MAX_ARTICLES_PER_FEED = 12;

    private static final Pattern HTML_ENTITY_PATTERN =
            Pattern.compile("&(?:nbsp|amp|quot|apos|lt|gt|hellip|ndash|mdash|rsquo|lsquo|rdquo|ldquo|trade|copy|reg|bull|middot|laquo|raquo|#\\d+|#x[0-9a-fA-F]+);");

    private static final Pattern BARE_AMPERSAND_PATTERN =
            Pattern.compile("&(?!#\\d+;|#x[0-9a-fA-F]+;|[A-Za-z][A-Za-z0-9]{1,31};)");

    /*
     * Some publisher feeds place raw HTML inside description/summary fields
     * without wrapping it in CDATA. A raw <link crossorigin ...> tag is not
     * valid RSS XML and can abort the entire feed. Only remove link tags that
     * clearly belong to embedded HTML; never remove normal RSS <link> elements.
     */
    private static final Pattern RAW_HTML_LINK_TAG_PATTERN =
            Pattern.compile("<\\s*link\\b(?=[^>]*\\bcrossorigin\\b)[^>]*>", Pattern.CASE_INSENSITIVE);

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public List<RssArticle> fetchFeed(NewsSource source) {
        if (source == null || source.getEndpoint() == null || source.getEndpoint().isBlank()) {
            return List.of();
        }

        String endpoint = source.getEndpoint().trim();
        logger.debug("Fetching RSS feed: source={}, endpoint={}", source.getName(), endpoint);

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(REQUEST_TIMEOUT)
                    .header("User-Agent", "Mozilla/5.0 (compatible; AgniPress/1.0; +https://agnipress.app)")
                    .header("Accept", "application/rss+xml, application/atom+xml, application/xml, text/xml, */*")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                logger.warn("RSS fetch failed: source={}, errorType=HTTP{}, message={}",
                        source.getName(), response.statusCode(), "non-success response");
                return List.of();
            }

            return parseFeed(source.getName(), sanitizeXml(response.body()));

        } catch (Exception e) {
            logger.warn("RSS fetch failed: source={}, errorType={}, message={}",
                    source.getName(), e.getClass().getSimpleName(), e.getMessage());
            return List.of();
        }
    }

    private List<RssArticle> parseFeed(String sourceName, String xml) {
        List<RssArticle> articles = new ArrayList<>();

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);

            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
            Element root = document.getDocumentElement();

            NodeList items = root.getElementsByTagName("item");
            if (items.getLength() == 0) {
                items = root.getElementsByTagNameNS("*", "entry");
            }

            for (int i = 0; i < items.getLength() && articles.size() < MAX_ARTICLES_PER_FEED; i++) {
                Node node = items.item(i);
                if (!(node instanceof Element element)) continue;

                String title = firstText(element, "title");
                String description = firstText(element, "description");
                if (description == null) description = firstText(element, "summary");
                if (description == null) description = firstText(element, "content");

                String url = firstText(element, "link");
                if (url == null) {
                    NodeList links = element.getElementsByTagNameNS("*", "link");
                    for (int j = 0; j < links.getLength(); j++) {
                        Node link = links.item(j);
                        if (link instanceof Element linkElement) {
                            String href = linkElement.getAttribute("href");
                            if (href != null && !href.isBlank()) {
                                url = href;
                                break;
                            }
                        }
                    }
                }

                String author = firstText(element, "author");
                if (author == null) author = firstText(element, "creator");
                if (author == null) author = firstText(element, "name");

                String published = firstText(element, "pubDate");
                if (published == null) published = firstText(element, "published");
                if (published == null) published = firstText(element, "updated");

                String imageUrl = extractImage(element, description);

                if (title == null || title.isBlank() || url == null || url.isBlank()) {
                    continue;
                }

                articles.add(new RssArticle(
                        cleanText(title),
                        cleanText(description),
                        cleanUrl(url),
                        cleanText(author),
                        cleanText(published),
                        cleanUrl(imageUrl)
                ));
            }

        } catch (Exception e) {
            logger.error("RSS XML parse failed: source={}, errorType={}, message={}",
                    sourceName, e.getClass().getSimpleName(), e.getMessage());
        }

        return articles;
    }

    private String firstText(Element parent, String localName) {
        NodeList nodes = parent.getElementsByTagNameNS("*", localName);
        if (nodes.getLength() == 0) {
            nodes = parent.getElementsByTagName(localName);
        }
        if (nodes.getLength() == 0) return null;

        Node node = nodes.item(0);
        String value = node.getTextContent();
        return value == null || value.isBlank() ? null : value;
    }

    private String extractImage(Element item, String description) {
        String[] imageTags = {"media:content", "media:thumbnail", "enclosure", "image"};
        for (String tag : imageTags) {
            NodeList nodes = item.getElementsByTagName(tag);
            for (int i = 0; i < nodes.getLength(); i++) {
                Node node = nodes.item(i);
                if (node instanceof Element element) {
                    String url = element.getAttribute("url");
                    if (url == null || url.isBlank()) url = element.getAttribute("href");
                    if (looksLikeImage(url)) return url;
                }
            }
        }

        if (description != null) {
            Matcher matcher = Pattern.compile(
                    "<img[^>]+(?:src|data-src|data-lazy-src|data-original)\\s*=\\s*[\"']([^\"']+)[\"']",
                    Pattern.CASE_INSENSITIVE).matcher(description);
            if (matcher.find()) return matcher.group(1);
        }

        return null;
    }

    private boolean looksLikeImage(String value) {
        if (value == null || value.isBlank()) return false;
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.matches(".*\\.(jpg|jpeg|png|webp|gif|avif)(?:[?#].*)?$") || lower.contains("image");
    }

    private String sanitizeXml(String xml) {
        if (xml == null) return "";

        String result = RAW_HTML_LINK_TAG_PATTERN.matcher(xml).replaceAll("");

        result = HTML_ENTITY_PATTERN.matcher(result).replaceAll(match -> {
            return switch (match.group().toLowerCase(Locale.ROOT)) {
                case "&nbsp;" -> "&#160;";
                case "&amp;" -> "&#38;";
                case "&quot;" -> "&#34;";
                case "&apos;" -> "&#39;";
                case "&lt;" -> "&#60;";
                case "&gt;" -> "&#62;";
                case "&hellip;" -> "&#8230;";
                case "&ndash;" -> "&#8211;";
                case "&mdash;" -> "&#8212;";
                case "&rsquo;" -> "&#8217;";
                case "&lsquo;" -> "&#8216;";
                case "&rdquo;" -> "&#8221;";
                case "&ldquo;" -> "&#8220;";
                case "&trade;" -> "&#8482;";
                case "&copy;" -> "&#169;";
                case "&reg;" -> "&#174;";
                case "&bull;" -> "&#8226;";
                case "&middot;" -> "&#183;";
                case "&laquo;" -> "&#171;";
                case "&raquo;" -> "&#187;";
                default -> match.group();
            };
        });

        result = BARE_AMPERSAND_PATTERN.matcher(result).replaceAll("&amp;");
        return result;
    }

    private String cleanText(String value) {
        if (value == null) return null;
        String cleaned = value.replace("\r", "").trim();
        return cleaned.isBlank() ? null : cleaned;
    }

    private String cleanUrl(String value) {
        if (value == null) return null;
        String cleaned = value.trim();
        return cleaned.isBlank() ? null : cleaned;
    }

    public static class RssArticle {
        private final String title;
        private final String description;
        private final String url;
        private final String author;
        private final String publishedAt;
        private final String imageUrl;

        public RssArticle(String title, String description, String url,
                          String author, String publishedAt, String imageUrl) {
            this.title = title;
            this.description = description;
            this.url = url;
            this.author = author;
            this.publishedAt = publishedAt;
            this.imageUrl = imageUrl;
        }

        public String getTitle() { return title; }
        public String getDescription() { return description; }
        public String getUrl() { return url; }
        public String getAuthor() { return author; }
        public String getPublishedAt() { return publishedAt; }
        public String getPublishedDate() { return publishedAt; }
        public String getImageUrl() { return imageUrl; }
    }
}
