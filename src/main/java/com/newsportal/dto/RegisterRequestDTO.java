package com.newsportal.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class RegisterRequestDTO {

    // =====================================================
    // FULL NAME
    // =====================================================

    @NotBlank(message = "Name is required")
    @Size(
        max = 100,
        message = "Name must not exceed 100 characters"
    )
    private String name;


    // =====================================================
    // USERNAME
    // =====================================================

    @NotBlank(message = "Username is required")
    @Size(
        min = 3,
        max = 50,
        message = "Username must be between 3 and 50 characters"
    )
    @Pattern(
        regexp = "^[a-zA-Z0-9_]+$",
        message = "Username can contain only letters, numbers and underscores"
    )
    private String username;


    // =====================================================
    // EMAIL
    // =====================================================

    @NotBlank(message = "Email is required")
    @Email(message = "Please enter a valid email address")
    @Size(
        max = 255,
        message = "Email address is too long"
    )
    private String email;


    // =====================================================
    // CONSTRUCTOR
    // =====================================================

    public RegisterRequestDTO() {
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
}