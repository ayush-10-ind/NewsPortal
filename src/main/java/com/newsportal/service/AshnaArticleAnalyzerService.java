package com.newsportal.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class AshnaArticleAnalyzerService {

    private final WebClientAPIService webClientService;
    private final ObjectMapper objectMapper;

    public AshnaArticleAnalyzerService(
            WebClientAPIService webClientService,
            ObjectMapper objectMapper) {

        this.webClientService = webClientService;
        this.objectMapper = objectMapper;
    }

    // =====================================================
    // ANALYZE RAW NEWS ARTICLE
    // =====================================================

    public ArticleAnalysis analyzeArticle(
            String title,
            String sourceName,
            String sourceUrl,
            String author,
            String publishedDate,
            String rawContent) {

        if (rawContent == null || rawContent.isBlank()) {
            throw new IllegalArgumentException(
                    "Article content cannot be empty."
            );
        }

        String prompt = """

                You are the editorial intelligence system
                for AgniPress, a professional online news portal.

                Analyze the supplied news article and return
                ONLY valid JSON.

                Your job is to classify, evaluate and structure
                the article.

                IMPORTANT RULES:

                - Do not invent facts.
                - Do not invent names, dates, quotes or statistics.
                - Do not add information that is not present.
                - Do not change the meaning of the source.
                - Do not assume missing information.
                - If information is unavailable, use null.
                - Do not use Markdown.
                - Return ONLY JSON.
                - The JSON must be valid and parseable.

                REQUIRED JSON STRUCTURE:

                {
                  "section": "India | World | Sports | Anime | Business | Technology | Entertainment | Science | Gaming",
                  "qualityScore": 0,
                  "newsworthy": true,
                  "headline": "",
                  "summary": "",
                  "content": "",
                  "author": "",
                  "source": "",
                  "publishedDate": "",
                  "imageRequired": true
                }

                SECTION RULES:

                India:
                Indian national, political, social, governmental,
                regional or domestic news.

                World:
                International affairs, foreign countries,
                diplomacy, geopolitics and global events.

                Sports:
                Football, cricket, tennis, basketball, Olympics,
                athletes, teams, tournaments and other sports.

                Anime:
                Anime, manga, Japanese animation, anime studios,
                anime releases, anime characters or related news.

                Business:
                Companies, markets, finance, economy, startups,
                investments, banking and business events.

                Technology:
                Software, hardware, AI, cybersecurity, gadgets,
                internet, programming and technology companies.

                Entertainment:
                Movies, television, music, celebrities, streaming,
                awards and popular culture.

                Science:
                Space, astronomy, physics, biology, research,
                medicine-related scientific discoveries and science.

                Gaming:
                Video games, game studios, consoles, esports,
                game releases and gaming industry news.

                QUALITY SCORE:

                0-3 = poor quality, unreliable or insufficient information
                4-5 = weak article
                6-7 = acceptable article
                8-9 = high-quality article
                10 = excellent article

                NEWSWORTHY:

                true only when the article contains meaningful
                news information suitable for publication.

                SUMMARY:

                Write a concise factual summary.

                CONTENT:

                Produce a clean factual version of the supplied
                article information.

                Do NOT write a completely new story.
                Do NOT introduce facts that are not present.

                ORIGINAL ARTICLE:

                TITLE:
                %s

                SOURCE:
                %s

                SOURCE URL:
                %s

                AUTHOR:
                %s

                PUBLISHED DATE:
                %s

                CONTENT:
                %s

                """.formatted(
                safe(title),
                safe(sourceName),
                safe(sourceUrl),
                safe(author),
                safe(publishedDate),
                rawContent
        );

        String response = webClientService.askAshna(prompt);

        return parseResponse(response);
    }


    // =====================================================
    // PARSE ASHNA RESPONSE
    // =====================================================

    private ArticleAnalysis parseResponse(String response) {

        if (response == null || response.isBlank()) {

            throw new RuntimeException(
                    "Ashna returned an empty analysis response."
            );
        }

        try {

            String json = cleanJsonResponse(response);

            JsonNode root = objectMapper.readTree(json);

            String section =
                    root.path("section").asText("World");

            int qualityScore =
                    root.path("qualityScore").asInt(0);

            boolean newsworthy =
                    root.path("newsworthy").asBoolean(false);

            String headline =
                    getNullableText(root, "headline");

            String summary =
                    getNullableText(root, "summary");

            String content =
                    getNullableText(root, "content");

            String author =
                    getNullableText(root, "author");

            String source =
                    getNullableText(root, "source");

            String publishedDate =
                    getNullableText(root, "publishedDate");

            boolean imageRequired =
                    root.path("imageRequired").asBoolean(true);

            return new ArticleAnalysis(
                    section,
                    qualityScore,
                    newsworthy,
                    headline,
                    summary,
                    content,
                    author,
                    source,
                    publishedDate,
                    imageRequired
            );

        } catch (Exception e) {

            throw new RuntimeException(
                    "Failed to parse Ashna article analysis: "
                            + e.getMessage(),
                    e
            );
        }
    }


    // =====================================================
    // CLEAN JSON
    // =====================================================

    private String cleanJsonResponse(String response) {

        String cleaned = response.trim();

        /*
         * Ashna should return JSON directly.
         *
         * This cleanup simply protects us if the model
         * accidentally surrounds the JSON with code fences.
         */

        if (cleaned.startsWith("```")) {

            int firstNewLine =
                    cleaned.indexOf('\n');

            int lastFence =
                    cleaned.lastIndexOf("```");

            if (firstNewLine >= 0 &&
                    lastFence > firstNewLine) {

                cleaned = cleaned.substring(
                        firstNewLine + 1,
                        lastFence
                ).trim();
            }
        }

        return cleaned;
    }


    // =====================================================
    // NULL-SAFE TEXT
    // =====================================================

    private String getNullableText(
            JsonNode node,
            String field) {

        JsonNode value = node.get(field);

        if (value == null ||
                value.isNull()) {

            return null;
        }

        String text = value.asText();

        return text == null ||
                text.isBlank()
                ? null
                : text.trim();
    }


    private String safe(String value) {

        return value == null ||
                value.isBlank()
                ? "Unknown"
                : value;
    }


    // =====================================================
    // RESULT OBJECT
    // =====================================================

    public static class ArticleAnalysis {

        private final String section;
        private final int qualityScore;
        private final boolean newsworthy;
        private final String headline;
        private final String summary;
        private final String content;
        private final String author;
        private final String source;
        private final String publishedDate;
        private final boolean imageRequired;


        public ArticleAnalysis(
                String section,
                int qualityScore,
                boolean newsworthy,
                String headline,
                String summary,
                String content,
                String author,
                String source,
                String publishedDate,
                boolean imageRequired) {

            this.section = section;
            this.qualityScore = qualityScore;
            this.newsworthy = newsworthy;
            this.headline = headline;
            this.summary = summary;
            this.content = content;
            this.author = author;
            this.source = source;
            this.publishedDate = publishedDate;
            this.imageRequired = imageRequired;
        }


        public String getSection() {
            return section;
        }


        public int getQualityScore() {
            return qualityScore;
        }


        public boolean isNewsworthy() {
            return newsworthy;
        }


        public String getHeadline() {
            return headline;
        }


        public String getSummary() {
            return summary;
        }


        public String getContent() {
            return content;
        }


        public String getAuthor() {
            return author;
        }


        public String getSource() {
            return source;
        }


        public String getPublishedDate() {
            return publishedDate;
        }


        public boolean isImageRequired() {
            return imageRequired;
        }


        public Map<String, Object> toMap() {

            Map<String, Object> result =
                    new LinkedHashMap<>();

            result.put("section", section);
            result.put("qualityScore", qualityScore);
            result.put("newsworthy", newsworthy);
            result.put("headline", headline);
            result.put("summary", summary);
            result.put("content", content);
            result.put("author", author);
            result.put("source", source);
            result.put("publishedDate", publishedDate);
            result.put("imageRequired", imageRequired);

            return result;
        }
    }
}