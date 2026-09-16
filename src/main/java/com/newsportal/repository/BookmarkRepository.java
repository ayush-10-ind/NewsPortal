package com.newsportal.repository;

import com.newsportal.entity.Bookmark;
import com.newsportal.entity.User;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

public interface BookmarkRepository extends JpaRepository<Bookmark, Long> {

    // =====================================================
    // CHECK IF BOOKMARK EXISTS
    // =====================================================

    boolean existsByUserIdAndNewsId(
            Long userId,
            Long newsId
    );


    // =====================================================
    // GET USER BOOKMARKS
    // =====================================================

    List<Bookmark> findByUserOrderByCreatedAtDesc(
            User user
    );


    // =====================================================
    // DELETE BOOKMARK
    // =====================================================

    @Modifying
    @Transactional
    @Query("""
        DELETE FROM Bookmark b
        WHERE b.user.id = :userId
        AND b.news.id = :newsId
    """)
    void deleteByUserIdAndNewsId(
            @Param("userId") Long userId,
            @Param("newsId") Long newsId
    );


    // =====================================================
    // RETENTION CLEANUP
    // Delete dependent bookmarks before old News rows so the
    // seven-day retention job cannot hit foreign-key constraints.
    // =====================================================

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        DELETE FROM Bookmark b
        WHERE b.news.publishedDate < :cutoffDate
    """)
    int deleteForExpiredNews(
            @Param("cutoffDate") LocalDate cutoffDate
    );

}
