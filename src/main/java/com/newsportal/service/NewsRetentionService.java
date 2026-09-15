package com.newsportal.service;

import com.newsportal.repository.NewsRepository;

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

    public NewsRetentionService(NewsRepository newsRepository) {
        this.newsRepository = newsRepository;
    }

    @Transactional
    public long deleteExpiredNews() {
        LocalDate cutoffDate = LocalDate.now().minusDays(RETENTION_DAYS);
        int deleted = newsRepository.deleteExpiredNews(cutoffDate);

        logger.info(
                "AgniPress seven-day news retention completed: cutoffDate={}, deleted={}",
                cutoffDate,
                deleted
        );

        return deleted;
    }
}
