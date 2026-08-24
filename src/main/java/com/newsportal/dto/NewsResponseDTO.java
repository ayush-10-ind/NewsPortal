package com.newsportal.dto;

import java.time.LocalDate;

public class NewsResponseDTO {

    private Long id;

    private String title;

    private String author;

    private String category;

    private String content;

    private LocalDate publishedDate;

    private String imageUrl;

    private String sourceUrl;

    private String sourceName;

    private String sourceType;


    // =====================================================
    // CONSTRUCTORS
    // =====================================================

    public NewsResponseDTO() {
    }

    public NewsResponseDTO(
            Long id,
            String title,
            String author,
            String category,
            String content,
            LocalDate publishedDate,
            String imageUrl,
            String sourceUrl,
            String sourceName,
            String sourceType) {

        this.id = id;
        this.title = title;
        this.author = author;
        this.category = category;
        this.content = content;
        this.publishedDate = publishedDate;
        this.imageUrl = imageUrl;
        this.sourceUrl = sourceUrl;
        this.sourceName = sourceName;
        this.sourceType = sourceType;
    }


    // =====================================================
    // GETTERS
    // =====================================================

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getAuthor() {
        return author;
    }

    public String getCategory() {
        return category;
    }

    public String getContent() {
        return content;
    }

    public LocalDate getPublishedDate() {
        return publishedDate;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public String getSourceName() {
        return sourceName;
    }

    public String getSourceType() {
        return sourceType;
    }


    // =====================================================
    // SETTERS
    // =====================================================

    public void setId(Long id) {
        this.id = id;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public void setPublishedDate(LocalDate publishedDate) {
        this.publishedDate = publishedDate;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public void setSourceUrl(String sourceUrl) {
        this.sourceUrl = sourceUrl;
    }

    public void setSourceName(String sourceName) {
        this.sourceName = sourceName;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }
}