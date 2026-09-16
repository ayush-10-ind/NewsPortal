package com.newsportal.repository;

import com.newsportal.entity.ReadingHistory;
import com.newsportal.entity.User;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
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


    // =====================================================
    // RETENTION CLEANUP
    // Delete dependent reading-history rows before old News
    // rows so the seven-day cleanup cannot hit foreign keys.
    // =====================================================

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        DELETE FROM ReadingHistory h
        WHERE h.news.publishedDate < :cutoffDate
    """)
    int deleteForExpiredNews(
            @Param("cutoffDate") LocalDate cutoffDate
    );
}
