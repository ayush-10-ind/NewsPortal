package com.newsportal.repository;

import com.newsportal.entity.ReadingHistory;
import com.newsportal.entity.User;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReadingHistoryRepository
        extends JpaRepository<ReadingHistory, Long> {


    // =====================================================
    // FIND EXISTING HISTORY
    // =====================================================

    Optional<ReadingHistory> findByUserIdAndNewsId(
            Long userId,
            Long newsId
    );


    // =====================================================
    // USER READING HISTORY
    // =====================================================

    List<ReadingHistory> findByUserOrderByLastReadAtDesc(
            User user
    );


    // =====================================================
    // COUNT ARTICLES READ BY USER
    // =====================================================

    long countByUser(
            User user
    );
}