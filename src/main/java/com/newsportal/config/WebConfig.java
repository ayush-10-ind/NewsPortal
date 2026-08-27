package com.newsportal.config;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {

        /*
         * =====================================================
         * FILESYSTEM UPLOADS
         * =====================================================
         *
         * Used for images stored in:
         *
         * uploads/news/
         *
         * This works locally and also supports images created
         * while the application is running.
         */

        Path uploadPath = Paths.get("uploads")
                .toAbsolutePath()
                .normalize();

        String uploadLocation = uploadPath.toUri().toString();

        if (!uploadLocation.endsWith("/")) {
            uploadLocation += "/";
        }

        /*
         * =====================================================
         * RESOURCE MAPPING
         * =====================================================
         *
         * First:
         *   filesystem uploads/
         *
         * Second:
         *   images packaged inside the application JAR
         *
         * This gives us a fallback for Railway deployments.
         */

        registry
                .addResourceHandler("/uploads/**")
                .addResourceLocations(
                        uploadLocation,
                        "classpath:/uploads/"
                );

        System.out.println("========================================");
        System.out.println("UPLOAD RESOURCE MAPPING ENABLED");
        System.out.println("URL: /uploads/**");
        System.out.println("FILESYSTEM FOLDER: " + uploadPath);
        System.out.println("CLASSPATH FALLBACK: classpath:/uploads/");
        System.out.println("========================================");
    }
}