package com.newsportal.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Entity
@Table(
    name = "bookmarks",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_user_news",
            columnNames = {"user_id", "news_id"}
        )
    }
)
public class Bookmark {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    // =====================================================
    // USER
    // =====================================================

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "user_id",
        nullable = false
    )
    private User user;


    // =====================================================
    // NEWS
    // =====================================================

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "news_id",
        nullable = false
    )
    private News news;


    // =====================================================
    // CREATED AT
    // =====================================================

    @Column(name = "created_at")
    private LocalDateTime createdAt;


    // =====================================================
    // CONSTRUCTORS
    // =====================================================

    public Bookmark() {
    }


    public Bookmark(User user, News news) {

        this.user = user;
        this.news = news;
    }


    // =====================================================
    // LIFECYCLE
    // =====================================================

    @PrePersist
    protected void onCreate() {

        if (createdAt == null) {

            createdAt =
                    LocalDateTime.now(
                            ZoneId.of("Asia/Kolkata")
                    );
        }
    }


    // =====================================================
    // GETTERS / SETTERS
    // =====================================================

    public Long getId() {
        return id;
    }


    public void setId(Long id) {
        this.id = id;
    }


    public User getUser() {
        return user;
    }


    public void setUser(User user) {
        this.user = user;
    }


    public News getNews() {
        return news;
    }


    public void setNews(News news) {
        this.news = news;
    }


    public LocalDateTime getCreatedAt() {
        return createdAt;
    }


    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

}