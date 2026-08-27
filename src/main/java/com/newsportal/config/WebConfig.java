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
         * Images are packaged by Maven into:
         *
         * BOOT-INF/classes/uploads/news/
         *
         * which corresponds to:
         *
         * classpath:/uploads/news/
         *
         * URL:
         *
         * /uploads/news/<filename>
         */

        registry
                .addResourceHandler("/uploads/**")
                .addResourceLocations("classpath:/uploads/");

        System.out.println("========================================");
        System.out.println("UPLOAD RESOURCE MAPPING ENABLED");
        System.out.println("URL: /uploads/**");
        System.out.println("RESOURCE: classpath:/uploads/");
        System.out.println("========================================");
    }
}