package com.newsportal.source;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger logger =
            LoggerFactory.getLogger(AllowedWebPageFetcherService.class);

    private static final Duration FETCH_TIMEOUT = Duration.ofSeconds(8);
    private static final int MAX_CONTENT_LENGTH = 500_000;
    private static final String DEFAULT_ALLOWED_DOMAINS =
            "indianexpress.com,wired.com,arstechnica.com,nasa.gov,pib.gov.in";

    private final WebClient webClient;
    private final Set<String> allowedDomains;

    public AllowedWebPageFetcherService(
            WebClient.Builder webClientBuilder,
            @Value("${news.fetcher.allowed-domains:" + DEFAULT_ALLOWED_DOMAINS + "}") String configuredDomains) {

        this.webClient = webClientBuilder
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.TEXT_HTML_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT_LANGUAGE, "en-US,en;q=0.9")
                .build();

        this.allowedDomains = parseAllowedDomains(configuredDomains);
    }

    /**
     * Fetches HTML from a publisher page only when its domain has explicitly
     * been allowed. This service does not bypass robots restrictions,
     * CAPTCHA, authentication, paywalls, anti-bot protection, or use proxy tricks.
     */
    public String fetchArticlePage(String articleUrl) {

        if (articleUrl == null || articleUrl.isBlank()) {
            logger.debug("Web fetch rejected: empty URL");
            return null;
        }

        String normalizedUrl = articleUrl.trim();
        URI uri;

        try {
            uri = URI.create(normalizedUrl);
        } catch (Exception e) {
            logger.debug("Web fetch rejected: invalid URL");
            return null;
        }

        String scheme = uri.getScheme();
        if (scheme == null ||
                (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
            logger.debug("Web fetch rejected: unsupported URL scheme");
            return null;
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            logger.debug("Web fetch rejected: URL has no host");
            return null;
        }

        String normalizedHost = host.toLowerCase(Locale.ENGLISH);

        if (!isAllowedDomain(normalizedHost)) {
            logger.debug("Web fetch rejected: domain not allowed, domain={}", normalizedHost);
            return null;
        }

        if (isLocalOrPrivateHost(normalizedHost)) {
            logger.warn("Web fetch rejected: local/private host, domain={}", normalizedHost);
            return null;
        }

        try {
            String html = webClient
                    .get()
                    .uri(normalizedUrl)
                    .header(HttpHeaders.USER_AGENT, "AgniPress-NewsFetcher/1.0")
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(FETCH_TIMEOUT)
                    .block();

            if (html == null || html.isBlank()) {
                logger.debug("Web fetch returned empty response: domain={}", normalizedHost);
                return null;
            }

            if (html.length() > MAX_CONTENT_LENGTH) {
                html = html.substring(0, MAX_CONTENT_LENGTH);
                logger.debug("Web fetch response truncated: domain={}, maxChars={}",
                        normalizedHost, MAX_CONTENT_LENGTH);
            }

            logger.debug("Web article fetch successful: domain={}, characters={}",
                    normalizedHost, html.length());

            return html;

        } catch (WebClientResponseException e) {
            logger.debug("Web article fetch failed: domain={}, status={}",
                    normalizedHost, e.getStatusCode());
            return null;

        } catch (Exception e) {
            logger.debug("Web article fetch failed: domain={}, errorType={}, message={}",
                    normalizedHost,
                    e.getClass().getSimpleName(),
                    e.getMessage());
            return null;
        }
    }

    private boolean isAllowedDomain(String host) {
        if (allowedDomains.isEmpty()) {
            return false;
        }

        for (String allowedDomain : allowedDomains) {
            String domain = allowedDomain.toLowerCase(Locale.ENGLISH).trim();

            if (domain.isBlank()) {
                continue;
            }

            if (host.equals(domain) || host.endsWith("." + domain)) {
                return true;
            }
        }

        return false;
    }

    private boolean isLocalOrPrivateHost(String host) {
        if (host.equals("localhost") ||
                host.equals("127.0.0.1") ||
                host.equals("0.0.0.0") ||
                host.equals("::1")) {
            return true;
        }

        if (host.startsWith("10.") || host.startsWith("192.168.")) {
            return true;
        }

        if (host.startsWith("172.")) {
            String[] parts = host.split("\\.");

            if (parts.length >= 2) {
                try {
                    int secondOctet = Integer.parseInt(parts[1]);
                    return secondOctet >= 16 && secondOctet <= 31;
                } catch (NumberFormatException ignored) {
                    return true;
                }
            }
        }

        return false;
    }

    private Set<String> parseAllowedDomains(String configuredDomains) {
        if (configuredDomains == null || configuredDomains.isBlank()) {
            return Collections.emptySet();
        }

        return Arrays.stream(configuredDomains.split(","))
                .map(String::trim)
                .filter(domain -> !domain.isBlank())
                .map(domain -> domain.toLowerCase(Locale.ENGLISH))
                .collect(Collectors.toSet());
    }

    public boolean isDomainAllowed(String domain) {
        if (domain == null || domain.isBlank()) {
            return false;
        }

        return isAllowedDomain(domain.trim().toLowerCase(Locale.ENGLISH));
    }

    public List<String> getAllowedDomains() {
        return allowedDomains.stream().sorted().toList();
    }
}
