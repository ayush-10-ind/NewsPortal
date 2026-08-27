package com.newsportal.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {

        /*
         * =====================================================
         * NEWS IMAGE RESOURCES
         * =====================================================
         *
         * Railway:
         *   /app/uploads/
         *
         * Local development:
         *   ./uploads/
         *
         * Fallback:
         *   classpath:/uploads/
         *
         * URL:
         *   /uploads/news/<filename>
         *
         * The order is intentional:
         *
         * 1. Railway persistent volume
         * 2. Local uploads folder
         * 3. Classpath fallback
         */

        registry
                .addResourceHandler("/uploads/**")
                .addResourceLocations(
                        "file:/app/uploads/",
                        "file:./uploads/",
                        "classpath:/uploads/"
                );

        System.out.println("========================================");
        System.out.println("UPLOAD RESOURCE MAPPING ENABLED");
        System.out.println("URL: /uploads/**");
        System.out.println("RAILWAY: file:/app/uploads/");
        System.out.println("LOCAL: file:./uploads/");
        System.out.println("FALLBACK: classpath:/uploads/");
        System.out.println("========================================");
    }
}