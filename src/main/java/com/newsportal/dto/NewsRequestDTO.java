package com.newsportal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public class NewsRequestDTO {

@NotBlank(message = "Title is required")
    @Size(max = 255, message = "Title must not exceed 255 characters")
    private String title;

@NotBlank(message = "Author is required")
    @Size(max = 100, message = "Author must not exceed 100 characters")
    private String author;

@NotBlank(message = "Category is required")
    @Size(max = 100, message = "Category must not exceed 100 characters")
    private String category;

@NotBlank(message = "Content is required")
    private String content;

@NotNull(message = "Published date is required")
    @PastOrPresent(message = "Published date cannot be in the future")
    private LocalDate publishedDate;

public NewsRequestDTO() {
    }

public NewsRequestDTO(String title, String author, String category, String content, LocalDate publishedDate) {
        this.title = title;
        this.author = author;
        this.category = category;
        this.content = content;
        this.publishedDate = publishedDate;
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
}
