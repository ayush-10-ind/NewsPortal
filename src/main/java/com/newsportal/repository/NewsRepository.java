package com.newsportal.repository;

import com.newsportal.entity.News;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface NewsRepository extends JpaRepository<News, Long> {

    // =====================================================
    // CATEGORY
    // =====================================================

    Page<News> findByCategoryIgnoreCase(
            String category,
            Pageable pageable
    );


    // =====================================================
    // SEARCH
    // =====================================================

    Page<News> findByTitleContainingIgnoreCaseOrContentContainingIgnoreCase(
            String titleKeyword,
            String contentKeyword,
            Pageable pageable
    );


    // =====================================================
    // CATEGORY + SEARCH
    // =====================================================

    @Query("""
        SELECT n
        FROM News n
        WHERE LOWER(n.category) = LOWER(:category)
        AND (
            LOWER(n.title) LIKE LOWER(CONCAT('%', :keyword, '%'))
            OR
            LOWER(n.content) LIKE LOWER(CONCAT('%', :keyword, '%'))
        )
    """)
    Page<News> findByCategoryAndKeyword(
            @Param("category") String category,
            @Param("keyword") String keyword,
            Pageable pageable
    );


    // =====================================================
    // LATEST NEWS
    // =====================================================

    Page<News> findAllByOrderByPublishedDateDesc(
            Pageable pageable
    );


    // =====================================================
    // MOST VIEWED
    // =====================================================

    Page<News> findAllByOrderByViewCountDesc(
            Pageable pageable
    );


    // =====================================================
    // RELATED NEWS
    // =====================================================

    List<News> findTop3ByCategoryIgnoreCaseAndIdNotOrderByPublishedDateDesc(
            String category,
            Long id
    );


    // =====================================================
    // EXTERNAL NEWS DUPLICATE CHECK
    // =====================================================

    Optional<News> findBySourceUrl(
            String sourceUrl
    );


    // =====================================================
    // FIND NEWS OLDER THAN 7 DAYS
    // =====================================================

    List<News> findByPublishedDateBefore(
            LocalDate cutoffDate
    );


    // =====================================================
    // IMAGE MIGRATION
    //
    // Only fetch a small number of old local-image records.
    // This avoids loading the entire news table into memory.
    // =====================================================

    List<News> findTop5ByImageUrlStartingWithOrderByIdAsc(
            String imagePrefix
    );


    // =====================================================
    // IMAGE MIGRATION COUNT
    // =====================================================

    long countByImageUrlStartingWith(
            String imagePrefix
    );
}