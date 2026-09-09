package com.newsportal.source;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.net.URI;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AllowedWebPageFetcherService {

    private final WebClient webClient;

    private final Set<String> allowedDomains;

    private static final Duration FETCH_TIMEOUT =
            Duration.ofSeconds(8);

    private static final int MAX_CONTENT_LENGTH =
            500_000;


    // =====================================================
    // CONSTRUCTOR
    // =====================================================

    public AllowedWebPageFetcherService(
            WebClient.Builder webClientBuilder,
            @Value("${news.fetcher.allowed-domains:}")
            String configuredDomains) {

        this.webClient =
                webClientBuilder
                        .defaultHeader(
                                HttpHeaders.ACCEPT,
                                MediaType.TEXT_HTML_VALUE
                        )
                        .build();

        this.allowedDomains =
                parseAllowedDomains(
                        configuredDomains
                );
    }


    // =====================================================
    // FETCH ARTICLE PAGE
    // =====================================================

    /**
     * Fetches HTML from a publisher page only when
     * its domain has explicitly been allowed.
     *
     * This service does NOT:
     *
     * - bypass robots restrictions
     * - bypass CAPTCHA
     * - bypass authentication
     * - bypass paywalls
     * - bypass anti-bot protection
     * - use proxy tricks
     * - spoof browser fingerprints
     *
     * A blocked response is treated as a failed fetch.
     */
    public String fetchArticlePage(
            String articleUrl) {

        if (articleUrl == null ||
                articleUrl.isBlank()) {

            System.out.println(
                    "WEB FETCH REJECTED: Empty URL."
            );

            return null;
        }


        String normalizedUrl =
                articleUrl.trim();


        // =============================================
        // URL VALIDATION
        // =============================================

        URI uri;

        try {

            uri =
                    URI.create(
                            normalizedUrl
                    );

        } catch (Exception e) {

            System.out.println(
                    "WEB FETCH REJECTED: Invalid URL."
            );

            return null;
        }


        // =============================================
        // HTTP / HTTPS ONLY
        // =============================================

        String scheme =
                uri.getScheme();


        if (scheme == null ||
                (
                        !scheme.equalsIgnoreCase("http") &&
                        !scheme.equalsIgnoreCase("https")
                )) {

            System.out.println(
                    "WEB FETCH REJECTED: Unsupported URL scheme."
            );

            return null;
        }


        // =============================================
        // DOMAIN VALIDATION
        // =============================================

        String host =
                uri.getHost();


        if (host == null ||
                host.isBlank()) {

            System.out.println(
                    "WEB FETCH REJECTED: URL has no host."
            );

            return null;
        }


        String normalizedHost =
                host.toLowerCase(
                        Locale.ENGLISH
                );


        if (!isAllowedDomain(
                normalizedHost)) {

            System.out.println(
                    "WEB FETCH REJECTED: Domain is not "
                            + "explicitly allowed."
            );

            System.out.println(
                    "Domain: "
                            + normalizedHost
            );

            return null;
        }


        // =============================================
        // PUBLIC WEB HOST SAFETY
        // =============================================

        if (isLocalOrPrivateHost(
                normalizedHost)) {

            System.out.println(
                    "WEB FETCH REJECTED: Local/private host."
            );

            return null;
        }


        // =============================================
        // FETCH
        // =============================================

        System.out.println();
        System.out.println(
                "WEB ARTICLE FETCH STARTED"
        );

        System.out.println(
                "URL: "
                        + normalizedUrl
        );

        System.out.println(
                "Domain: "
                        + normalizedHost
        );


        try {

            String html =
                    webClient
                            .get()
                            .uri(
                                    normalizedUrl
                            )
                            .header(
                                    HttpHeaders.USER_AGENT,
                                    "AgniPress-NewsFetcher/1.0"
                            )
                            .retrieve()
                            .bodyToMono(
                                    String.class
                            )
                            .timeout(
                                    FETCH_TIMEOUT
                            )
                            .block();


            if (html == null ||
                    html.isBlank()) {

                System.out.println(
                        "WEB FETCH FAILED: Empty response."
                );

                return null;
            }


            if (html.length() >
                    MAX_CONTENT_LENGTH) {

                html =
                        html.substring(
                                0,
                                MAX_CONTENT_LENGTH
                        );

                System.out.println(
                        "WEB FETCH: Response truncated "
                                + "to safe maximum size."
                );
            }


            System.out.println(
                    "WEB ARTICLE FETCH SUCCESS"
            );

            System.out.println(
                    "Characters fetched: "
                            + html.length()
            );


            return html;


        } catch (
                WebClientResponseException e) {

            System.out.println(
                    "WEB ARTICLE FETCH FAILED"
            );

            System.out.println(
                    "HTTP status: "
                            + e.getStatusCode()
            );

            /*
             * Important:
             *
             * 403 / 401 / 429 / CAPTCHA / anti-bot
             * responses are NOT bypassed.
             */

            return null;


        } catch (Exception e) {

            System.out.println(
                    "WEB ARTICLE FETCH FAILED"
            );

            System.out.println(
                    "Error: "
                            + e.getMessage()
            );

            return null;
        }
    }


    // =====================================================
    // DOMAIN CHECK
    // =====================================================

    private boolean isAllowedDomain(
            String host) {

        if (allowedDomains.isEmpty()) {

            return false;
        }


        for (String allowedDomain :
                allowedDomains) {

            String domain =
                    allowedDomain
                            .toLowerCase(
                                    Locale.ENGLISH
                            )
                            .trim();


            if (domain.isBlank()) {

                continue;
            }


            /*
             * Exact domain:
             *
             * example.com
             *
             * Also allows:
             *
             * www.example.com
             *
             * sub.example.com
             */

            if (host.equals(domain) ||
                    host.endsWith("." + domain)) {

                return true;
            }
        }


        return false;
    }


    // =====================================================
    // LOCAL / PRIVATE HOST PROTECTION
    // =====================================================

    private boolean isLocalOrPrivateHost(
            String host) {

        if (host.equals("localhost") ||
                host.equals("127.0.0.1") ||
                host.equals("0.0.0.0") ||
                host.equals("::1")) {

            return true;
        }


        /*
         * IPv4 private ranges.
         *
         * This is intentionally conservative.
         */

        if (host.startsWith("10.")) {

            return true;
        }


        if (host.startsWith("192.168.")) {

            return true;
        }


        if (host.startsWith("172.")) {

            String[] parts =
                    host.split("\\.");

            if (parts.length >= 2) {

                try {

                    int secondOctet =
                            Integer.parseInt(
                                    parts[1]
                            );

                    if (secondOctet >= 16 &&
                            secondOctet <= 31) {

                        return true;
                    }

                } catch (NumberFormatException ignored) {

                    return true;
                }
            }
        }


        return false;
    }


    // =====================================================
    // CONFIGURATION PARSER
    // =====================================================

    private Set<String> parseAllowedDomains(
            String configuredDomains) {

        if (configuredDomains == null ||
                configuredDomains.isBlank()) {

            return Collections.emptySet();
        }


        return Arrays.stream(
                        configuredDomains.split(",")
                )
                .map(
                        String::trim
                )
                .filter(
                        domain ->
                                !domain.isBlank()
                )
                .map(
                        domain ->
                                domain
                                        .toLowerCase(
                                                Locale.ENGLISH
                                        )
                )
                .collect(
                        Collectors.toSet()
                );
    }


    // =====================================================
    // STATUS
    // =====================================================

    public boolean isDomainAllowed(
            String domain) {

        if (domain == null ||
                domain.isBlank()) {

            return false;
        }


        return isAllowedDomain(
                domain
                        .trim()
                        .toLowerCase(
                                Locale.ENGLISH
                        )
        );
    }


    public List<String> getAllowedDomains() {

        return allowedDomains
                .stream()
                .sorted()
                .toList();
    }
}