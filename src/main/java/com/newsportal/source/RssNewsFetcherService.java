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

@Service
public class RssNewsFetcherService {

    private static final Logger logger =
            LoggerFactory.getLogger(RssNewsFetcherService.class);

    private static final Duration RSS_TIMEOUT =
            Duration.ofSeconds(8);

    private static final int MAX_FEED_SIZE =
            1_000_000;

    private final WebClient webClient;

    public RssNewsFetcherService(
            WebClient.Builder webClientBuilder) {

        this.webClient =
                webClientBuilder
                        .codecs(configurer ->
                                configurer
                                        .defaultCodecs()
                                        .maxInMemorySize(MAX_FEED_SIZE)
                        )
                        .build();
    }

    // =====================================================
    // FETCH RSS FEED
    // =====================================================

    public List<RssArticle> fetchFeed(
            NewsSource source) {

        List<RssArticle> articles =
                new ArrayList<>();

        if (source == null) {
            logger.warn("RSS fetch skipped: source is null");
            return articles;
        }

        if (!source.isUsable()) {
            logger.warn(
                    "RSS fetch skipped: source is not usable: {}",
                    source.getName()
            );
            return articles;
        }

        if (source.getProviderType() == null ||
                !source.getProviderType()
                        .equalsIgnoreCase("RSS")) {

            logger.warn(
                    "RSS fetch skipped: provider is not RSS: {}",
                    source.getName()
            );
            return articles;
        }

        String endpoint =
                source.getEndpoint();

        if (endpoint == null ||
                endpoint.isBlank()) {

            logger.warn(
                    "RSS fetch skipped: empty endpoint for {}",
                    source.getName()
            );
            return articles;
        }

        logger.info(
                "Fetching RSS feed: source={}, endpoint={}",
                source.getName(),
                endpoint
        );

        try {

            String xml =
                    webClient
                            .get()
                            .uri(endpoint)
                            .header(
                                    "User-Agent",
                                    "AgniPress-RSSFetcher/1.0"
                            )
                            .header(
                                    "Accept",
                                    "application/rss+xml, application/atom+xml, application/xml, text/xml"
                            )
                            .retrieve()
                            .bodyToMono(String.class)
                            .timeout(RSS_TIMEOUT)
                            .block();

            if (xml == null ||
                    xml.isBlank()) {

                logger.warn(
                        "RSS fetch returned empty response: {}",
                        source.getName()
                );

                return articles;
            }

            if (xml.length() >
                    MAX_FEED_SIZE) {

                logger.warn(
                        "RSS feed exceeded {} bytes and will be truncated: {}",
                        MAX_FEED_SIZE,
                        source.getName()
                );

                xml =
                        xml.substring(
                                0,
                                MAX_FEED_SIZE
                        );
            }

            articles =
                    parseFeed(
                            xml,
                            source
                    );

            logger.info(
                    "RSS feed fetched: source={}, articles={}",
                    source.getName(),
                    articles.size()
            );

            return articles;

        } catch (Exception e) {

            logger.error(
                    "RSS fetch failed: source={}, errorType={}, message={}",
                    source.getName(),
                    e.getClass().getSimpleName(),
                    e.getMessage()
            );

            return articles;
        }
    }

    // =====================================================
    // PARSE XML FEED
    // =====================================================

