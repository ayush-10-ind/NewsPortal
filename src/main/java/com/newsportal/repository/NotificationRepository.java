package com.newsportal.repository;

import com.newsportal.entity.Notification;
import com.newsportal.entity.User;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRepository
        extends JpaRepository<Notification, Long> {


    // =====================================================
    // USER NOTIFICATIONS
    // =====================================================

    List<Notification>
    findByUserOrderByCreatedAtDesc(
            User user
    );


    // =====================================================
    // RECENT NOTIFICATIONS
    // =====================================================

    List<Notification>
    findTop5ByUserOrderByCreatedAtDesc(
            User user
    );


    // =====================================================
    // UNREAD NOTIFICATIONS
    // =====================================================

    long countByUserAndReadFalse(
            User user
    );
}