package com.newsportal.controller;

import com.newsportal.entity.News;
import com.newsportal.repository.NewsRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Controller
public class HomeController {

    private final NewsRepository newsRepository;

    public HomeController(NewsRepository newsRepository) {
        this.newsRepository = newsRepository;
    }


    // =====================================================
    // HOME PAGE
    // =====================================================

    @GetMapping("/")
    public String home(Model model) {

        // =================================================
        // LATEST NEWS
        // =================================================

        Page<News> latestPage =
                newsRepository.findAll(
                        PageRequest.of(
                                0,
                                8,
                                Sort.by("publishedDate").descending()
                        )
                );

        List<News> latestNews =
                latestPage.getContent();


        model.addAttribute(
                "latestNews",
                latestNews
        );


        // =================================================
        // FEATURED NEWS
        // =================================================

        News featuredNews =
                latestNews.isEmpty()
                        ? null
                        : latestNews.get(0);


        model.addAttribute(
                "featuredNews",
                featuredNews
        );


        // =================================================
        // TRENDING NEWS
        // =================================================

        List<News> trendingNews =
                newsRepository.findAll()
                        .stream()
                        .sorted(
                                Comparator.comparing(
                                        News::getViewCount,
                                        Comparator.nullsLast(
                                                Comparator.reverseOrder()
                                        )
                                )
                        )
                        .limit(5)
                        .collect(Collectors.toList());


        model.addAttribute(
                "trendingNews",
                trendingNews
        );


        // =================================================
        // CATEGORIES
        // =================================================

        List<String> categories =
                newsRepository.findAll()
                        .stream()
                        .map(News::getCategory)
                        .filter(Objects::nonNull)
                        .map(String::trim)
                        .filter(category -> !category.isEmpty())
                        .distinct()
                        .sorted()
                        .collect(Collectors.toList());


        model.addAttribute(
                "categories",
                categories
        );


        return "index";
    }

}