    private List<RssArticle> parseFeed(
            String xml,
            NewsSource source) {

        List<RssArticle> articles =
                new ArrayList<>();

        try {

            DocumentBuilderFactory factory =
                    DocumentBuilderFactory.newInstance();

            // Prevent XXE / external entity attacks
            factory.setFeature(
                    "http://apache.org/xml/features/disallow-doctype-decl",
                    true
            );

            factory.setFeature(
                    "http://xml.org/sax/features/external-general-entities",
                    false
            );

            factory.setFeature(
                    "http://xml.org/sax/features/external-parameter-entities",
                    false
            );

            factory.setFeature(
                    "http://apache.org/xml/features/nonvalidating/load-external-dtd",
                    false
            );

            factory.setXIncludeAware(false);

            factory.setExpandEntityReferences(false);

            factory.setFeature(
                    XMLConstants.FEATURE_SECURE_PROCESSING,
                    true
            );

            DocumentBuilder builder =
                    factory.newDocumentBuilder();

            Document document =
                    builder.parse(
                            new ByteArrayInputStream(
                                    xml.getBytes(
                                            StandardCharsets.UTF_8
                                    )
                            )
                    );

            document
                    .getDocumentElement()
                    .normalize();

            // =================================================
            // RSS
            // =================================================

            NodeList rssItems =
                    document.getElementsByTagName("item");

            if (rssItems.getLength() > 0) {

                for (int i = 0;
                     i < rssItems.getLength();
                     i++) {

                    Node node =
                            rssItems.item(i);

                    if (node.getNodeType()
                            != Node.ELEMENT_NODE) {

                        continue;
                    }

                    Element item =
                            (Element) node;

                    RssArticle article =
                            parseRssItem(
                                    item,
                                    source
                            );

                    if (article != null) {
                        articles.add(article);
                    }
                }

                return articles;
            }

            // =================================================
            // ATOM
            // =================================================

            NodeList atomEntries =
                    document.getElementsByTagName("entry");

            if (atomEntries.getLength() > 0) {

                for (int i = 0;
                     i < atomEntries.getLength();
                     i++) {

                    Node node =
                            atomEntries.item(i);

                    if (node.getNodeType()
                            != Node.ELEMENT_NODE) {

                        continue;
                    }

                    Element entry =
                            (Element) node;

                    RssArticle article =
                            parseAtomEntry(
                                    entry,
                                    source
                            );

                    if (article != null) {
                        articles.add(article);
                    }
                }

                return articles;
            }

            logger.warn(
                    "RSS parse found no <item> or <entry> elements: {}",
                    source.getName()
            );

        } catch (Exception e) {

            logger.error(
                    "RSS XML parse failed: source={}, errorType={}, message={}",
                    source.getName(),
                    e.getClass().getSimpleName(),
                    e.getMessage()
            );
        }

        return articles;
    }

    // =====================================================
    // PARSE RSS ITEM
    // =====================================================

    private RssArticle parseRssItem(
            Element item,
            NewsSource source) {

        String title =
                getChildText(
                        item,
                        "title"
                );

        String link =
                getChildText(
                        item,
                        "link"
                );

        String description =
                getChildText(
                        item,
                        "description"
                );

        if (description == null ||
                description.isBlank()) {

            description =
                    getChildText(
                            item,
                            "content:encoded"
                    );
        }

        String publishedDate =
                getChildText(
                        item,
                        "pubDate"
                );

        if (publishedDate == null ||
                publishedDate.isBlank()) {

            publishedDate =
                    getChildText(
                            item,
                            "published"
                    );
        }

        String author =
                getChildText(
                        item,
                        "author"
                );

        if (author == null ||
                author.isBlank()) {

            author =
                    getChildText(
                            item,
                            "dc:creator"
                    );
        }

        String imageUrl =
                extractRssImage(item);

        if (title == null ||
                title.isBlank() ||
                link == null ||
                link.isBlank()) {

            return null;
        }

        return new RssArticle(
                cleanText(title),
                cleanUrl(link),
                cleanText(description),
                cleanText(author),
                cleanText(publishedDate),
                source.getName(),
                source.getSection(),
                cleanUrl(imageUrl)
        );
    }

    // =====================================================
    // PARSE ATOM ENTRY
    // =====================================================

