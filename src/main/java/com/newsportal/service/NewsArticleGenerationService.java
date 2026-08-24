package com.newsportal.service;

import com.newsportal.entity.News;
import com.newsportal.repository.NewsRepository;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class NewsArticleGenerationService {

    private final NewsRepository newsRepository;
    private final WebClientAPIService webClientService;


    public NewsArticleGenerationService(
            NewsRepository newsRepository,
            WebClientAPIService webClientService) {

        this.newsRepository = newsRepository;
        this.webClientService = webClientService;
    }


    // =====================================================
    // BACKGROUND ASHNA GENERATION
    // =====================================================

    @Async("newsTaskExecutor")
    public void generateArticleAsync(Long newsId) {

        try {

            News news = newsRepository
                    .findById(newsId)
                    .orElse(null);


            if (news == null) {

                System.out.println(
                        "News not found for Ashna generation: "
                                + newsId
                );

                return;
            }


            System.out.println(
                    "Ashna generation started for: "
                            + news.getTitle()
            );


            String prompt = """

                    You are a professional news editor
                    for a modern online news portal.

                    Rewrite the information below into an
                    original, factual and readable news article.

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

                    Write approximately 5 to 7 paragraphs.

                    """.formatted(

                    news.getTitle(),

                    news.getAuthor() != null
                            ? news.getAuthor()
                            : "Unknown",

                    news.getSourceName() != null
                            ? news.getSourceName()
                            : "Unknown",

                    news.getPublishedDate() != null
                            ? news.getPublishedDate().toString()
                            : "Unknown",

                    news.getContent() != null
                            ? news.getContent()
                            : ""
            );


            String response =
                    webClientService.askAshna(prompt);


            if (response != null &&
                    !response.isBlank()) {

                news.setContent(
                        response.trim()
                );

                newsRepository.save(news);


                System.out.println(
                        "Ashna generation completed: "
                                + news.getTitle()
                );

            } else {

                System.out.println(
                        "Ashna returned empty content for: "
                                + news.getTitle()
                );
            }


        } catch (Exception e) {

            System.out.println(
                    "Ashna generation failed for news ID "
                            + newsId
            );

            System.out.println(
                    "Error: "
                            + e.getMessage()
            );
        }
    }


    // =====================================================
    // SYNCHRONOUS GENERATION
    // Used by /generate/{id}
    // =====================================================

    public String generateArticle(Long id) {

        News news = newsRepository
                .findById(id)
                .orElseThrow(() ->
                        new RuntimeException(
                                "News article not found with id: "
                                        + id
                        )
                );


        String prompt = """

                You are a professional news editor.

                Rewrite the following information into
                an original, factual news article.

                IMPORTANT RULES:

                - Do not invent facts.
                - Do not invent quotes.
                - Do not add unsupported information.
                - Do not copy the source word-for-word.
                - Keep the article factual.
                - Use professional journalism.
                - Use short readable paragraphs.
                - Do not use Markdown.
                - Do not mention AI.
                - Return ONLY the article text.

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

                Write approximately 5 to 7 paragraphs.

                """.formatted(

                news.getTitle(),

                news.getAuthor() != null
                        ? news.getAuthor()
                        : "Unknown",

                news.getSourceName() != null
                        ? news.getSourceName()
                        : "Unknown",

                news.getPublishedDate() != null
                        ? news.getPublishedDate().toString()
                        : "Unknown",

                news.getContent() != null
                        ? news.getContent()
                        : ""
        );


        String response =
                webClientService.askAshna(prompt);


        if (response == null ||
                response.isBlank()) {

            throw new RuntimeException(
                    "Ashna returned an empty response."
            );
        }


        news.setContent(
                response.trim()
        );

        newsRepository.save(news);


        return response.trim();
    }
}