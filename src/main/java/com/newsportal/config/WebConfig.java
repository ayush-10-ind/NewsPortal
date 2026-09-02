package com.newsportal.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    /*
     * =====================================================
     * AGNIPRESS WEB CONFIGURATION
     * =====================================================
     *
     * News images are NOT stored locally.
     *
     * ArticleImageService stores only:
     *
     * 1. NewsAPI external image URLs
     * 2. Publisher metadata image URLs
     * 3. AgniPress dynamic fallback URLs
     *
     * Therefore we intentionally DO NOT configure
     * /uploads/** as a static resource location.
     *
     * News images are rendered directly by the browser
     * from their external URLs.
     *
     * Example:
     *
     * https://publisher.com/image.jpg
     *
     * or:
     *
     * /images/fallback?category=Technology
     *
     * No local image storage is required.
     */

}