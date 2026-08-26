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

    // =====================================================
    // ID
    // =====================================================

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    // =====================================================
    // TOKEN
    // =====================================================

    @Column(
        nullable = false,
        unique = true,
        length = 100
    )
    private String token;


    // =====================================================
    // USER
    // =====================================================

    /*
     * Each user can have only ONE active verification token.
     *
     * When a new verification email is requested,
     * the old token is deleted and replaced.
     */

    @OneToOne(
        fetch = FetchType.LAZY,
        optional = false
    )
    @JoinColumn(
        name = "user_id",
        nullable = false,
        unique = true
    )
    private User user;


    // =====================================================
    // EXPIRATION
    // =====================================================

    @Column(
        name = "expires_at",
        nullable = false
    )
    private LocalDateTime expiresAt;


    // =====================================================
    // CREATED AT
    // =====================================================

    @Column(
        name = "created_at",
        nullable = false
    )
    private LocalDateTime createdAt;


    // =====================================================
    // CONSTRUCTORS
    // =====================================================

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


    // =====================================================
    // ID
    // =====================================================

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }


    // =====================================================
    // TOKEN
    // =====================================================

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }


    // =====================================================
    // USER
    // =====================================================

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }


    // =====================================================
    // EXPIRATION
    // =====================================================

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(
            LocalDateTime expiresAt) {

        this.expiresAt = expiresAt;
    }


    // =====================================================
    // CREATED AT
    // =====================================================

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(
            LocalDateTime createdAt) {

        this.createdAt = createdAt;
    }


    // =====================================================
    // EXPIRATION CHECK
    // =====================================================

    public boolean isExpired() {

        return LocalDateTime.now()
                .isAfter(expiresAt);
    }
}