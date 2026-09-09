package com.newsportal.source;

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

    private final WebClient webClient;

    private static final Duration RSS_TIMEOUT =
            Duration.ofSeconds(8);

    private static final int MAX_FEED_SIZE =
            1_000_000;


    // =====================================================
    // CONSTRUCTOR
    // =====================================================

    public RssNewsFetcherService(
            WebClient.Builder webClientBuilder) {

        this.webClient =
                webClientBuilder
                        .codecs(configurer ->
                                configurer
                                        .defaultCodecs()
                                        .maxInMemorySize(
                                                MAX_FEED_SIZE
                                        )
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

            System.out.println(
                    "RSS FETCH SKIPPED: Source is null."
            );

            return articles;
        }


        if (!source.isUsable()) {

            System.out.println(
                    "RSS FETCH SKIPPED: Source is not usable: "
                            + source.getName()
            );

            return articles;
        }


        if (source.getProviderType() == null ||
                !source.getProviderType()
                        .equalsIgnoreCase("RSS")) {

            System.out.println(
                    "RSS FETCH SKIPPED: Provider is not RSS: "
                            + source.getName()
            );

            return articles;
        }


        String endpoint =
                source.getEndpoint();


        if (endpoint == null ||
                endpoint.isBlank()) {

            System.out.println(
                    "RSS FETCH SKIPPED: Empty endpoint for "
                            + source.getName()
            );

            return articles;
        }


        System.out.println();
        System.out.println(
                "========================================"
        );

        System.out.println(
                "RSS FETCH STARTED"
        );

        System.out.println(
                "Source: "
                        + source.getName()
        );

        System.out.println(
                "Feed: "
                        + endpoint
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
                            .bodyToMono(
                                    String.class
                            )
                            .timeout(
                                    RSS_TIMEOUT
                            )
                            .block();


            if (xml == null ||
                    xml.isBlank()) {

                System.out.println(
                        "RSS FETCH FAILED: Empty response."
                );

                return articles;
            }


            if (xml.length() >
                    MAX_FEED_SIZE) {

                System.out.println(
                        "RSS FEED TOO LARGE. Truncating."
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


            System.out.println(
                    "RSS ARTICLES FOUND: "
                            + articles.size()
            );

            System.out.println(
                    "========================================"
            );


            return articles;

        } catch (Exception e) {

            System.out.println(
                    "RSS FETCH FAILED: "
                            + source.getName()
            );

            System.out.println(
                    "Error: "
                            + e.getClass().getSimpleName()
                            + " - "
                            + e.getMessage()
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


            // =================================================
            // SECURITY
            // Prevent XXE / external entity attacks
            // =================================================

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
                    document.getElementsByTagName(
                            "item"
                    );


            if (rssItems.getLength() > 0) {

                System.out.println(
                        "RSS FORMAT DETECTED"
                );


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

                        articles.add(
                                article
                        );
                    }
                }


                return articles;
            }


            // =================================================
            // ATOM
            // =================================================

            NodeList atomEntries =
                    document.getElementsByTagName(
                            "entry"
                    );


            if (atomEntries.getLength() > 0) {

                System.out.println(
                        "ATOM FORMAT DETECTED"
                );


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

                        articles.add(
                                article
                        );
                    }
                }


                return articles;
            }


            System.out.println(
                    "RSS PARSE FAILED: No <item> or <entry> elements found."
            );


        } catch (Exception e) {

            System.out.println(
                    "RSS XML PARSE FAILED: "
                            + e.getClass().getSimpleName()
                            + " - "
                            + e.getMessage()
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
                source.getSection()
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
                extractAtomLink(
                        entry
                );


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
                extractAtomAuthor(
                        entry
                );


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
                source.getSection()
        );
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


        String cleaned =
                value

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


        return cleaned;
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


        public RssArticle(
                String title,
                String url,
                String description,
                String author,
                String publishedDate,
                String sourceName,
                NewsSection section) {

            this.title = title;

            this.url = url;

            this.description = description;

            this.author = author;

            this.publishedDate = publishedDate;

            this.sourceName = sourceName;

            this.section = section;
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
    }
}