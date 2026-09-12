package com.newsportal.service;

import com.newsportal.entity.News;
import com.newsportal.repository.NewsRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class NewsArticleGenerationService {

    private static final Logger logger = LoggerFactory.getLogger(NewsArticleGenerationService.class);

    private static final String PLACEHOLDER = "Article content is being prepared.";
    private static final int MIN_SOURCE_LENGTH = 80;
    private static final int MIN_GENERATED_LENGTH = 500;
    private static final int MIN_PARAGRAPH_BREAKS = 3;

    private final NewsRepository newsRepository;
    private final WebClientAPIService webClientService;

    public NewsArticleGenerationService(NewsRepository newsRepository, WebClientAPIService webClientService) {
        this.newsRepository = newsRepository;
        this.webClientService = webClientService;
    }

    @Async("newsTaskExecutor")
    public void generateArticleAsync(Long newsId) {
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

            logger.debug("Ashna background generation started: newsId={}", newsId);

            String prompt = buildPrompt(news, sourceContent);
            String response = webClientService.askAshna(prompt);

            if (!isValidGeneratedArticle(response)) {
                logger.warn("Ashna returned unusable content: newsId={}", newsId);
                return;
            }

            news.setContent(clean(response));
            newsRepository.saveAndFlush(news);
            logger.info("Ashna background generation completed: newsId={}", newsId);

        } catch (Exception e) {
            logger.warn(
                    "Ashna background generation failed: newsId={}, errorType={}, message={}",
                    newsId,
                    e.getClass().getSimpleName(),
                    e.getMessage()
            );
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
        newsRepository.saveAndFlush(news);

        return generated;
    }

    private String buildPrompt(News news, String sourceContent) {
        return """
                You are a professional news editor for a modern online news portal.

                Rewrite the information below into an original, factual and readable news article.

                IMPORTANT RULES:
                - Do not invent facts.
                - Do not invent quotes.
                - Do not add unsupported information.
                - Do not copy the source word-for-word.
                - Keep all facts consistent with the source.
                - Use professional journalism.
                - Use short readable paragraphs.
                - Do not use Markdown.
                - Do not mention AI.
                - Return ONLY the article text.
                - Write approximately 5 to 7 substantial paragraphs.
                - Do not turn a short source into a list of unsupported details.

                TITLE:
                %s

                AUTHOR:
                %s

                SOURCE:
                %s

                DATE:
                %s

                INFORMATION:
                %s
                """.formatted(
                news.getTitle(),
                news.getAuthor() != null ? news.getAuthor() : "Unknown",
                news.getSourceName() != null ? news.getSourceName() : "Unknown",
                news.getPublishedDate() != null ? news.getPublishedDate().toString() : "Unknown",
                sourceContent
        );
    }

    private boolean isValidGeneratedArticle(String response) {
        String generated = clean(response);

        if (generated == null || generated.length() < MIN_GENERATED_LENGTH) {
            return false;
        }

        return countParagraphBreaks(generated) >= MIN_PARAGRAPH_BREAKS;
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
