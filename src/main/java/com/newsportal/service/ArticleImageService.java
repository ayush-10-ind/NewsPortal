package com.newsportal.service;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ArticleImageService {

    private final WebClient webClient;

    /*
     * Keep external requests reasonably short.
     *
     * IMPORTANT:
     * No image bytes are stored.
     * We only validate URLs and save the URL.
     */
    private static final Duration IMAGE_TIMEOUT =
            Duration.ofSeconds(5);

    private static final Duration ARTICLE_TIMEOUT =
            Duration.ofSeconds(8);


    // =========================================================
    // META TAG PATTERNS
    // =========================================================

    private static final Pattern OG_IMAGE_PATTERN =
            Pattern.compile(
                    "<meta\\b[^>]*?(?:property|name)\\s*=\\s*[\"']"
                            + "(?:og:image|og:image:url|og:image:secure_url)"
                            + "[\"'][^>]*?(?:content)\\s*=\\s*[\"']"
                            + "([^\"']+)"
                            + "[\"'][^>]*>",
                    Pattern.CASE_INSENSITIVE
            );


    private static final Pattern OG_IMAGE_REVERSE_PATTERN =
            Pattern.compile(
                    "<meta\\b[^>]*?(?:content)\\s*=\\s*[\"']"
                            + "([^\"']+)"
                            + "[\"'][^>]*?(?:property|name)\\s*=\\s*[\"']"
                            + "(?:og:image|og:image:url|og:image:secure_url)"
                            + "[\"'][^>]*>",
                    Pattern.CASE_INSENSITIVE
            );


    private static final Pattern TWITTER_IMAGE_PATTERN =
            Pattern.compile(
                    "<meta\\b[^>]*?(?:property|name)\\s*=\\s*[\"']"
                            + "(?:twitter:image|twitter:image:src|twitter:image:url)"
                            + "[\"'][^>]*?(?:content)\\s*=\\s*[\"']"
                            + "([^\"']+)"
                            + "[\"'][^>]*>",
                    Pattern.CASE_INSENSITIVE
            );


    private static final Pattern TWITTER_IMAGE_REVERSE_PATTERN =
            Pattern.compile(
                    "<meta\\b[^>]*?(?:content)\\s*=\\s*[\"']"
                            + "([^\"']+)"
                            + "[\"'][^>]*?(?:property|name)\\s*=\\s*[\"']"
                            + "(?:twitter:image|twitter:image:src|twitter:image:url)"
                            + "[\"'][^>]*>",
                    Pattern.CASE_INSENSITIVE
            );


    // =========================================================
    // OTHER META / LINK IMAGE PATTERNS
    // =========================================================

    private static final Pattern IMAGE_SRC_LINK_PATTERN =
            Pattern.compile(
                    "<link\\b[^>]*?(?:rel)\\s*=\\s*[\"']"
                            + "[^\"']*image_src[^\"']*"
                            + "[\"'][^>]*?(?:href)\\s*=\\s*[\"']"
                            + "([^\"']+)"
                            + "[\"'][^>]*>",
                    Pattern.CASE_INSENSITIVE
            );


    private static final Pattern IMAGE_SRC_LINK_REVERSE_PATTERN =
            Pattern.compile(
                    "<link\\b[^>]*?(?:href)\\s*=\\s*[\"']"
                            + "([^\"']+)"
                            + "[\"'][^>]*?(?:rel)\\s*=\\s*[\"']"
                            + "[^\"']*image_src[^\"']*"
                            + "[\"'][^>]*>",
                    Pattern.CASE_INSENSITIVE
            );


    private static final Pattern THUMBNAIL_META_PATTERN =
            Pattern.compile(
                    "<meta\\b[^>]*?(?:name|property)\\s*=\\s*[\"']"
                            + "(?:thumbnail|thumbnailUrl|image|image:url)"
                            + "[\"'][^>]*?(?:content)\\s*=\\s*[\"']"
                            + "([^\"']+)"
                            + "[\"'][^>]*>",
                    Pattern.CASE_INSENSITIVE
            );


    private static final Pattern THUMBNAIL_META_REVERSE_PATTERN =
            Pattern.compile(
                    "<meta\\b[^>]*?(?:content)\\s*=\\s*[\"']"
                            + "([^\"']+)"
                            + "[\"'][^>]*?(?:name|property)\\s*=\\s*[\"']"
                            + "(?:thumbnail|thumbnailUrl|image|image:url)"
                            + "[\"'][^>]*>",
                    Pattern.CASE_INSENSITIVE
            );


    // =========================================================
    // JSON-LD IMAGE PATTERNS
    // =========================================================

    /*
     * Handles:
     *
     * "image": "https://example.com/image.jpg"
     *
     * "thumbnailUrl": "https://example.com/image.jpg"
     *
     * "contentUrl": "https://example.com/image.jpg"
     *
     * We deliberately search for these independently rather
     * than trying to parse the entire JSON document with a
     * JSON library. This keeps the service lightweight and
     * avoids introducing another dependency.
     */

    private static final Pattern JSON_LD_IMAGE_PATTERN =
            Pattern.compile(
                    "\"(?:image|thumbnailUrl|contentUrl)\"\\s*:\\s*"
                            + "\"((?:\\\\.|[^\"\\\\])+)\"",
                    Pattern.CASE_INSENSITIVE
            );


    /*
     * Handles JSON-LD image objects such as:
     *
     * "image": {
     *     "url": "https://..."
     * }
     *
     * and:
     *
     * "image": {
     *     "contentUrl": "https://..."
     * }
     */

    private static final Pattern JSON_LD_IMAGE_OBJECT_PATTERN =
            Pattern.compile(
                    "\"(?:image|thumbnail|thumbnailUrl)\"\\s*:\\s*\\{"
                            + "[^{}]{0,2500}?"
                            + "\"(?:url|contentUrl)\"\\s*:\\s*"
                            + "\"((?:\\\\.|[^\"\\\\])+)\"",
                    Pattern.CASE_INSENSITIVE
            );


    // =========================================================
    // GENERIC IMAGE TAG PATTERNS
    // =========================================================

    /*
     * Many publishers don't expose a usable OG image but do
     * expose the article's main image through:
     *
     * data-src
     * data-lazy-src
     * data-original
     * src
     *
     * We only consider image tags whose class/id contains
     * article/hero/featured/main/content/story/thumbnail
     * keywords. This reduces the chance of selecting logos,
     * icons, avatars or advertisements.
     */

    private static final Pattern ARTICLE_IMAGE_TAG_PATTERN =
            Pattern.compile(
                    "<img\\b[^>]*?(?:class|id)\\s*=\\s*[\"'][^\"']*"
                            + "(?:article|hero|featured|feature|main-image|main_image|"
                            + "story|content-image|content_image|thumbnail|lead|cover)"
                            + "[^\"']*[\"'][^>]*>",
                    Pattern.CASE_INSENSITIVE
            );


    private static final Pattern IMAGE_ATTRIBUTE_PATTERN =
            Pattern.compile(
                    "(?:data-src|data-lazy-src|data-original|data-image|src)"
                            + "\\s*=\\s*[\"']([^\"']+)[\"']",
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
    // MAIN IMAGE RESOLUTION
    // =========================================================

    /**
     * Resolves an article image using multiple strategies.
     *
     * Priority:
     *
     * 1. NewsAPI urlToImage
     * 2. Open Graph image
     * 3. Twitter image
     * 4. image_src link
     * 5. JSON-LD image
     * 6. thumbnail/image metadata
     * 7. Article hero/content image
     * 8. AgniPress fallback
     *
     * IMPORTANT:
     * The application never stores image binaries.
     * Only the final URL is returned.
     */
    public String resolveImage(
            String newsApiImageUrl,
            String articleUrl,
            String category) {

        System.out.println();
        System.out.println(
                "========================================"
        );
        System.out.println(
                "IMAGE RESOLUTION STARTED"
        );
        System.out.println(
                "Article URL: " + articleUrl
        );
        System.out.println(
                "NewsAPI image: " + newsApiImageUrl
        );
        System.out.println(
                "========================================"
        );


        // =====================================================
        // LEVEL 1
        // NEWSAPI
        // =====================================================

        if (isUsableImageUrl(newsApiImageUrl)) {

            System.out.println(
                    "IMAGE LEVEL 1 SUCCESS: NewsAPI"
            );

            return newsApiImageUrl.trim();
        }


        System.out.println(
                "IMAGE LEVEL 1 FAILED"
        );


        // =====================================================
        // LEVEL 2
        // PUBLISHER PAGE
        // =====================================================

        String articleImage =
                extractArticleImage(articleUrl);


        if (articleImage != null &&
                !articleImage.isBlank()) {

            System.out.println(
                    "IMAGE LEVEL 2 SUCCESS: Publisher page"
            );

            System.out.println(
                    "Image: " + articleImage
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

        String fallback =
                buildFallbackImageUrl(category);


        System.out.println(
                "IMAGE LEVEL 3 USED"
        );

        System.out.println(
                "Fallback: " + fallback
        );


        return fallback;
    }


    // =========================================================
    // IMAGE URL VALIDATION
    // =========================================================

    private boolean isUsableImageUrl(
            String imageUrl) {

        if (imageUrl == null ||
                imageUrl.isBlank()) {

            return false;
        }


        String url =
                normalizeCandidateUrl(
                        imageUrl,
                        null
                );


        if (url == null) {

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
                            .headers(headers ->
                                    applyBrowserHeaders(
                                            headers,
                                            false
                                    )
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
                            .timeout(
                                    IMAGE_TIMEOUT
                            )
                            .onErrorResume(
                                    e -> Mono.empty()
                            )
                            .block();


            if (contentType == null) {

                return false;
            }


            /*
             * Normal image response.
             */
            if ("image".equalsIgnoreCase(
                    contentType.getType())) {

                return true;
            }


            /*
             * Some CDNs don't send a correct content type.
             *
             * If the URL strongly looks like an image, allow it.
             * The browser will perform the final rendering check.
             */
            return looksLikeImageUrl(url);

        } catch (Exception e) {

            return false;
        }
    }


    // =========================================================
    // EXTRACT ARTICLE PAGE
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
                            .headers(headers ->
                                    applyBrowserHeaders(
                                            headers,
                                            true
                                    )
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

                System.out.println(
                        "Publisher HTML unavailable."
                );

                return null;
            }


            System.out.println(
                    "Publisher HTML received: "
                            + html.length()
                            + " characters"
            );


            /*
             * Keep candidates in insertion order and prevent
             * the same URL from being tested repeatedly.
             */
            Set<String> candidates =
                    new LinkedHashSet<>();


            // =================================================
            // 1. OPEN GRAPH
            // =================================================

            collectPatternCandidates(
                    html,
                    articleUri,
                    OG_IMAGE_PATTERN,
                    candidates
            );


            collectPatternCandidates(
                    html,
                    articleUri,
                    OG_IMAGE_REVERSE_PATTERN,
                    candidates
            );


            // =================================================
            // 2. TWITTER
            // =================================================

            collectPatternCandidates(
                    html,
                    articleUri,
                    TWITTER_IMAGE_PATTERN,
                    candidates
            );


            collectPatternCandidates(
                    html,
                    articleUri,
                    TWITTER_IMAGE_REVERSE_PATTERN,
                    candidates
            );


            // =================================================
            // 3. IMAGE_SRC
            // =================================================

            collectPatternCandidates(
                    html,
                    articleUri,
                    IMAGE_SRC_LINK_PATTERN,
                    candidates
            );


            collectPatternCandidates(
                    html,
                    articleUri,
                    IMAGE_SRC_LINK_REVERSE_PATTERN,
                    candidates
            );


            // =================================================
            // 4. THUMBNAIL / IMAGE META
            // =================================================

            collectPatternCandidates(
                    html,
                    articleUri,
                    THUMBNAIL_META_PATTERN,
                    candidates
            );


            collectPatternCandidates(
                    html,
                    articleUri,
                    THUMBNAIL_META_REVERSE_PATTERN,
                    candidates
            );


            // =================================================
            // 5. JSON-LD
            // =================================================

            collectPatternCandidates(
                    html,
                    articleUri,
                    JSON_LD_IMAGE_PATTERN,
                    candidates
            );


            collectPatternCandidates(
                    html,
                    articleUri,
                    JSON_LD_IMAGE_OBJECT_PATTERN,
                    candidates
            );


            // =================================================
            // 6. ARTICLE HERO / FEATURED IMAGE
            // =================================================

            collectArticleImageTagCandidates(
                    html,
                    articleUri,
                    candidates
            );


            System.out.println(
                    "Image candidates found: "
                            + candidates.size()
            );


            // =================================================
            // VALIDATE CANDIDATES
            // =================================================

            int checked =
                    0;


            /*
             * Don't hammer a publisher page forever.
             * Eight carefully ordered candidates is enough.
             */
            for (String candidate :
                    candidates) {

                if (candidate == null ||
                        candidate.isBlank()) {

                    continue;
                }


                if (checked >= 8) {

                    break;
                }


                checked++;


                System.out.println(
                        "Checking image candidate #"
                                + checked
                                + ": "
                                + candidate
                );


                if (isUsableImageUrl(
                        candidate
                )) {

                    System.out.println(
                            "VALID IMAGE FOUND"
                    );

                    return candidate;
                }
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
    // COLLECT REGEX CANDIDATES
    // =========================================================

    private void collectPatternCandidates(
            String html,
            URI articleUri,
            Pattern pattern,
            Set<String> candidates) {

        Matcher matcher =
                pattern.matcher(html);


        while (matcher.find()) {

            if (matcher.groupCount() < 1) {

                continue;
            }


            String raw =
                    matcher.group(1);


            String resolved =
                    normalizeCandidateUrl(
                            raw,
                            articleUri
                    );


            if (resolved != null) {

                candidates.add(
                        resolved
                );
            }
        }
    }


    // =========================================================
    // ARTICLE IMAGE TAG EXTRACTION
    // =========================================================

    private void collectArticleImageTagCandidates(
            String html,
            URI articleUri,
            Set<String> candidates) {

        Matcher imageTagMatcher =
                ARTICLE_IMAGE_TAG_PATTERN
                        .matcher(html);


        while (imageTagMatcher.find()) {

            String imageTag =
                    imageTagMatcher.group();


            Matcher attributeMatcher =
                    IMAGE_ATTRIBUTE_PATTERN
                            .matcher(imageTag);


            while (attributeMatcher.find()) {

                String raw =
                        attributeMatcher.group(1);


                String resolved =
                        normalizeCandidateUrl(
                                raw,
                                articleUri
                        );


                if (resolved != null) {

                    candidates.add(
                            resolved
                    );
                }
            }


            /*
             * srcset can contain multiple image URLs.
             */
            collectSrcsetCandidates(
                    imageTag,
                    articleUri,
                    candidates
            );
        }
    }


    // =========================================================
    // SRCSET
    // =========================================================

    private void collectSrcsetCandidates(
            String htmlFragment,
            URI articleUri,
            Set<String> candidates) {

        Pattern srcsetPattern =
                Pattern.compile(
                        "(?:srcset|data-srcset)"
                                + "\\s*=\\s*[\"']"
                                + "([^\"']+)"
                                + "[\"']",
                        Pattern.CASE_INSENSITIVE
                );


        Matcher matcher =
                srcsetPattern.matcher(
                        htmlFragment
                );


        while (matcher.find()) {

            String srcset =
                    matcher.group(1);


            String[] entries =
                    srcset.split(",");


            /*
             * Usually the last/highest-resolution candidate
             * is the best one.
             */
            for (int i =
                    entries.length - 1;
                 i >= 0;
                 i--) {

                String entry =
                        entries[i].trim();


                if (entry.isBlank()) {

                    continue;
                }


                String[] parts =
                        entry.split("\\s+");


                if (parts.length == 0) {

                    continue;
                }


                String resolved =
                        normalizeCandidateUrl(
                                parts[0],
                                articleUri
                        );


                if (resolved != null) {

                    candidates.add(
                            resolved
                    );
                }
            }
        }
    }


    // =========================================================
    // NORMALIZE IMAGE URL
    // =========================================================

    private String normalizeCandidateUrl(
            String rawUrl,
            URI articleUri) {

        if (rawUrl == null ||
                rawUrl.isBlank()) {

            return null;
        }


        String url =
                rawUrl.trim();


        /*
         * Remove HTML escaping commonly found in JSON-LD.
         */
        url =
                url.replace(
                        "\\/",
                        "/"
                );


        url =
                url.replace(
                        "&amp;",
                        "&"
                );


        /*
         * JSON escaped quotes/backslashes.
         */
        url =
                url.replace(
                        "\\\"",
                        "\""
                );


        /*
         * Ignore obvious placeholders.
         */
        if (isPlaceholderImage(url)) {

            return null;
        }


        try {

            /*
             * Protocol-relative URL:
             *
             * //cdn.example.com/image.jpg
             */
            if (url.startsWith("//")) {

                return "https:" + url;
            }


            /*
             * Already absolute.
             */
            if (url.startsWith("http://") ||
                    url.startsWith("https://")) {

                URI uri =
                        URI.create(url);


                if (uri.getHost() == null ||
                        uri.getHost().isBlank()) {

                    return null;
                }


                return uri.toString();
            }


            /*
             * Relative URL from publisher page.
             */
            if (articleUri != null) {

                URI resolved =
                        articleUri.resolve(url);


                if (resolved.getHost() == null ||
                        resolved.getHost().isBlank()) {

                    return null;
                }


                return resolved.toString();
            }

        } catch (Exception e) {

            return null;
        }


        return null;
    }


    // =========================================================
    // PLACEHOLDER DETECTION
    // =========================================================

    private boolean isPlaceholderImage(
            String url) {

        String lower =
                url.toLowerCase();


        return lower.contains(
                    "placeholder"
                )
                || lower.contains(
                    "placehold"
                )
                || lower.contains(
                    "default-image"
                )
                || lower.contains(
                    "default_image"
                )
                || lower.contains(
                    "no-image"
                )
                || lower.contains(
                    "no_image"
                )
                || lower.contains(
                    "image-not-found"
                )
                || lower.contains(
                    "image_not_found"
                )
                || lower.contains(
                    "spacer.gif"
                )
                || lower.contains(
                    "transparent.gif"
                );
    }


    // =========================================================
    // IMAGE URL HEURISTIC
    // =========================================================

    private boolean looksLikeImageUrl(
            String url) {

        String lower =
                url.toLowerCase();


        /*
         * Strip query string before extension checking.
         */
        int questionMark =
                lower.indexOf("?");


        if (questionMark >= 0) {

            lower =
                    lower.substring(
                            0,
                            questionMark
                    );
        }


        int hash =
                lower.indexOf("#");


        if (hash >= 0) {

            lower =
                    lower.substring(
                            0,
                            hash
                    );
        }


        return lower.endsWith(".jpg")
                || lower.endsWith(".jpeg")
                || lower.endsWith(".png")
                || lower.endsWith(".webp")
                || lower.endsWith(".avif")
                || lower.endsWith(".gif")
                || lower.endsWith(".svg")
                || lower.contains(
                    "/image/"
                )
                || lower.contains(
                    "/images/"
                )
                || lower.contains(
                    "/img/"
                )
                || lower.contains(
                    "/photo/"
                )
                || lower.contains(
                    "/photos/"
                )
                || lower.contains(
                    "/media/"
                );
    }


    // =========================================================
    // BROWSER HEADERS
    // =========================================================

    private void applyBrowserHeaders(
            HttpHeaders headers,
            boolean htmlRequest) {

        headers.set(
                HttpHeaders.USER_AGENT,
                getBrowserUserAgent()
        );


        headers.set(
                HttpHeaders.ACCEPT_LANGUAGE,
                "en-US,en;q=0.9"
        );


        headers.set(
                HttpHeaders.CONNECTION,
                "keep-alive"
        );


        if (htmlRequest) {

            headers.set(
                    HttpHeaders.ACCEPT,
                    "text/html,"
                            + "application/xhtml+xml,"
                            + "application/xml;q=0.9,"
                            + "image/avif,image/webp,"
                            + "*/*;q=0.8"
            );

        } else {

            headers.set(
                    HttpHeaders.ACCEPT,
                    "image/avif,image/webp,image/apng,"
                            + "image/svg+xml,image/*,"
                            + "*/*;q=0.8"
            );
        }
    }


    // =========================================================
    // USER AGENT
    // =========================================================

    private String getBrowserUserAgent() {

        return "Mozilla/5.0 "
                + "(Windows NT 10.0; Win64; x64) "
                + "AppleWebKit/537.36 "
                + "(KHTML, like Gecko) "
                + "Chrome/131.0.0.0 "
                + "Safari/537.36";
    }


    // =========================================================
    // FALLBACK
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
    // CATEGORY ENCODING
    // =========================================================

    private String encodeCategory(
            String category) {

        return category
                .replace(
                        " ",
                        "%20"
                )
                .replace(
                        "&",
                        "%26"
                )
                .replace(
                        "?",
                        "%3F"
                )
                .replace(
                        "#",
                        "%23"
                );
    }
}