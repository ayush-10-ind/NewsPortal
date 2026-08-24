package com.newsportal.repository;

import com.newsportal.entity.OAuthAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OAuthAccountRepository
        extends JpaRepository<OAuthAccount, Long> {

    Optional<OAuthAccount> findByProviderAndProviderUserId(
            String provider,
            String providerUserId
    );

    boolean existsByProviderAndProviderUserId(
            String provider,
            String providerUserId
    );
}