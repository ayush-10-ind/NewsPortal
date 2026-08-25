package com.newsportal.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
    name = "email_verification_tokens",
    indexes = {
        @Index(
            name = "idx_verification_token",
            columnList = "token",
            unique = true
        )
    }
)
public class EmailVerificationToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    @Column(nullable = false, unique = true, length = 100)
    private String token;


    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "user_id",
        nullable = false,
        unique = true
    )
    private User user;


    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;


    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;


    public EmailVerificationToken() {
    }


    public EmailVerificationToken(User user) {

        this.user = user;

        this.token =
                UUID.randomUUID().toString();

        this.createdAt =
                LocalDateTime.now();

        this.expiresAt =
                LocalDateTime.now()
                        .plusHours(24);
    }


    public Long getId() {
        return id;
    }


    public void setId(Long id) {
        this.id = id;
    }


    public String getToken() {
        return token;
    }


    public void setToken(String token) {
        this.token = token;
    }


    public User getUser() {
        return user;
    }


    public void setUser(User user) {
        this.user = user;
    }


    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }


    public void setExpiresAt(
            LocalDateTime expiresAt) {

        this.expiresAt = expiresAt;
    }


    public LocalDateTime getCreatedAt() {
        return createdAt;
    }


    public void setCreatedAt(
            LocalDateTime createdAt) {

        this.createdAt = createdAt;
    }


    public boolean isExpired() {

        return LocalDateTime.now()
                .isAfter(expiresAt);
    }
}