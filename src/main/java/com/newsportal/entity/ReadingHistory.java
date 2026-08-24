package com.newsportal.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Entity
@Table(
    name = "reading_history",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_history_user_news",
            columnNames = {"user_id", "news_id"}
        )
    }
)
public class ReadingHistory {

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
    // LAST READ
    // =====================================================

    @Column(name = "last_read_at", nullable = false)
    private LocalDateTime lastReadAt;


    // =====================================================
    // CONSTRUCTORS
    // =====================================================

    public ReadingHistory() {
    }


    public ReadingHistory(
            User user,
            News news) {

        this.user = user;
        this.news = news;
    }


    // =====================================================
    // LIFECYCLE
    // =====================================================

    @PrePersist
    protected void onCreate() {

        if (lastReadAt == null) {

            lastReadAt =
                    LocalDateTime.now(
                            ZoneId.of("Asia/Kolkata")
                    );
        }
    }


    @PreUpdate
    protected void onUpdate() {

        lastReadAt =
                LocalDateTime.now(
                        ZoneId.of("Asia/Kolkata")
                );
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


    public LocalDateTime getLastReadAt() {
        return lastReadAt;
    }


    public void setLastReadAt(
            LocalDateTime lastReadAt) {

        this.lastReadAt = lastReadAt;
    }
}