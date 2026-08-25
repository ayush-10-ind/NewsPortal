package com.newsportal.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(
    name = "users",
    uniqueConstraints = {
        @UniqueConstraint(columnNames = "email"),
        @UniqueConstraint(columnNames = "username"),
        @UniqueConstraint(columnNames = "phone")
    }
)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    // =====================================================
    // NAME
    // =====================================================

    @Column(nullable = false, length = 100)
    private String name;


    // =====================================================
    // USERNAME
    // =====================================================

    @Column(
        nullable = false,
        unique = true,
        length = 50
    )
    private String username;


    // =====================================================
    // EMAIL
    // =====================================================

    @Column(
        nullable = false,
        unique = true,
        length = 255
    )
    private String email;


    // =====================================================
    // PASSWORD
    // =====================================================

    /*
     * Password is NULL until the user
     * verifies their email.
     */
    @Column(length = 255)
    private String password;


    // =====================================================
    // PHONE
    // =====================================================

    @Column(unique = true, length = 20)
    private String phone;


    // =====================================================
    // PROFILE IMAGE
    // =====================================================

    @Column(
        name = "profile_image",
        length = 500
    )
    private String profileImage;


    // =====================================================
    // ENABLED
    // =====================================================

    /*
     * New accounts remain disabled until
     * email verification + password creation.
     */
    @Column(nullable = false)
    private boolean enabled = false;


    // =====================================================
    // EMAIL VERIFIED
    // =====================================================

    @Column(
        name = "email_verified",
        nullable = false
    )
    private boolean emailVerified = false;


    // =====================================================
    // DATES
    // =====================================================

    @Column(
        name = "created_at",
        nullable = false
    )
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;


    // =====================================================
    // ROLES
    // =====================================================

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "user_roles",
        joinColumns = @JoinColumn(name = "user_id"),
        inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    private Set<Role> roles = new HashSet<>();


    // =====================================================
    // CONSTRUCTORS
    // =====================================================

    public User() {
    }


    public User(
            String name,
            String username,
            String email) {

        this.name = name;
        this.username = username;
        this.email = email;

        this.enabled = false;
        this.emailVerified = false;
    }


    // =====================================================
    // LIFECYCLE
    // =====================================================

    @PrePersist
    protected void onCreate() {

        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }

        updatedAt = LocalDateTime.now();
    }


    @PreUpdate
    protected void onUpdate() {

        updatedAt = LocalDateTime.now();
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
    // NAME
    // =====================================================

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }


    // =====================================================
    // USERNAME
    // =====================================================

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }


    // =====================================================
    // EMAIL
    // =====================================================

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }


    // =====================================================
    // PASSWORD
    // =====================================================

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }


    // =====================================================
    // PHONE
    // =====================================================

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }


    // =====================================================
    // PROFILE IMAGE
    // =====================================================

    public String getProfileImage() {
        return profileImage;
    }

    public void setProfileImage(String profileImage) {
        this.profileImage = profileImage;
    }


    // =====================================================
    // ENABLED
    // =====================================================

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }


    // =====================================================
    // EMAIL VERIFIED
    // =====================================================

    public boolean isEmailVerified() {
        return emailVerified;
    }

    public void setEmailVerified(boolean emailVerified) {
        this.emailVerified = emailVerified;
    }


    // =====================================================
    // DATES
    // =====================================================

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }


    // =====================================================
    // ROLES
    // =====================================================

    public Set<Role> getRoles() {
        return roles;
    }

    public void setRoles(Set<Role> roles) {
        this.roles = roles;
    }


    public void addRole(Role role) {
        this.roles.add(role);
    }

    public void removeRole(Role role) {
        this.roles.remove(role);
    }
}