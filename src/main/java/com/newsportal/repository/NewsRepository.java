package com.newsportal.repository;

import com.newsportal.entity.News;
import com.newsportal.entity.NewsSourceType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface NewsRepository extends JpaRepository<News, Long> {

    @Query("""
        SELECT n
        FROM News n
        WHERE LOWER(TRIM(n.category)) = LOWER(TRIM(:category))
        AND NOT EXISTS (
            SELECT n2.id
            FROM News n2
            WHERE LOWER(TRIM(n2.category)) = LOWER(TRIM(n.category))
            AND LOWER(TRIM(n2.title)) = LOWER(TRIM(n.title))
            AND n2.id < n.id
        )
    """)
    Page<News> findByCategoryIgnoreCase(
            @Param("category") String category,
            Pageable pageable
    );

    @Query("""
        SELECT n
        FROM News n
        WHERE (
            LOWER(n.title) LIKE LOWER(CONCAT('%', :titleKeyword, '%'))
            OR
            LOWER(n.content) LIKE LOWER(CONCAT('%', :contentKeyword, '%'))
        )
        AND NOT EXISTS (
            SELECT n2.id
            FROM News n2
            WHERE LOWER(TRIM(n2.category)) = LOWER(TRIM(n.category))
            AND LOWER(TRIM(n2.title)) = LOWER(TRIM(n.title))
            AND n2.id < n.id
        )
    """)
    Page<News> findByTitleContainingIgnoreCaseOrContentContainingIgnoreCase(
            @Param("titleKeyword") String titleKeyword,
            @Param("contentKeyword") String contentKeyword,
            Pageable pageable
    );

    @Query("""
        SELECT n
        FROM News n
        WHERE LOWER(TRIM(n.category)) = LOWER(TRIM(:category))
        AND (
            LOWER(n.title) LIKE LOWER(CONCAT('%', :keyword, '%'))
            OR
            LOWER(n.content) LIKE LOWER(CONCAT('%', :keyword, '%'))
        )
        AND NOT EXISTS (
            SELECT n2.id
            FROM News n2
            WHERE LOWER(TRIM(n2.category)) = LOWER(TRIM(n.category))
            AND LOWER(TRIM(n2.title)) = LOWER(TRIM(n.title))
            AND n2.id < n.id
        )
    """)
    Page<News> findByCategoryAndKeyword(
            @Param("category") String category,
            @Param("keyword") String keyword,
            Pageable pageable
    );

    @Query("""
        SELECT n
        FROM News n
        WHERE NOT (
            LOWER(COALESCE(n.title, '')) LIKE '%coupon%'
            OR LOWER(COALESCE(n.title, '')) LIKE '%promo code%'
            OR LOWER(COALESCE(n.title, '')) LIKE '%promo codes%'
            OR LOWER(COALESCE(n.title, '')) LIKE '%discount code%'
            OR LOWER(COALESCE(n.title, '')) LIKE '%discount codes%'
            OR LOWER(COALESCE(n.title, '')) LIKE '%discount deal%'
            OR LOWER(COALESCE(n.title, '')) LIKE '%discount deals%'
            OR LOWER(COALESCE(n.title, '')) LIKE '%shop now%'
        )
        ORDER BY n.publishedDate DESC
    """)
    Page<News> findAllByOrderByPublishedDateDesc(Pageable pageable);

    Page<News> findAllByOrderByViewCountDesc(
            Pageable pageable
    );

    List<News> findTop5ByOrderByViewCountDesc();

    @Query("""
        SELECT DISTINCT n.category
        FROM News n
        WHERE n.category IS NOT NULL
        AND TRIM(n.category) <> ''
        ORDER BY n.category
    """)
    List<String> findDistinctCategories();

    @Query("""
        SELECT n
        FROM News n
        WHERE n.sourceType = :sourceType
        AND n.sourceUrl IS NOT NULL
        AND (
            n.content IS NULL
            OR n.content = :placeholder
            OR LENGTH(n.content) < 1500
        )
        ORDER BY n.id ASC
    """)
    List<News> findRepairCandidates(
            @Param("sourceType") NewsSourceType sourceType,
            @Param("placeholder") String placeholder,
            Pageable pageable
    );

    List<News> findTop3ByCategoryIgnoreCaseAndIdNotOrderByPublishedDateDesc(
            String category,
            Long id
    );

    Optional<News> findBySourceUrl(
            String sourceUrl
    );

    @Query("""
        SELECT CASE WHEN COUNT(n) > 0 THEN true ELSE false END
        FROM News n
        WHERE LOWER(TRIM(n.title)) = LOWER(TRIM(:title))
        AND LOWER(TRIM(n.category)) = LOWER(TRIM(:category))
    """)
    boolean existsByTitleAndCategoryIgnoreCase(
            @Param("title") String title,
            @Param("category") String category
    );

    List<News> findByPublishedDateBefore(
            LocalDate cutoffDate
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM News n WHERE n.publishedDate < :cutoffDate")
    int deleteExpiredNews(
            @Param("cutoffDate") LocalDate cutoffDate
    );

    List<News> findTop5ByImageUrlStartingWithOrderByIdAsc(
            String imagePrefix
    );

    long countByImageUrlStartingWith(
            String imagePrefix
    );

    List<News> findTop5ByImageUrlStartingWithAndSourceUrlIsNotNullOrderByIdAsc(
            String imagePrefix
    );

    List<News> findTop25ByImageUrlStartingWithAndSourceUrlIsNotNullOrderByPublishedDateDesc(
            String imagePrefix
    );

    @Query("""
        SELECT n
        FROM News n
        WHERE n.imageUrl LIKE CONCAT(:imagePrefix, '%')
        AND n.sourceUrl IS NOT NULL
        AND LOWER(n.sourceUrl) LIKE '%nasa.gov%'
        ORDER BY n.publishedDate DESC
    """)
    List<News> findNasaFallbackImageCandidates(
            @Param("imagePrefix") String imagePrefix,
            Pageable pageable
    );
}
