package com.newsportal.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "oauth_accounts",
    uniqueConstraints = {
        @UniqueConstraint(
            columnNames = {"provider", "provider_user_id"}
        )
    }
)
public class OAuthAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /*
     * The News Portal user who owns this
     * Google/GitHub account.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /*
     * Example:
     * GOOGLE
     * GITHUB
     */
    @Column(nullable = false, length = 30)
    private String provider;

    /*
     * The unique ID supplied by Google/GitHub.
     */
    @Column(name = "provider_user_id", nullable = false, length = 255)
    private String providerUserId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;


    // =========================
    // CONSTRUCTORS
    // =========================

    public OAuthAccount() {
    }

    public OAuthAccount(User user, String provider, String providerUserId) {
        this.user = user;
        this.provider = provider;
        this.providerUserId = providerUserId;
    }


    // =========================
    // LIFECYCLE
    // =========================

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }


    // =========================
    // GETTERS AND SETTERS
    // =========================

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

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getProviderUserId() {
        return providerUserId;
    }

    public void setProviderUserId(String providerUserId) {
        this.providerUserId = providerUserId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}