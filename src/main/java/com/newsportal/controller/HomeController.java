package com.newsportal.controller;

import com.newsportal.entity.News;
import com.newsportal.repository.NewsRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@Controller
public class HomeController {

    private final NewsRepository newsRepository;

    public HomeController(NewsRepository newsRepository) {
        this.newsRepository = newsRepository;
    }

    @GetMapping("/")
    public String home(Model model) {

        // Keep the homepage request small. Only fetch the records that the
        // template actually needs instead of loading the complete news table.
        Page<News> latestPage = newsRepository.findAll(
                PageRequest.of(
                        0,
                        8,
                        Sort.by("publishedDate").descending()
                )
        );

        List<News> latestNews = latestPage.getContent();
        model.addAttribute("latestNews", latestNews);

        News featuredNews = latestNews.stream()
                .findFirst()
                .orElse(null);

        model.addAttribute("featuredNews", featuredNews);

        // Trending news is now limited by the database query. This avoids
        // loading every News entity and sorting the complete table in Java.
        List<News> trendingNews = newsRepository.findTop5ByOrderByViewCountDesc();
        model.addAttribute("trendingNews", trendingNews);

        // Categories are returned directly from the database instead of
        // loading every News entity just to extract a string field.
        List<String> categories = newsRepository.findDistinctCategories();
        model.addAttribute("categories", categories);

        return "index";
    }
}
