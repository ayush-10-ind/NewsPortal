package com.newsportal.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Loads AgniPress' shared responsive stylesheet into rendered HTML pages.
 * This keeps mobile styling centralized instead of duplicating media queries
 * across individual Thymeleaf templates.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 10)
public class ResponsiveStylesheetFilter extends OncePerRequestFilter {

    private static final String RESPONSIVE_LINK =
            "<link id=\"agnipress-responsive-css\" rel=\"stylesheet\" " +
            "href=\"/responsive.css?v=20260913\">";

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

        if (body.length > 0
                && contentType != null
                && contentType.toLowerCase().contains("text/html")) {

            Charset charset = wrapper.getCharacterEncoding() == null
                    ? StandardCharsets.UTF_8
                    : Charset.forName(wrapper.getCharacterEncoding());

            String html = new String(body, charset);

            if (!html.contains("id=\"agnipress-responsive-css\"")) {
                int headEnd = html.toLowerCase().indexOf("</head>");

                if (headEnd >= 0) {
                    html = html.substring(0, headEnd)
                            + RESPONSIVE_LINK
                            + html.substring(headEnd);
                    body = html.getBytes(charset);
                }
            }
        }

        response.setContentLength(body.length);
        response.getOutputStream().write(body);
    }
}
