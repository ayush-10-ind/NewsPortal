package com.newsportal.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Entity
@Table(name = "notifications")
public class Notification {

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
    // NOTIFICATION CONTENT
    // =====================================================

    @Column(nullable = false, length = 255)
    private String title;


    @Column(columnDefinition = "TEXT")
    private String message;


    @Column(length = 500)
    private String link;


    // =====================================================
    // STATUS
    // =====================================================

    @Column(name = "is_read", nullable = false)
    private boolean read = false;


    // =====================================================
    // TIMESTAMP
    // =====================================================

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;


    // =====================================================
    // CONSTRUCTORS
    // =====================================================

    public Notification() {
    }


    public Notification(
            User user,
            String title,
            String message,
            String link) {

        this.user = user;
        this.title = title;
        this.message = message;
        this.link = link;
        this.read = false;
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


    public String getTitle() {
        return title;
    }


    public void setTitle(String title) {
        this.title = title;
    }


    public String getMessage() {
        return message;
    }


    public void setMessage(String message) {
        this.message = message;
    }


    public String getLink() {
        return link;
    }


    public void setLink(String link) {
        this.link = link;
    }


    public boolean isRead() {
        return read;
    }


    public void setRead(boolean read) {
        this.read = read;
    }


    public LocalDateTime getCreatedAt() {
        return createdAt;
    }


    public void setCreatedAt(
            LocalDateTime createdAt) {

        this.createdAt = createdAt;
    }
}