    private RssArticle parseAtomEntry(
            Element entry,
            NewsSource source) {

        String title =
                getChildText(
                        entry,
                        "title"
                );

        String link =
                extractAtomLink(entry);

        String description =
                getChildText(
                        entry,
                        "summary"
                );

        if (description == null ||
                description.isBlank()) {

            description =
                    getChildText(
                            entry,
                            "content"
                    );
        }

        String publishedDate =
                getChildText(
                        entry,
                        "published"
                );

        if (publishedDate == null ||
                publishedDate.isBlank()) {

            publishedDate =
                    getChildText(
                            entry,
                            "updated"
                    );
        }

        String author =
                extractAtomAuthor(entry);

        String imageUrl =
                extractAtomImage(entry);

        if (title == null ||
                title.isBlank() ||
                link == null ||
                link.isBlank()) {

            return null;
        }

        return new RssArticle(
                cleanText(title),
                cleanUrl(link),
                cleanText(description),
                cleanText(author),
                cleanText(publishedDate),
                source.getName(),
                source.getSection(),
                cleanUrl(imageUrl)
        );
    }

    // =====================================================
    // RSS IMAGE EXTRACTION
    // =====================================================

    private String extractRssImage(
            Element item) {

        String image =
                extractImageFromTag(
                        item,
                        "media:content"
                );

        if (isValidImageUrl(image)) {
            return image;
        }

        image =
                extractImageFromTag(
                        item,
                        "media:thumbnail"
                );

        if (isValidImageUrl(image)) {
            return image;
        }

        NodeList children =
                item.getChildNodes();

        for (int i = 0;
             i < children.getLength();
             i++) {

            Node node =
                    children.item(i);

            if (node.getNodeType()
                    != Node.ELEMENT_NODE) {

                continue;
            }

            Element element =
                    (Element) node;

            String nodeName =
                    element.getNodeName();

            String localName =
                    element.getLocalName();

            if (!"enclosure".equalsIgnoreCase(nodeName) &&
                    !"enclosure".equalsIgnoreCase(localName)) {

                continue;
            }

            String url =
                    element.getAttribute("url");

            String type =
                    element.getAttribute("type");

            if (isValidImageUrl(url) ||
                    (type != null &&
                            type.toLowerCase()
                                    .startsWith("image/") &&
                            url != null &&
                            !url.isBlank())) {

                return cleanUrl(url);
            }
        }

        image =
                extractImageFromTag(
                        item,
                        "image"
                );

        if (isValidImageUrl(image)) {
            return image;
        }

        String encodedContent =
                getChildText(
                        item,
                        "content:encoded"
                );

        image =
                extractImageFromHtml(
                        encodedContent
                );

        if (isValidImageUrl(image)) {
            return image;
        }

        String description =
                getChildText(
                        item,
                        "description"
                );

        image =
                extractImageFromHtml(
                        description
                );

        if (isValidImageUrl(image)) {
            return image;
        }

        return null;
    }

    // =====================================================
    // ATOM IMAGE EXTRACTION
    // =====================================================

    private String extractAtomImage(
            Element entry) {

        String image =
                extractImageFromTag(
                        entry,
                        "media:content"
                );

        if (isValidImageUrl(image)) {
            return image;
        }

        image =
                extractImageFromTag(
                        entry,
                        "media:thumbnail"
                );

        if (isValidImageUrl(image)) {
            return image;
        }

        NodeList children =
                entry.getChildNodes();

        for (int i = 0;
             i < children.getLength();
             i++) {

            Node node =
                    children.item(i);

            if (node.getNodeType()
                    != Node.ELEMENT_NODE) {

                continue;
            }

            Element element =
                    (Element) node;

            String nodeName =
                    element.getNodeName();

            String localName =
                    element.getLocalName();

            if (!"link".equalsIgnoreCase(nodeName) &&
                    !"link".equalsIgnoreCase(localName)) {

                continue;
            }

            String rel =
                    element.getAttribute("rel");

            String type =
                    element.getAttribute("type");

            String href =
                    element.getAttribute("href");

            if ("enclosure".equalsIgnoreCase(rel) &&
                    href != null &&
                    !href.isBlank()) {

                if (isValidImageUrl(href) ||
                        (type != null &&
                                type.toLowerCase()
                                        .startsWith("image/"))) {

                    return cleanUrl(href);
                }
            }
        }

        String content =
                getChildText(
                        entry,
                        "content"
                );

        image =
                extractImageFromHtml(content);

        if (isValidImageUrl(image)) {
            return image;
        }

        String summary =
                getChildText(
                        entry,
                        "summary"
                );

        image =
                extractImageFromHtml(summary);

        if (isValidImageUrl(image)) {
            return image;
        }

        return null;
    }

