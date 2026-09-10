package com.newsportal.service;

import com.newsportal.entity.News;
import com.newsportal.repository.NewsRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class NewsCleanupService {

    private static final Logger logger = LoggerFactory.getLogger(NewsCleanupService.class);

    private final NewsRepository newsRepository;

    public NewsCleanupService(NewsRepository newsRepository) {
        this.newsRepository = newsRepository;
    }

    @Transactional
    public int deleteOldNews() {
        LocalDate cutoffDate = LocalDate.now().minusDays(7);

        logger.info("AgniPress old news cleanup started: cutoffDate={}", cutoffDate);

        List<News> oldNews = newsRepository.findByPublishedDateBefore(cutoffDate);

        if (oldNews == null || oldNews.isEmpty()) {
            logger.info("AgniPress old news cleanup completed: deleted=0");
            return 0;
        }

        int deletedCount = 0;

        for (News news : oldNews) {
            try {
                newsRepository.delete(news);
                deletedCount++;
            } catch (Exception e) {
                logger.warn(
                        "Old news deletion failed: id={}, errorType={}, message={}",
                        news.getId(),
                        e.getClass().getSimpleName(),
                        e.getMessage()
                );
            }
        }

        logger.info(
                "AgniPress old news cleanup completed: found={}, deleted={}, failed={}",
                oldNews.size(),
                deletedCount,
                oldNews.size() - deletedCount
        );

        return deletedCount;
    }
}