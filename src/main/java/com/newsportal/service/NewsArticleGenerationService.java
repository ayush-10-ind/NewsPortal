package com.newsportal.service;

import com.newsportal.entity.News;
import com.newsportal.repository.NewsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class NewsArticleGenerationService {

    private static final Logger logger = LoggerFactory.getLogger(NewsArticleGenerationService.class);
    private static final String PLACEHOLDER = "Article content is being prepared.";
    private static final int MIN_SOURCE_LENGTH = 80;
    private static final int MAX_SOURCE_LENGTH = 12000;
    private static final int MIN_GENERATED_LENGTH = 500;
    private static final int MIN_PARAGRAPH_BREAKS = 3;

    private final NewsRepository newsRepository;
    private final WebClientAPIService webClientService;
    private final Set<Long> inFlight = ConcurrentHashMap.newKeySet();

    public NewsArticleGenerationService(NewsRepository newsRepository, WebClientAPIService webClientService) {
        this.newsRepository = newsRepository;
        this.webClientService = webClientService;
    }

    @Async("newsTaskExecutor")
    public void generateArticleAsync(Long newsId) {
        if (newsId == null || !inFlight.add(newsId)) {
            return;
        }

        try {
            News news = newsRepository.findById(newsId).orElse(null);
            if (news == null) {
                logger.warn("Ashna generation skipped: newsId={} not found", newsId);
                return;
            }

            String sourceContent = clean(news.getContent());
            if (sourceContent == null || PLACEHOLDER.equalsIgnoreCase(sourceContent)) {
                logger.warn("Ashna generation skipped: newsId={} has no usable source content", newsId);
                return;
            }

            if (sourceContent.length() < MIN_SOURCE_LENGTH) {
                logger.warn("Ashna generation skipped: newsId={} source content too short ({} chars)",
                        newsId, sourceContent.length());
                return;
            }

            logger.info("Ashna background generation started: newsId={}, sourceChars={}",
                    newsId, sourceContent.length());

            String response = webClientService.askAshna(buildPrompt(news, sourceContent));

            if (!isValidGeneratedArticle(response)) {
                logger.warn("Ashna returned unusable content: newsId={}, responseChars={}, paragraphBreaks={}",
                        newsId,
                        response == null ? 0 : response.trim().length(),
                        response == null ? 0 : countParagraphBreaks(response.trim()));
                return;
            }

            news.setContent(clean(response));
            newsRepository.save(news);
            logger.info("Ashna background generation completed: newsId={}, generatedChars={}",
                    newsId, response.trim().length());

        } catch (Exception e) {
            logger.warn("Ashna background generation failed: newsId={}, errorType={}, message={}",
                    newsId, e.getClass().getSimpleName(), e.getMessage());
        } finally {
            inFlight.remove(newsId);
        }
    }

    public String generateArticle(Long id) {
        News news = newsRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("News article not found with id: " + id));

        String sourceContent = clean(news.getContent());
        if (sourceContent == null || PLACEHOLDER.equalsIgnoreCase(sourceContent)) {
            throw new RuntimeException("No usable source content is available for this article.");
        }
        if (sourceContent.length() < MIN_SOURCE_LENGTH) {
            throw new RuntimeException("Source content is too short to safely generate this article.");
        }

        String response = webClientService.askAshna(buildPrompt(news, sourceContent));
        if (!isValidGeneratedArticle(response)) {
            throw new RuntimeException("Ashna returned unusable article content.");
        }

        String generated = clean(response);
        news.setContent(generated);
        newsRepository.save(news);
        return generated;
    }

    private String buildPrompt(News news, String sourceContent) {
        String boundedSource = sourceContent.length() <= MAX_SOURCE_LENGTH
                ? sourceContent
                : sourceContent.substring(0, MAX_SOURCE_LENGTH);

        return """
                You are a professional news editor for AgniPress.

                Turn the supplied source information into a polished original news article.

                FACTUAL INTEGRITY:
                - Use only facts supported by the supplied information.
                - Never invent names, quotes, dates, statistics, locations or events.
                - Do not claim that you researched anything externally.
                - Do not mention AI.
                - Do not copy the source word-for-word.

                ARTICLE FORMAT:
                - Return ONLY the article text.
                - No Markdown.
                - Write 5 to 7 clear, readable paragraphs.
                - Develop the supplied facts into coherent journalistic prose without inventing new facts.
                - If the source is brief, stay concise rather than fabricating details.

                TITLE:
                %s

                AUTHOR:
                %s

                SOURCE:
                %s

                DATE:
                %s

                SOURCE INFORMATION:
                %s
                """.formatted(
                news.getTitle(),
                news.getAuthor() != null ? news.getAuthor() : "Unknown",
                news.getSourceName() != null ? news.getSourceName() : "Unknown",
                news.getPublishedDate() != null ? news.getPublishedDate().toString() : "Unknown",
                boundedSource);
    }

    private boolean isValidGeneratedArticle(String response) {
        String generated = clean(response);
        return generated != null
                && generated.length() >= MIN_GENERATED_LENGTH
                && countParagraphBreaks(generated) >= MIN_PARAGRAPH_BREAKS;
    }

    private int countParagraphBreaks(String content) {
        int count = 0;
        for (int i = 0; i < content.length() - 1; i++) {
            if (content.charAt(i) == '\n' && content.charAt(i + 1) == '\n') {
                count++;
            }
        }
        return count;
    }

    private String clean(String value) {
        if (value == null) return null;
        String cleaned = value.trim();
        return cleaned.isBlank() ? null : cleaned;
    }
}
