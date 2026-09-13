package com.newsportal.source;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Extracts factual article text from a publisher page when an RSS description
 * is unavailable or unusable. The extracted text is only used as source
 * material; Ashna remains responsible for writing the final AgniPress article.
 */
@Service
public class WebArticleContentExtractorService {

    private static final Logger logger = LoggerFactory.getLogger(WebArticleContentExtractorService.class);

    private static final int MIN_ARTICLE_LENGTH = 80;
    private static final int MAX_ARTICLE_LENGTH = 30000;

    private final AllowedWebPageFetcherService pageFetcherService;
    private final ObjectMapper objectMapper;

    public WebArticleContentExtractorService(
            AllowedWebPageFetcherService pageFetcherService,
            ObjectMapper objectMapper) {
        this.pageFetcherService = pageFetcherService;
        this.objectMapper = objectMapper;
    }

    public String fetchArticleText(String articleUrl) {
        String html = pageFetcherService.fetchArticlePage(articleUrl);
        if (html == null || html.isBlank()) {
            return null;
        }

        try {
            Document document = Jsoup.parse(html, articleUrl);

            String jsonLdText = extractJsonLdArticleBody(document);
            if (isUsable(jsonLdText)) {
                return limitAndNormalize(jsonLdText);
            }

            removeNonArticleElements(document);

            List<Element> candidates = new ArrayList<>();
            collectSelectorCandidates(document, candidates,
                    "article",
                    "[itemprop=articleBody]",
                    "[class*=article-body]",
                    "[class*=article-content]",
                    "[class*=story-content]",
                    "[class*=story-body]",
                    "[class*=post-content]",
                    "[class*=entry-content]");

            Element main = document.select("main").first();
            if (main != null) {
                candidates.add(main);
            }

            return candidates.stream()
                    .map(this::candidateText)
                    .filter(this::isUsable)
                    .max(Comparator.comparingInt(this::scoreText))
                    .map(this::limitAndNormalize)
                    .orElse(null);

        } catch (Exception e) {
            logger.debug("Web article extraction failed: errorType={}, message={}",
                    e.getClass().getSimpleName(), e.getMessage());
            return null;
        }
    }

    private void collectSelectorCandidates(
            Document document,
            List<Element> candidates,
            String... selectors) {
        Set<Element> seen = new HashSet<>();

        for (String selector : selectors) {
            Elements elements = document.select(selector);
            for (Element element : elements) {
                if (seen.add(element)) {
                    candidates.add(element);
                }
            }
        }
    }

    private String extractJsonLdArticleBody(Document document) {
        for (Element script : document.select("script[type=application/ld+json]")) {
            String json = script.data();
            if (json == null || json.isBlank()) {
                json = script.html();
            }

            String body = findArticleBody(json);
            if (isUsable(body)) {
                return body;
            }
        }

        return null;
    }

    private String findArticleBody(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }

        try {
            JsonNode root = objectMapper.readTree(json);
            return findArticleBodyNode(root);
        } catch (Exception ignored) {
            // Some publishers emit multiple JSON-LD objects or slightly
            // malformed JSON. DOM extraction below remains the fallback.
            return null;
        }
    }

    private String findArticleBodyNode(JsonNode node) {
        if (node == null) {
            return null;
        }

        if (node.isObject()) {
            JsonNode articleBody = node.get("articleBody");
            if (articleBody != null && articleBody.isTextual() &&
                    isUsable(articleBody.asText())) {
                return articleBody.asText();
            }

            var fields = node.fields();
            while (fields.hasNext()) {
                JsonNode value = fields.next().getValue();
                String result = findArticleBodyNode(value);
                if (isUsable(result)) {
                    return result;
                }
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                String result = findArticleBodyNode(child);
                if (isUsable(result)) {
                    return result;
                }
            }
        }

        return null;
    }

    private void removeNonArticleElements(Document document) {
        document.select("script, style, noscript, template, svg, nav, header, footer, aside, form, iframe")
                .remove();
    }

    private String candidateText(Element element) {
        if (element == null) {
            return null;
        }

        StringBuilder builder = new StringBuilder();
        for (Element paragraph : element.select("p")) {
            String text = paragraph.text();
            if (text.length() >= 25) {
                if (!builder.isEmpty()) {
                    builder.append("\n\n");
                }
                builder.append(text);
            }
        }

        String paragraphs = builder.toString().trim();
        if (paragraphs.length() >= MIN_ARTICLE_LENGTH) {
            return paragraphs;
        }

        return element.text();
    }

    private int scoreText(String text) {
        if (text == null) {
            return 0;
        }

        int lengthScore = Math.min(text.length(), MAX_ARTICLE_LENGTH);
        int paragraphScore = text.split("\\R\\s*\\R").length * 250;
        return lengthScore + paragraphScore;
    }

    private boolean isUsable(String text) {
        return text != null && text.trim().length() >= MIN_ARTICLE_LENGTH;
    }

    private String limitAndNormalize(String text) {
        String normalized = text
                .replace('\u00a0', ' ')
                .replaceAll("[ \\t]+", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();

        if (normalized.length() > MAX_ARTICLE_LENGTH) {
            normalized = normalized.substring(0, MAX_ARTICLE_LENGTH).trim();
        }

        return normalized;
    }
}
