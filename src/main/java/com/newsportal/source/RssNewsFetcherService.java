package com.newsportal.source;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class RssNewsFetcherService {

    private static final Logger logger = LoggerFactory.getLogger(RssNewsFetcherService.class);
    private static final Duration RSS_TIMEOUT = Duration.ofSeconds(8);
    private static final int MAX_FEED_SIZE = 5_000_000;

    private static final Pattern IMAGE_SRC_PATTERN = Pattern.compile(
            "<img[^>]+(?:src|data-src|data-lazy-src|data-original)\\s*=\\s*[\"']([^\"']+)[\"']",
            Pattern.CASE_INSENSITIVE);

    private final WebClient webClient;

    public RssNewsFetcherService(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(MAX_FEED_SIZE))
                .build();
    }

    public List<RssArticle> fetchFeed(NewsSource source) {
        List<RssArticle> articles = new ArrayList<>();

        if (source == null || !source.isUsable()) {
            logger.warn("RSS fetch skipped: source is null or unusable");
            return articles;
        }

        if (source.getProviderType() == null || !source.getProviderType().equalsIgnoreCase("RSS")) {
            logger.warn("RSS fetch skipped: provider is not RSS: {}", source.getName());
            return articles;
        }

        String endpoint = source.getEndpoint();
        if (endpoint == null || endpoint.isBlank()) {
            logger.warn("RSS fetch skipped: empty endpoint for {}", source.getName());
            return articles;
        }

        logger.info("Fetching RSS feed: source={}, endpoint={}", source.getName(), endpoint);

        try {
            String xml = webClient.get()
                    .uri(endpoint)
                    .header("User-Agent", "AgniPress-RSSFetcher/1.0")
                    .header("Accept", "application/rss+xml, application/atom+xml, application/xml, text/xml")
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(RSS_TIMEOUT)
                    .block();

            if (xml == null || xml.isBlank()) {
                logger.warn("RSS fetch returned empty response: {}", source.getName());
                return articles;
            }

            if (xml.length() > MAX_FEED_SIZE) {
                logger.warn("RSS feed exceeded {} bytes and will be truncated: {}", MAX_FEED_SIZE, source.getName());
                xml = xml.substring(0, MAX_FEED_SIZE);
            }

            articles = parseFeed(xml, source);
            logger.info("RSS feed fetched: source={}, articles={}", source.getName(), articles.size());
            return articles;

        } catch (Exception e) {
            logger.error("RSS fetch failed: source={}, errorType={}, message={}",
                    source.getName(), e.getClass().getSimpleName(), e.getMessage());
            return articles;
        }
    }

    private List<RssArticle> parseFeed(String xml, NewsSource source) {
        List<RssArticle> articles = new ArrayList<>();

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();

            // Legitimate feeds such as Indian Express can contain DOCTYPE.
            // Keep external entities and external DTD loading disabled.
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", false);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);

            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
            document.getDocumentElement().normalize();

            NodeList items = document.getElementsByTagName("item");
            if (items.getLength() > 0) {
                for (int i = 0; i < items.getLength(); i++) {
                    if (items.item(i).getNodeType() == Node.ELEMENT_NODE) {
                        RssArticle article = parseRssItem((Element) items.item(i), source);
                        if (article != null) articles.add(article);
                    }
                }
                return articles;
            }

            NodeList entries = document.getElementsByTagName("entry");
            for (int i = 0; i < entries.getLength(); i++) {
                if (entries.item(i).getNodeType() == Node.ELEMENT_NODE) {
                    RssArticle article = parseAtomEntry((Element) entries.item(i), source);
                    if (article != null) articles.add(article);
                }
            }

            if (articles.isEmpty()) {
                logger.warn("RSS parse found no <item> or <entry> elements: {}", source.getName());
            }

        } catch (Exception e) {
            logger.error("RSS XML parse failed: source={}, errorType={}, message={}",
                    source.getName(), e.getClass().getSimpleName(), e.getMessage());
        }

        return articles;
    }

    private RssArticle parseRssItem(Element item, NewsSource source) {
        String title = childText(item, "title");
        String link = childText(item, "link");
        String description = childText(item, "description");
        if (blank(description)) description = childText(item, "content:encoded");

        String published = childText(item, "pubDate");
        if (blank(published)) published = childText(item, "published");

        String author = childText(item, "author");
        if (blank(author)) author = childText(item, "dc:creator");

        if (blank(title) || blank(link)) return null;

        return new RssArticle(
                cleanText(title), cleanUrl(link), cleanText(description), cleanText(author),
                cleanText(published), source.getName(), source.getSection(), extractRssImage(item));
    }

    private RssArticle parseAtomEntry(Element entry, NewsSource source) {
        String title = childText(entry, "title");
        String link = extractAtomLink(entry);
        String description = childText(entry, "summary");
        if (blank(description)) description = childText(entry, "content");

        String published = childText(entry, "published");
        if (blank(published)) published = childText(entry, "updated");

        if (blank(title) || blank(link)) return null;

        return new RssArticle(
                cleanText(title), cleanUrl(link), cleanText(description),
                cleanText(extractAtomAuthor(entry)), cleanText(published),
                source.getName(), source.getSection(), extractAtomImage(entry));
    }

    private String extractRssImage(Element item) {
        String image = imageFromTag(item, "media:content");
        if (validImage(image)) return cleanUrl(image);

        image = imageFromTag(item, "media:thumbnail");
        if (validImage(image)) return cleanUrl(image);

        NodeList children = item.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) continue;
            Element element = (Element) node;
            if ("enclosure".equalsIgnoreCase(element.getNodeName()) ||
                    "enclosure".equalsIgnoreCase(element.getLocalName())) {
                String url = element.getAttribute("url");
                String type = element.getAttribute("type");
                if (validImage(url) || (type != null && type.toLowerCase().startsWith("image/") && !blank(url))) {
                    return cleanUrl(url);
                }
            }
        }

        image = imageFromTag(item, "image");
        if (validImage(image)) return cleanUrl(image);

        image = imageFromHtml(childText(item, "content:encoded"));
        if (validImage(image)) return cleanUrl(image);

        return cleanUrl(imageFromHtml(childText(item, "description")));
    }

    private String extractAtomImage(Element entry) {
        String image = imageFromTag(entry, "media:content");
        if (validImage(image)) return cleanUrl(image);

        image = imageFromTag(entry, "media:thumbnail");
        if (validImage(image)) return cleanUrl(image);

        NodeList children = entry.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) continue;
            Element element = (Element) node;
            if (!"link".equalsIgnoreCase(element.getNodeName()) &&
                    !"link".equalsIgnoreCase(element.getLocalName())) continue;

            if ("enclosure".equalsIgnoreCase(element.getAttribute("rel"))) {
                String href = element.getAttribute("href");
                String type = element.getAttribute("type");
                if (validImage(href) || (type != null && type.toLowerCase().startsWith("image/") && !blank(href))) {
                    return cleanUrl(href);
                }
            }
        }

        String content = childText(entry, "content");
        image = imageFromHtml(content);
        if (validImage(image)) return cleanUrl(image);

        return cleanUrl(imageFromHtml(childText(entry, "summary")));
    }

    private String imageFromTag(Element parent, String tagName) {
        NodeList nodes = parent.getElementsByTagName(tagName);
        for (int i = 0; i < nodes.getLength(); i++) {
            if (nodes.item(i).getNodeType() != Node.ELEMENT_NODE) continue;
            Element element = (Element) nodes.item(i);
            String url = element.getAttribute("url");
            if (blank(url)) url = element.getAttribute("href");
            if (blank(url)) url = element.getTextContent();
            if (validImage(url)) return url;
        }
        return null;
    }

    private String imageFromHtml(String html) {
        if (blank(html)) return null;
        Matcher matcher = IMAGE_SRC_PATTERN.matcher(html);
        if (matcher.find() && validImage(matcher.group(1))) return matcher.group(1);
        return null;
    }

    private String childText(Element parent, String tagName) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) continue;
            Element element = (Element) node;
            String nodeName = element.getNodeName();
            String localName = element.getLocalName();
            if (tagName.equalsIgnoreCase(nodeName) ||
                    (localName != null && tagName.equalsIgnoreCase(localName))) {
                return element.getTextContent();
            }
        }
        return null;
    }

    private String extractAtomLink(Element entry) {
        NodeList children = entry.getChildNodes();
        String alternate = null;
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) continue;
            Element link = (Element) node;
            if (!"link".equalsIgnoreCase(link.getNodeName()) &&
                    !"link".equalsIgnoreCase(link.getLocalName())) continue;
            String href = link.getAttribute("href");
            if (blank(href)) continue;
            String rel = link.getAttribute("rel");
            if (blank(rel) || "alternate".equalsIgnoreCase(rel)) return href;
            if (alternate == null) alternate = href;
        }
        return alternate;
    }

    private String extractAtomAuthor(Element entry) {
        NodeList children = entry.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) continue;
            Element author = (Element) node;
            if (!"author".equalsIgnoreCase(author.getNodeName()) &&
                    !"author".equalsIgnoreCase(author.getLocalName())) continue;
            String name = childText(author, "name");
            return blank(name) ? author.getTextContent() : name;
        }
        return null;
    }

    private boolean validImage(String url) {
        if (blank(url)) return false;
        String value = url.trim();
        return (value.startsWith("http://") || value.startsWith("https://")) &&
                !value.toLowerCase().contains("data:image");
    }

    private String cleanText(String value) {
        if (value == null) return null;
        return value.replaceAll("<[^>]+>", " ")
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&apos;", "'")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String cleanUrl(String value) {
        return value == null ? null : value.replace("&amp;", "&").trim();
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    public static class RssArticle {
        private final String title;
        private final String url;
        private final String description;
        private final String author;
        private final String publishedDate;
        private final String sourceName;
        private final NewsSection section;
        private final String imageUrl;

        public RssArticle(String title, String url, String description, String author,
                          String publishedDate, String sourceName, NewsSection section, String imageUrl) {
            this.title = title;
            this.url = url;
            this.description = description;
            this.author = author;
            this.publishedDate = publishedDate;
            this.sourceName = sourceName;
            this.section = section;
            this.imageUrl = imageUrl;
        }

        public String getTitle() { return title; }
        public String getUrl() { return url; }
        public String getDescription() { return description; }
        public String getAuthor() { return author; }
        public String getPublishedDate() { return publishedDate; }
        public String getSourceName() { return sourceName; }
        public NewsSection getSection() { return section; }
        public String getImageUrl() { return imageUrl; }
    }
}