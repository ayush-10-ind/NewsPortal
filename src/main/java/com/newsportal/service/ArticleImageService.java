package com.newsportal.service;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ArticleImageService {

    private final WebClient webClient;

    private static final Duration IMAGE_TIMEOUT =
            Duration.ofSeconds(6);

    private static final Duration ARTICLE_TIMEOUT =
            Duration.ofSeconds(7);


    // =========================================================
    // IMAGE META TAG PATTERNS
    // =========================================================

    private static final Pattern OG_IMAGE_PATTERN =
            Pattern.compile(
                    "<meta[^>]+(?:property|name)\\s*=\\s*[\"']"
                            + "(?:og:image|og:image:url)"
                            + "[\"'][^>]+content\\s*=\\s*[\"']"
                            + "([^\"']+)"
                            + "[\"'][^>]*>",
                    Pattern.CASE_INSENSITIVE
            );


    private static final Pattern OG_IMAGE_REVERSE_PATTERN =
            Pattern.compile(
                    "<meta[^>]+content\\s*=\\s*[\"']"
                            + "([^\"']+)"
                            + "[\"'][^>]+(?:property|name)\\s*=\\s*[\"']"
                            + "(?:og:image|og:image:url)"
                            + "[\"'][^>]*>",
                    Pattern.CASE_INSENSITIVE
            );


    private static final Pattern TWITTER_IMAGE_PATTERN =
            Pattern.compile(
                    "<meta[^>]+(?:property|name)\\s*=\\s*[\"']"
                            + "(?:twitter:image|twitter:image:src)"
                            + "[\"'][^>]+content\\s*=\\s*[\"']"
                            + "([^\"']+)"
                            + "[\"'][^>]*>",
                    Pattern.CASE_INSENSITIVE
            );


    private static final Pattern TWITTER_IMAGE_REVERSE_PATTERN =
            Pattern.compile(
                    "<meta[^>]+content\\s*=\\s*[\"']"
                            + "([^\"']+)"
                            + "[\"'][^>]+(?:property|name)\\s*=\\s*[\"']"
                            + "(?:twitter:image|twitter:image:src)"
                            + "[\"'][^>]*>",
                    Pattern.CASE_INSENSITIVE
            );


    // =========================================================
    // CONSTRUCTOR
    // =========================================================

    public ArticleImageService(
            WebClient.Builder webClientBuilder) {

        this.webClient =
                webClientBuilder.build();
    }


    // =========================================================
    // MAIN THREE-LEVEL IMAGE RESOLUTION
    // =========================================================

    public String resolveImage(
            String newsApiImageUrl,
            String articleUrl,
            String category) {


        // =====================================================
        // LEVEL 1
        // NEWSAPI IMAGE
        // =====================================================

        System.out.println();
        System.out.println(
                "IMAGE RESOLUTION STARTED"
        );

        System.out.println(
                "Article URL: " + articleUrl
        );

        System.out.println(
                "NewsAPI image: " + newsApiImageUrl
        );


        if (isUsableImageUrl(newsApiImageUrl)) {

            System.out.println(
                    "IMAGE LEVEL 1 SUCCESS"
            );

            System.out.println(
                    "Using NewsAPI image."
            );

            return newsApiImageUrl.trim();
        }


        System.out.println(
                "IMAGE LEVEL 1 FAILED"
        );


        // =====================================================
        // LEVEL 2
        // ARTICLE OG:IMAGE / TWITTER:IMAGE
        // =====================================================

        String articleImage =
                extractArticleImage(articleUrl);


        if (isUsableImageUrl(articleImage)) {

            System.out.println(
                    "IMAGE LEVEL 2 SUCCESS"
            );

            System.out.println(
                    "Using image extracted from article page."
            );

            return articleImage.trim();
        }


        System.out.println(
                "IMAGE LEVEL 2 FAILED"
        );


        // =====================================================
        // LEVEL 3
        // AGNIPRESS FALLBACK
        // =====================================================

        String fallbackImage =
                buildFallbackImageUrl(category);


        System.out.println(
                "IMAGE LEVEL 3 USED"
        );

        System.out.println(
                "Using AgniPress category fallback: "
                        + fallbackImage
        );


        return fallbackImage;
    }


    // =========================================================
    // LEVEL 1 IMAGE VALIDATION
    // =========================================================

    private boolean isUsableImageUrl(
            String imageUrl) {

        if (imageUrl == null ||
                imageUrl.isBlank()) {

            return false;
        }


        String url =
                imageUrl.trim();


        if (!url.startsWith("http://") &&
                !url.startsWith("https://")) {

            return false;
        }


        try {

            URI uri =
                    URI.create(url);


            if (uri.getHost() == null ||
                    uri.getHost().isBlank()) {

                return false;
            }


            MediaType contentType =
                    webClient
                            .get()
                            .uri(uri)
                            .header(
                                    HttpHeaders.USER_AGENT,
                                    getBrowserUserAgent()
                            )
                            .header(
                                    HttpHeaders.ACCEPT,
                                    "image/avif,image/webp,image/apng,"
                                            + "image/svg+xml,image/*,*/*;q=0.8"
                            )
                            .exchangeToMono(response -> {

                                if (!response.statusCode()
                                        .is2xxSuccessful()) {

                                    return response
                                            .releaseBody()
                                            .then(Mono.empty());
                                }


                                MediaType type =
                                        response.headers()
                                                .contentType()
                                                .orElse(null);


                                return response
                                        .releaseBody()
                                        .thenReturn(type);
                            })
                            .timeout(IMAGE_TIMEOUT)
                            .onErrorResume(
                                    e -> Mono.empty()
                            )
                            .block();


            return contentType != null &&
                    "image".equalsIgnoreCase(
                            contentType.getType()
                    );


        } catch (Exception e) {

            return false;
        }
    }


    // =========================================================
    // LEVEL 2
    // EXTRACT IMAGE FROM ARTICLE HTML
    // =========================================================

    private String extractArticleImage(
            String articleUrl) {

        if (articleUrl == null ||
                articleUrl.isBlank()) {

            return null;
        }


        try {

            URI articleUri =
                    URI.create(
                            articleUrl.trim()
                    );


            String html =
                    webClient
                            .get()
                            .uri(articleUri)
                            .header(
                                    HttpHeaders.USER_AGENT,
                                    getBrowserUserAgent()
                            )
                            .header(
                                    HttpHeaders.ACCEPT,
                                    "text/html,"
                                            + "application/xhtml+xml,"
                                            + "application/xml;q=0.9,"
                                            + "*/*;q=0.8"
                            )
                            .retrieve()
                            .bodyToMono(
                                    String.class
                            )
                            .timeout(
                                    ARTICLE_TIMEOUT
                            )
                            .onErrorResume(
                                    e -> Mono.empty()
                            )
                            .block();


            if (html == null ||
                    html.isBlank()) {

                return null;
            }


            // =================================================
            // FIRST: OG IMAGE
            // =================================================

            String image =
                    findImage(
                            html,
                            OG_IMAGE_PATTERN
                    );


            if (image == null) {

                image =
                        findImage(
                                html,
                                OG_IMAGE_REVERSE_PATTERN
                        );
            }


            // =================================================
            // SECOND: TWITTER IMAGE
            // =================================================

            if (image == null) {

                image =
                        findImage(
                                html,
                                TWITTER_IMAGE_PATTERN
                        );
            }


            if (image == null) {

                image =
                        findImage(
                                html,
                                TWITTER_IMAGE_REVERSE_PATTERN
                        );
            }


            if (image == null ||
                    image.isBlank()) {

                return null;
            }


            // =================================================
            // RESOLVE RELATIVE URL
            // =================================================

            URI resolvedUri =
                    articleUri.resolve(
                            image.trim()
                    );


            String resolvedUrl =
                    resolvedUri.toString();


            // =================================================
            // VALIDATE EXTRACTED IMAGE
            // =================================================

            if (isUsableImageUrl(resolvedUrl)) {

                return resolvedUrl;
            }


        } catch (Exception e) {

            System.out.println(
                    "Article image extraction failed: "
                            + e.getMessage()
            );
        }


        return null;
    }


    // =========================================================
    // REGEX IMAGE EXTRACTION
    // =========================================================

    private String findImage(
            String html,
            Pattern pattern) {

        Matcher matcher =
                pattern.matcher(html);


        if (matcher.find()) {

            String image =
                    matcher.group(1);


            if (image != null &&
                    !image.isBlank()) {

                return decodeHtmlEntities(
                        image.trim()
                );
            }
        }


        return null;
    }


    // =========================================================
    // HTML ENTITY DECODER
    // =========================================================

    private String decodeHtmlEntities(
            String value) {

        if (value == null) {
            return null;
        }


        return value
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&#x27;", "'")
                .replace("&lt;", "<")
                .replace("&gt;", ">");
    }


    // =========================================================
    // LEVEL 3 FALLBACK
    // =========================================================

    private String buildFallbackImageUrl(
            String category) {

        String safeCategory =
                category == null ||
                        category.isBlank()
                        ? "General"
                        : category.trim();


        return "/images/fallback?category="
                + encodeCategory(
                        safeCategory
                );
    }


    // =========================================================
    // CATEGORY URL ENCODING
    // =========================================================

    private String encodeCategory(
            String category) {

        return category
                .replace(" ", "%20")
                .replace("&", "%26")
                .replace("?", "%3F")
                .replace("#", "%23");
    }


    // =========================================================
    // BROWSER USER AGENT
    // =========================================================

    private String getBrowserUserAgent() {

        return "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                + "AppleWebKit/537.36 "
                + "(KHTML, like Gecko) "
                + "Chrome/151.0 Safari/537.36";
    }
}