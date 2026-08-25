package com.newsportal.repository;

import com.newsportal.entity.User;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    // =========================================================
    // FIND USER BY EMAIL
    // =========================================================

    Optional<User> findByEmail(String email);


    // =========================================================
    // EMAIL CHECK
    // =========================================================

    boolean existsByEmail(String email);


    // =========================================================
    // FIND USER BY PHONE
    // =========================================================

    Optional<User> findByPhone(String phone);


    // =========================================================
    // PHONE CHECK
    // =========================================================

    boolean existsByPhone(String phone);


    // =========================================================
    // PHONE CHECK EXCLUDING CURRENT USER
    // =========================================================

    boolean existsByPhoneAndIdNot(
            String phone,
            Long id
    );
}