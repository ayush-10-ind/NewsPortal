package com.newsportal.controller;

import com.newsportal.dto.NewsRequestDTO;
import com.newsportal.dto.NewsResponseDTO;
import com.newsportal.service.NewsService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/news")
@CrossOrigin(origins = "*")
public class NewsController {

private final NewsService newsService;

@Autowired
    public NewsController(NewsService newsService) {
        this.newsService = newsService;
    }

@GetMapping
    public ResponseEntity<Page<NewsResponseDTO>> getAllNews(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size,
            @RequestParam(defaultValue = "publishedDate") String sortBy,
            @RequestParam(defaultValue = "desc") String direction
    ) {
        Pageable pageable = createPageable(page, size, sortBy, direction);
        Page<NewsResponseDTO> news = newsService.getAllNews(pageable);

return ResponseEntity.ok(news);
    }

@GetMapping("/{id}")
    public ResponseEntity<NewsResponseDTO> getNewsById(@PathVariable Long id) {
        NewsResponseDTO news = newsService.getNewsById(id);

return ResponseEntity.ok(news);
    }

@PostMapping
    public ResponseEntity<NewsResponseDTO> createNews(@Valid @RequestBody NewsRequestDTO requestDTO) {
        NewsResponseDTO createdNews = newsService.createNews(requestDTO);

return new ResponseEntity<>(createdNews, HttpStatus.CREATED);
    }

@PutMapping("/{id}")
    public ResponseEntity<NewsResponseDTO> updateNews(
            @PathVariable Long id,
            @Valid @RequestBody NewsRequestDTO requestDTO
    ) {
        NewsResponseDTO updatedNews = newsService.updateNews(id, requestDTO);

return ResponseEntity.ok(updatedNews);
    }

@DeleteMapping("/{id}")
    public ResponseEntity<String> deleteNews(@PathVariable Long id) {
        newsService.deleteNews(id);

return ResponseEntity.ok("News deleted successfully with id: " + id);
    }

@GetMapping("/category/{category}")
    public ResponseEntity<Page<NewsResponseDTO>> getNewsByCategory(
            @PathVariable String category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size,
            @RequestParam(defaultValue = "publishedDate") String sortBy,
            @RequestParam(defaultValue = "desc") String direction
    ) {
        Pageable pageable = createPageable(page, size, sortBy, direction);
        Page<NewsResponseDTO> news = newsService.getNewsByCategory(category, pageable);

return ResponseEntity.ok(news);
    }

@GetMapping("/search/{keyword}")
    public ResponseEntity<Page<NewsResponseDTO>> searchNews(
            @PathVariable String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size,
            @RequestParam(defaultValue = "publishedDate") String sortBy,
            @RequestParam(defaultValue = "desc") String direction
    ) {
        Pageable pageable = createPageable(page, size, sortBy, direction);
        Page<NewsResponseDTO> news = newsService.searchNews(keyword, pageable);

return ResponseEntity.ok(news);
    }

@GetMapping("/latest")
    public ResponseEntity<Page<NewsResponseDTO>> getLatestNews(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        Page<NewsResponseDTO> news = newsService.getLatestNews(pageable);

return ResponseEntity.ok(news);
    }

private Pageable createPageable(int page, int size, String sortBy, String direction) {
        Sort sort = direction.equalsIgnoreCase("asc")
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();

return PageRequest.of(page, size, sort);
    }
}
