package com.newsportal.service;

import com.newsportal.repository.BookmarkRepository;
import com.newsportal.repository.NewsRepository;
import com.newsportal.repository.ReadingHistoryRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
public class NewsRetentionService {

    private static final Logger logger = LoggerFactory.getLogger(NewsRetentionService.class);
    private static final long RETENTION_DAYS = 7;

    private final NewsRepository newsRepository;
    private final BookmarkRepository bookmarkRepository;
    private final ReadingHistoryRepository readingHistoryRepository;

    public NewsRetentionService(
            NewsRepository newsRepository,
            BookmarkRepository bookmarkRepository,
            ReadingHistoryRepository readingHistoryRepository) {
        this.newsRepository = newsRepository;
        this.bookmarkRepository = bookmarkRepository;
        this.readingHistoryRepository = readingHistoryRepository;
    }

    @Transactional
    public long deleteExpiredNews() {
        LocalDate cutoffDate = LocalDate.now().minusDays(RETENTION_DAYS);

        logger.info(
                "AgniPress seven-day news retention started: cutoffDate={}",
                cutoffDate
        );

        // News can be referenced by bookmarks and reading history.
        // Delete those dependent rows first so the News bulk delete cannot
        // violate the database foreign-key constraints.
        int deletedBookmarks =
                bookmarkRepository.deleteForExpiredNews(cutoffDate);

        int deletedHistory =
                readingHistoryRepository.deleteForExpiredNews(cutoffDate);

        int deletedNews =
                newsRepository.deleteExpiredNews(cutoffDate);

        logger.info(
                "AgniPress seven-day news retention completed: cutoffDate={}, newsDeleted={}, bookmarksDeleted={}, historyDeleted={}",
                cutoffDate,
                deletedNews,
                deletedBookmarks,
                deletedHistory
        );

        return deletedNews;
    }
}
