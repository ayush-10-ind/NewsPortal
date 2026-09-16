package com.newsportal.controller;

import com.newsportal.entity.News;
import com.newsportal.repository.NewsRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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

        // Use the same editorially filtered latest-news query as the news list.
        // This hides already-stored coupon/promotional records from the homepage.
        Page<News> latestPage = newsRepository.findAllByOrderByPublishedDateDesc(
                PageRequest.of(0, 8)
        );

        List<News> latestNews = latestPage.getContent();
        model.addAttribute("latestNews", latestNews);

        News featuredNews = latestNews.stream()
                .findFirst()
                .orElse(null);

        model.addAttribute("featuredNews", featuredNews);

        // Trending news is limited by the database query.
        List<News> trendingNews = newsRepository.findTop5ByOrderByViewCountDesc();
        model.addAttribute("trendingNews", trendingNews);

        // Categories are returned directly from the database.
        List<String> categories = newsRepository.findDistinctCategories();
        model.addAttribute("categories", categories);

        return "index";
    }
}