    // =====================================================
    // IMAGE FROM XML TAG
    // =====================================================

    private String extractImageFromTag(
            Element parent,
            String tagName) {

        NodeList descendants =
                parent.getElementsByTagName(tagName);

        for (int i = 0;
             i < descendants.getLength();
             i++) {

            Node node =
                    descendants.item(i);

            if (node.getNodeType()
                    != Node.ELEMENT_NODE) {

                continue;
            }

            Element element =
                    (Element) node;

            String url =
                    element.getAttribute("url");

            if (url == null ||
                    url.isBlank()) {

                url =
                        element.getAttribute("href");
            }

            if (url == null ||
                    url.isBlank()) {

                url =
                        element.getTextContent();
            }

            if (isValidImageUrl(url)) {
                return cleanUrl(url);
            }
        }

        return null;
    }

    // =====================================================
    // IMAGE FROM HTML
    // =====================================================

    private String extractImageFromHtml(
            String html) {

        if (html == null ||
                html.isBlank()) {

            return null;
        }

        java.util.regex.Pattern srcPattern =
                java.util.regex.Pattern.compile(
                        "<img[^>]+src\\s*=\\s*[\"']([^\"']+)[\"']",
                        java.util.regex.Pattern.CASE_INSENSITIVE
                );

        java.util.regex.Matcher matcher =
                srcPattern.matcher(html);

        if (matcher.find()) {

            String image =
                    matcher.group(1);

            if (isValidImageUrl(image)) {
                return cleanUrl(image);
            }
        }

        java.util.regex.Pattern dataSrcPattern =
                java.util.regex.Pattern.compile(
                        "<img[^>]+data-src\\s*=\\s*[\"']([^\"']+)[\"']",
                        java.util.regex.Pattern.CASE_INSENSITIVE
                );

        matcher =
                dataSrcPattern.matcher(html);

        if (matcher.find()) {

            String image =
                    matcher.group(1);

            if (isValidImageUrl(image)) {
                return cleanUrl(image);
            }
        }

        return null;
    }

    // =====================================================
    // IMAGE URL VALIDATION
    // =====================================================

    private boolean isValidImageUrl(
            String url) {

        if (url == null ||
                url.isBlank()) {

            return false;
        }

        String cleaned =
                url.trim();

        if (!(cleaned.startsWith("http://") ||
                cleaned.startsWith("https://"))) {

            return false;
        }

        String lower =
                cleaned.toLowerCase();

        return !lower.contains("data:image");
    }

    // =====================================================
    // GET CHILD TEXT
    // =====================================================

    private String getChildText(
            Element parent,
            String tagName) {

        NodeList children =
                parent.getChildNodes();

        for (int i = 0;
             i < children.getLength();
             i++) {

            Node node =
                    children.item(i);

            if (node.getNodeType()
                    != Node.ELEMENT_NODE) {

                continue;
            }

            Element element =
                    (Element) node;

            String nodeName =
                    element.getNodeName();

            String localName =
                    element.getLocalName();

            if (tagName.equalsIgnoreCase(nodeName) ||
                    (localName != null &&
                            tagName.equalsIgnoreCase(localName))) {

                return element.getTextContent();
            }
        }

        return null;
    }

    // =====================================================
    // ATOM LINK
    // =====================================================

