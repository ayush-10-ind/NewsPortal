package com.newsportal.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.ContentCachingResponseWrapper;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Adds the news-list preview treatment without changing the article body
 * stored in the database. Full article content remains available on the
 * article detail page.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class NewsListPresentationFilter extends OncePerRequestFilter {

    private static final String PREVIEW_STYLE = """
            <style id="agnipress-news-list-preview">
                .news-card-description {
                    display: -webkit-box !important;
                    -webkit-box-orient: vertical !important;
                    -webkit-line-clamp: 4 !important;
                    overflow: hidden !important;
                    max-height: 6.2em;
                    position: relative;
                }
            </style>
            """;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return !("/news".equals(uri) || "/newsList".equals(uri));
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        ContentCachingResponseWrapper wrapper =
                new ContentCachingResponseWrapper(response);

        filterChain.doFilter(request, wrapper);

        byte[] body = wrapper.getContentAsByteArray();
        String contentType = wrapper.getContentType();

        if (body.length > 0 &&
                contentType != null &&
                contentType.toLowerCase().contains("text/html")) {

            Charset charset = wrapper.getCharacterEncoding() == null
                    ? StandardCharsets.UTF_8
                    : Charset.forName(wrapper.getCharacterEncoding());

            String html = new String(body, charset);

            if (!html.contains("id=\"agnipress-news-list-preview\"")) {
                int headEnd = html.toLowerCase().indexOf("</head>");

                if (headEnd >= 0) {
                    html = html.substring(0, headEnd)
                            + PREVIEW_STYLE
                            + html.substring(headEnd);
                    body = html.getBytes(charset);
                }
            }
        }

        response.setContentLength(body.length);
        response.getOutputStream().write(body);
    }
}
