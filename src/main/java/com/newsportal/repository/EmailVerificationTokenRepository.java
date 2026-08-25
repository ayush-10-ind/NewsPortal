package com.newsportal.repository;

import com.newsportal.entity.EmailVerificationToken;
import com.newsportal.entity.User;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EmailVerificationTokenRepository
        extends JpaRepository<EmailVerificationToken, Long> {

    Optional<EmailVerificationToken> findByToken(
            String token
    );

    Optional<EmailVerificationToken> findByUser(
            User user
    );

    void deleteByUser(User user);
}