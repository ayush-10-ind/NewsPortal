package com.newsportal.service;

import com.newsportal.entity.News;
import com.newsportal.repository.NewsRepository;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Periodically retries image resolution for articles that are currently
 * using the AgniPress fallback image. This allows images that could not be
 * extracted during import to be recovered later without re-importing news.
 */
@Service
public class NewsImageRepairService {

    private static final Logger logger =
            LoggerFactory.getLogger(NewsImageRepairService.class);

    private static final String FALLBACK_PREFIX = "/images/fallback";
    private static final int BATCH_SIZE = 5;
    private static final long INITIAL_DELAY_SECONDS = 25;
    private static final long DELAY_SECONDS = 60;

    private final NewsRepository newsRepository;
    private final ArticleImageService articleImageService;
    private final TransactionTemplate transactionTemplate;

    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(
                        runnable,
                        "news-image-repair-scheduler"
                );
                thread.setDaemon(true);
                return thread;
            });

    public NewsImageRepairService(
            NewsRepository newsRepository,
            ArticleImageService articleImageService,
            TransactionTemplate transactionTemplate) {

        this.newsRepository = newsRepository;
        this.articleImageService = articleImageService;
        this.transactionTemplate = transactionTemplate;
    }

    @PostConstruct
    public void initializeRepairMonitor() {
        scheduler.scheduleWithFixedDelay(
                this::repairCycle,
                INITIAL_DELAY_SECONDS,
                DELAY_SECONDS,
                TimeUnit.SECONDS
        );

        logger.info("News image repair monitor initialized");
    }

    private void repairCycle() {

        try {
            List<News> candidates =
                    newsRepository
                            .findTop5ByImageUrlStartingWithAndSourceUrlIsNotNullOrderByIdAsc(
                                    FALLBACK_PREFIX
                            );

            if (candidates.isEmpty()) {
                return;
            }

            int repaired = 0;

            for (News news : candidates) {
                try {
                    String sourceUrl = news.getSourceUrl();

                    if (sourceUrl == null || sourceUrl.isBlank()) {
                        continue;
                    }

                    String resolvedImage =
                            articleImageService.resolveImage(
                                    null,
                                    sourceUrl,
                                    news.getCategory()
                            );

                    if (!isRealImage(resolvedImage)) {
                        continue;
                    }

                    Boolean saved = transactionTemplate.execute(status -> {
                        News current =
                                newsRepository.findById(news.getId()).orElse(null);

                        if (current == null ||
                                !isFallbackImage(current.getImageUrl())) {
                            return false;
                        }

                        current.setImageUrl(resolvedImage);
                        newsRepository.save(current);
                        return true;
                    });

                    if (Boolean.TRUE.equals(saved)) {
                        repaired++;
                        logger.info(
                                "Recovered news image: articleId={}",
                                news.getId()
                        );
                    }

                } catch (Exception ex) {
                    logger.debug(
                            "News image retry skipped: articleId={}, errorType={}",
                            news.getId(),
                            ex.getClass().getSimpleName()
                    );
                }
            }

            if (repaired > 0) {
                logger.info(
                        "News image repair cycle completed: repaired={}",
                        repaired
                );
            }

        } catch (Exception ex) {
            logger.warn(
                    "News image repair cycle failed: errorType={}, message={}",
                    ex.getClass().getSimpleName(),
                    ex.getMessage()
            );
        }
    }

    private boolean isFallbackImage(String imageUrl) {
        return imageUrl != null &&
                imageUrl.startsWith(FALLBACK_PREFIX);
    }

    private boolean isRealImage(String imageUrl) {
        return imageUrl != null &&
                !imageUrl.isBlank() &&
                !isFallbackImage(imageUrl);
    }
}