    private String extractAtomLink(
            Element entry) {

        NodeList children =
                entry.getChildNodes();

        String alternateLink =
                null;

        for (int i = 0;
             i < children.getLength();
             i++) {

            Node node =
                    children.item(i);

            if (node.getNodeType()
                    != Node.ELEMENT_NODE) {

                continue;
            }

            Element link =
                    (Element) node;

            String nodeName =
                    link.getNodeName();

            String localName =
                    link.getLocalName();

            if (!"link".equalsIgnoreCase(nodeName) &&
                    !"link".equalsIgnoreCase(localName)) {

                continue;
            }

            String href =
                    link.getAttribute("href");

            if (href == null ||
                    href.isBlank()) {

                continue;
            }

            String rel =
                    link.getAttribute("rel");

            if (rel == null ||
                    rel.isBlank() ||
                    "alternate".equalsIgnoreCase(rel)) {

                return href;
            }

            if (alternateLink == null) {
                alternateLink = href;
            }
        }

        return alternateLink;
    }

    // =====================================================
    // ATOM AUTHOR
    // =====================================================

    private String extractAtomAuthor(
            Element entry) {

        NodeList children =
                entry.getChildNodes();

        for (int i = 0;
             i < children.getLength();
             i++) {

            Node node =
                    children.item(i);

            if (node.getNodeType()
                    != Node.ELEMENT_NODE) {

                continue;
            }

            Element element =
                    (Element) node;

            String nodeName =
                    element.getNodeName();

            String localName =
                    element.getLocalName();

            if (!"author".equalsIgnoreCase(nodeName) &&
                    !"author".equalsIgnoreCase(localName)) {

                continue;
            }

            String name =
                    getChildText(
                            element,
                            "name"
                    );

            if (name != null &&
                    !name.isBlank()) {

                return name;
            }

            return element.getTextContent();
        }

        return null;
    }

    // =====================================================
    // CLEAN TEXT
    // =====================================================

    private String cleanText(
            String value) {

        if (value == null) {
            return null;
        }

        return value
                .replaceAll(
                        "<[^>]+>",
                        " "
                )
                .replace(
                        "&amp;",
                        "&"
                )
                .replace(
                        "&quot;",
                        "\""
                )
                .replace(
                        "&#39;",
                        "'"
                )
                .replace(
                        "&apos;",
                        "'"
                )
                .replace(
                        "&lt;",
                        "<"
                )
                .replace(
                        "&gt;",
                        ">"
                )
                .replaceAll(
                        "\\s+",
                        " "
                )
                .trim();
    }

    // =====================================================
    // CLEAN URL
    // =====================================================

    private String cleanUrl(
            String value) {

        if (value == null) {
            return null;
        }

        return value
                .replace(
                        "&amp;",
                        "&"
                )
                .trim();
    }

    // =====================================================
    // RSS ARTICLE DTO
    // =====================================================

    public static class RssArticle {

        private final String title;
        private final String url;
        private final String description;
        private final String author;
        private final String publishedDate;
        private final String sourceName;
        private final NewsSection section;
        private final String imageUrl;

        public RssArticle(
                String title,
                String url,
                String description,
                String author,
                String publishedDate,
                String sourceName,
                NewsSection section,
                String imageUrl) {

            this.title = title;
            this.url = url;
            this.description = description;
            this.author = author;
            this.publishedDate = publishedDate;
            this.sourceName = sourceName;
            this.section = section;
            this.imageUrl = imageUrl;
        }

        public String getTitle() {
            return title;
        }

        public String getUrl() {
            return url;
        }

        public String getDescription() {
            return description;
        }

        public String getAuthor() {
            return author;
        }

        public String getPublishedDate() {
            return publishedDate;
        }

        public String getSourceName() {
            return sourceName;
        }

        public NewsSection getSection() {
            return section;
        }

        public String getImageUrl() {
            return imageUrl;
        }
    }
}