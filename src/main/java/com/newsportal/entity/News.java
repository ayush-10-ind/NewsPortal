package com.newsportal.entity;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "news")
public class News {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    // =====================================================
    // NEWS INFORMATION
    // =====================================================

    @Column(nullable = false, length = 255)
    private String title;


    @Column(length = 100)
    private String author;


    /*
     * Existing category field.
     *
     * We are keeping this for compatibility
     * with the existing database.
     */
    @Column(length = 100)
    private String category;


    @Column(columnDefinition = "TEXT")
    private String content;


    @Column(name = "published_date")
    private LocalDate publishedDate;


    /*
     * Image path / URL.
     */
    @Column(name = "image_url", length = 500)
    private String imageUrl;
    
 // =====================================================
 // EXTERNAL NEWS API INFORMATION
 // =====================================================

 @Column(name = "source_url", length = 1000)
 private String sourceUrl;

 @Column(name = "source_name", length = 255)
 private String sourceName;

 @Enumerated(EnumType.STRING)
 @Column(name = "source_type", nullable = false)
 private NewsSourceType sourceType = NewsSourceType.MANUAL;


    // =====================================================
    // VIEW COUNT
    // =====================================================

    @Column(
        name = "view_count",
        nullable = false
    )
    private Long viewCount = 0L;


    // =====================================================
    // NEW RELATIONSHIPS
    // =====================================================

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category categoryEntity;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;


    // =====================================================
    // TIMESTAMPS
    // =====================================================

    @Column(name = "created_at")
    private LocalDateTime createdAt;


    @Column(name = "updated_at")
    private LocalDateTime updatedAt;


    // =====================================================
    // CONSTRUCTORS
    // =====================================================

    public News() {
    }


    public News(
            String title,
            String author,
            String category,
            String content,
            LocalDate publishedDate) {

        this.title = title;
        this.author = author;
        this.category = category;
        this.content = content;
        this.publishedDate = publishedDate;
        this.viewCount = 0L;
    }


    // =====================================================
    // LIFECYCLE
    // =====================================================

    @PrePersist
    protected void onCreate() {

        LocalDateTime now = LocalDateTime.now();

        createdAt = now;
        updatedAt = now;

        if (viewCount == null) {
            viewCount = 0L;
        }
    }


    @PreUpdate
    protected void onUpdate() {

        updatedAt = LocalDateTime.now();

        if (viewCount == null) {
            viewCount = 0L;
        }
    }


    // =====================================================
    // GETTERS AND SETTERS
    // =====================================================

    public Long getId() {
        return id;
    }


    public void setId(Long id) {
        this.id = id;
    }


    public String getTitle() {
        return title;
    }


    public void setTitle(String title) {
        this.title = title;
    }


    public String getAuthor() {
        return author;
    }


    public void setAuthor(String author) {
        this.author = author;
    }


    public String getCategory() {
        return category;
    }


    public void setCategory(String category) {
        this.category = category;
    }


    public String getContent() {
        return content;
    }


    public void setContent(String content) {
        this.content = content;
    }


    public LocalDate getPublishedDate() {
        return publishedDate;
    }


    public void setPublishedDate(LocalDate publishedDate) {
        this.publishedDate = publishedDate;
    }


    public String getImageUrl() {
        return imageUrl;
    }


    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }
    
    public String getSourceUrl() {
        return sourceUrl;
    }

    public void setSourceUrl(String sourceUrl) {
        this.sourceUrl = sourceUrl;
    }

    public String getSourceName() {
        return sourceName;
    }

    public void setSourceName(String sourceName) {
        this.sourceName = sourceName;
    }

    public NewsSourceType getSourceType() {
        return sourceType;
    }

    public void setSourceType(NewsSourceType sourceType) {
        this.sourceType = sourceType;
    }


    // =====================================================
    // VIEW COUNT GETTER / SETTER
    // =====================================================

    public Long getViewCount() {
        return viewCount;
    }


    public void setViewCount(Long viewCount) {
        this.viewCount = viewCount;
    }


    // =====================================================
    // CATEGORY RELATIONSHIP
    // =====================================================

    public Category getCategoryEntity() {
        return categoryEntity;
    }


    public void setCategoryEntity(Category categoryEntity) {
        this.categoryEntity = categoryEntity;
    }


    // =====================================================
    // CREATED BY
    // =====================================================

    public User getCreatedBy() {
        return createdBy;
    }


    public void setCreatedBy(User createdBy) {
        this.createdBy = createdBy;
    }


    // =====================================================
    // TIMESTAMPS
    // =====================================================

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }


    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

}