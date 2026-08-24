package com.newsportal.mapper;

import com.newsportal.dto.NewsRequestDTO;
import com.newsportal.dto.NewsResponseDTO;
import com.newsportal.entity.News;

public class NewsMapper {

    private NewsMapper() {
    }

    // =====================================================
    // DTO → ENTITY
    // Used for manually created news
    // =====================================================

    public static News toEntity(NewsRequestDTO requestDTO) {

        News news = new News();

        news.setTitle(requestDTO.getTitle());

        news.setAuthor(requestDTO.getAuthor());

        news.setCategory(requestDTO.getCategory());

        news.setContent(requestDTO.getContent());

        news.setPublishedDate(requestDTO.getPublishedDate());

        return news;
    }

    // =====================================================
    // ENTITY → RESPONSE DTO
    // =====================================================

    public static NewsResponseDTO toResponseDTO(News news) {

        NewsResponseDTO responseDTO = new NewsResponseDTO();

        responseDTO.setId(news.getId());

        responseDTO.setTitle(news.getTitle());

        responseDTO.setAuthor(news.getAuthor());

        responseDTO.setCategory(news.getCategory());

        responseDTO.setContent(news.getContent());

        responseDTO.setPublishedDate(news.getPublishedDate());

        // =================================================
        // EXTERNAL NEWS INFORMATION
        // =================================================

        responseDTO.setImageUrl(
                news.getImageUrl()
        );

        responseDTO.setSourceUrl(
                news.getSourceUrl()
        );

        responseDTO.setSourceName(
                news.getSourceName()
        );

        if (news.getSourceType() != null) {

            responseDTO.setSourceType(
                    news.getSourceType().name()
            );
        }

        return responseDTO;
    }

    // =====================================================
    // UPDATE ENTITY FROM REQUEST DTO
    // Used for manually edited news
    // =====================================================

    public static void updateEntityFromDTO(
            News news,
            NewsRequestDTO requestDTO) {

        news.setTitle(
                requestDTO.getTitle()
        );

        news.setAuthor(
                requestDTO.getAuthor()
        );

        news.setCategory(
                requestDTO.getCategory()
        );

        news.setContent(
                requestDTO.getContent()
        );

        news.setPublishedDate(
                requestDTO.getPublishedDate()
        );
    }
}