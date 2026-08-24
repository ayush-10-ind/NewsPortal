package com.newsportal.service;

import com.newsportal.dto.NewsRequestDTO;
import com.newsportal.dto.NewsResponseDTO;
import com.newsportal.entity.News;
import com.newsportal.exception.ResourceNotFoundException;
import com.newsportal.mapper.NewsMapper;
import com.newsportal.repository.NewsRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
public class NewsService {

private final NewsRepository newsRepository;

@Autowired
    public NewsService(NewsRepository newsRepository) {
        this.newsRepository = newsRepository;
    }

public Page<NewsResponseDTO> getAllNews(Pageable pageable) {
        return newsRepository.findAll(pageable)
                .map(NewsMapper::toResponseDTO);
    }

public NewsResponseDTO getNewsById(Long id) {
        News news = newsRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("News not found with id: " + id));

return NewsMapper.toResponseDTO(news);
    }

public NewsResponseDTO createNews(NewsRequestDTO requestDTO) {
        News news = NewsMapper.toEntity(requestDTO);
        News savedNews = newsRepository.save(news);

return NewsMapper.toResponseDTO(savedNews);
    }

public NewsResponseDTO updateNews(Long id, NewsRequestDTO requestDTO) {
        News existingNews = newsRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("News not found with id: " + id));

NewsMapper.updateEntityFromDTO(existingNews, requestDTO);

News updatedNews = newsRepository.save(existingNews);

return NewsMapper.toResponseDTO(updatedNews);
    }

public void deleteNews(Long id) {
        News existingNews = newsRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("News not found with id: " + id));

newsRepository.delete(existingNews);
    }

public Page<NewsResponseDTO> getNewsByCategory(String category, Pageable pageable) {
        return newsRepository.findByCategoryIgnoreCase(category, pageable)
                .map(NewsMapper::toResponseDTO);
    }

public Page<NewsResponseDTO> searchNews(String keyword, Pageable pageable) {
        return newsRepository
                .findByTitleContainingIgnoreCaseOrContentContainingIgnoreCase(keyword, keyword, pageable)
                .map(NewsMapper::toResponseDTO);
    }

public Page<NewsResponseDTO> getLatestNews(Pageable pageable) {
        return newsRepository.findAllByOrderByPublishedDateDesc(pageable)
                .map(NewsMapper::toResponseDTO);
    }
}
