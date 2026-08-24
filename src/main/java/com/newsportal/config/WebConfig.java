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
         * Serve files stored in:
         *
         * project-folder/uploads/
         *
         * Example:
         * uploads/news/abc.jpg
         *
         * will be available in browser as:
         *
         * http://localhost:8082/uploads/news/abc.jpg
         */

        Path uploadPath =
                Paths.get("uploads")
                        .toAbsolutePath()
                        .normalize();

        String uploadLocation =
                uploadPath.toUri().toString();

        if (!uploadLocation.endsWith("/")) {
            uploadLocation += "/";
        }

        System.out.println(
                "========================================"
        );

        System.out.println(
                "UPLOAD RESOURCE MAPPING ENABLED"
        );

        System.out.println(
                "URL: /uploads/**"
        );

        System.out.println(
                "FOLDER: " + uploadPath
        );

        System.out.println(
                "========================================"
        );

        registry
                .addResourceHandler("/uploads/**")
                .addResourceLocations(uploadLocation);
    }
}