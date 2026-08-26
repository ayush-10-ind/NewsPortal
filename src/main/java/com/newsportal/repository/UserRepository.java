package com.newsportal.repository;

import com.newsportal.entity.User;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    // =====================================================
    // EMAIL
    // =====================================================

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    // =====================================================
    // USERNAME
    // =====================================================

    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    boolean existsByUsernameAndIdNot(
            String username,
            Long id
    );

    // =====================================================
    // PHONE
    // =====================================================

    Optional<User> findByPhone(String phone);

    boolean existsByPhone(String phone);

    boolean existsByPhoneAndIdNot(
            String phone,
            Long id
    );
}