package com.newsportal.repository;

import com.newsportal.entity.EmailVerificationToken;
import com.newsportal.entity.User;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EmailVerificationTokenRepository
        extends JpaRepository<EmailVerificationToken, Long> {

    // =====================================================
    // FIND TOKEN
    // =====================================================

    Optional<EmailVerificationToken> findByToken(
            String token
    );


    // =====================================================
    // FIND USER'S TOKEN
    // =====================================================

    Optional<EmailVerificationToken> findByUser(
            User user
    );


    // =====================================================
    // DELETE USER'S OLD TOKEN
    // =====================================================

    void deleteByUser(
            User user
    );


    // =====================================================
    // DELETE ALL USER TOKENS
    // =====================================================

    void deleteAllByUser(
            User user
    );
}