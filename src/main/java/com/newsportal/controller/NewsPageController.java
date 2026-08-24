package com.newsportal.controller;

import com.newsportal.entity.News;
import com.newsportal.entity.ReadingHistory;
import com.newsportal.entity.User;

import com.newsportal.repository.BookmarkRepository;
import com.newsportal.repository.NewsRepository;
import com.newsportal.repository.ReadingHistoryRepository;
import com.newsportal.repository.UserRepository;

import com.newsportal.service.NotificationService;

import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;

import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Controller
public class NewsPageController {

    private final NewsRepository newsRepository;
    private final BookmarkRepository bookmarkRepository;
    private final UserRepository userRepository;
    private final ReadingHistoryRepository readingHistoryRepository;
    private final NotificationService notificationService;


    @Autowired
    public NewsPageController(
            NewsRepository newsRepository,
            BookmarkRepository bookmarkRepository,
            UserRepository userRepository,
            ReadingHistoryRepository readingHistoryRepository,
            NotificationService notificationService) {

        this.newsRepository = newsRepository;
        this.bookmarkRepository = bookmarkRepository;
        this.userRepository = userRepository;
        this.readingHistoryRepository = readingHistoryRepository;
        this.notificationService = notificationService;
    }


    // =====================================================
    // NEWS LIST PAGE
    // SEARCH + CATEGORY + SORTING + PAGINATION
    // =====================================================

    @GetMapping({"/news", "/newsList"})
    public String newsList(

            @RequestParam(defaultValue = "0")
            int page,

            @RequestParam(defaultValue = "")
            String search,

            @RequestParam(defaultValue = "")
            String category,

            @RequestParam(defaultValue = "latest")
            String sort,

            Model model) {


        if (page < 0) {
            page = 0;
        }


        search =
                search == null
                        ? ""
                        : search.trim();


        category =
                category == null
                        ? ""
                        : category.trim();


        sort =
                sort == null
                        ? "latest"
                        : sort.trim().toLowerCase();


        Sort sorting;


        switch (sort) {

            case "oldest":

                sorting =
                        Sort.by(
                                "publishedDate"
                        ).ascending();

                break;


            case "mostviewed":

                sorting =
                        Sort.by(
                                "viewCount"
                        ).descending();

                sort = "mostViewed";

                break;


            case "latest":
            default:

                sorting =
                        Sort.by(
                                "publishedDate"
                        ).descending();

                sort = "latest";

                break;
        }


        Pageable pageable =
                PageRequest.of(
                        page,
                        6,
                        sorting
                );


        Page<News> newsPage;


        boolean hasSearch =
                !search.isEmpty();


        boolean hasCategory =
                !category.isEmpty()
                        && !category.equalsIgnoreCase("all");


        if (hasSearch && hasCategory) {

            newsPage =
                    newsRepository
                            .findByCategoryAndKeyword(
                                    category,
                                    search,
                                    pageable
                            );

        } else if (hasSearch) {

            newsPage =
                    newsRepository
                            .findByTitleContainingIgnoreCaseOrContentContainingIgnoreCase(
                                    search,
                                    search,
                                    pageable
                            );

        } else if (hasCategory) {

            newsPage =
                    newsRepository
                            .findByCategoryIgnoreCase(
                                    category,
                                    pageable
                            );

        } else {

            newsPage =
                    newsRepository.findAll(
                            pageable
                    );
        }


        model.addAttribute(
                "newsPage",
                newsPage
        );


        model.addAttribute(
                "newsList",
                newsPage.getContent()
        );


        model.addAttribute(
                "search",
                search
        );


        model.addAttribute(
                "selectedCategory",
                category
        );


        model.addAttribute(
                "selectedSort",
                sort
        );


        // =================================================
        // TRENDING NEWS
        // =================================================

        Pageable trendingPageable =
                PageRequest.of(
                        0,
                        5,
                        Sort.by(
                                "viewCount"
                        ).descending()
                );


        Page<News> trendingPage =
                newsRepository.findAll(
                        trendingPageable
                );


        model.addAttribute(
                "trendingNews",
                trendingPage.getContent()
        );


        // =================================================
        // CATEGORIES
        // =================================================

        List<String> categories =
                newsRepository.findAll()
                        .stream()
                        .map(News::getCategory)
                        .filter(
                                c ->
                                        c != null
                                                && !c.trim().isEmpty()
                        )
                        .map(String::trim)
                        .distinct()
                        .sorted()
                        .toList();


        model.addAttribute(
                "categories",
                categories
        );


        return "newsList";
    }


    // =====================================================
    // ADD NEWS
    // =====================================================

    @GetMapping("/addNews")
    public String addNews(
            Model model) {

        model.addAttribute(
                "news",
                new News()
        );


        return "addNews";
    }


    // =====================================================
    // SAVE NEWS
    // =====================================================

    @PostMapping("/saveNews")
    public String saveNews(
            @ModelAttribute News news,
            Principal principal) {


        // =================================================
        // INITIAL VIEW COUNT
        // =================================================

        news.setViewCount(0L);


        // =================================================
        // SAVE ARTICLE
        // =================================================

        News savedNews =
                newsRepository.save(news);


        // =================================================
        // SEND NOTIFICATIONS
        // =================================================

        String authorEmail =
                principal != null
                        ? principal.getName()
                        : null;


        notificationService
                .notifyUsersAboutNewArticle(
                        savedNews,
                        authorEmail
                );


        return "redirect:/newsList";
    }


    // =====================================================
    // VIEW NEWS
    // =====================================================

    @GetMapping("/viewNews/{id}")
    public String viewNews(

            @PathVariable Long id,

            Model model,

            Principal principal) {


        News news =
                newsRepository
                        .findById(id)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "News not found with id: "
                                                + id
                                )
                        );


        // =================================================
        // GLOBAL VIEW COUNT
        // =================================================

        Long currentViews =
                news.getViewCount();


        if (currentViews == null) {

            currentViews = 0L;
        }


        news.setViewCount(
                currentViews + 1
        );


        newsRepository.save(news);


        // =================================================
        // PERSONAL READING HISTORY
        // =================================================

        if (principal != null) {

            User user =
                    userRepository
                            .findByEmail(
                                    principal.getName()
                            )
                            .orElse(null);


            if (user != null) {

                ReadingHistory history =
                        readingHistoryRepository
                                .findByUserIdAndNewsId(
                                        user.getId(),
                                        news.getId()
                                )
                                .orElse(null);


                if (history == null) {

                    history =
                            new ReadingHistory(
                                    user,
                                    news
                            );

                } else {

                    history.setLastReadAt(
                            LocalDateTime.now(
                                    ZoneId.of(
                                            "Asia/Kolkata"
                                    )
                            )
                    );
                }


                readingHistoryRepository.save(
                        history
                );
            }
        }


        // =================================================
        // RELATED NEWS
        // =================================================

        List<News> relatedNews =
                List.of();


        if (news.getCategory() != null
                && !news.getCategory()
                        .trim()
                        .isEmpty()) {

            relatedNews =
                    newsRepository
                            .findTop3ByCategoryIgnoreCaseAndIdNotOrderByPublishedDateDesc(
                                    news.getCategory(),
                                    news.getId()
                            );
        }


        // =================================================
        // BOOKMARK STATUS
        // =================================================

        boolean isBookmarked =
                false;


        if (principal != null) {

            User user =
                    userRepository
                            .findByEmail(
                                    principal.getName()
                            )
                            .orElse(null);


            if (user != null) {

                isBookmarked =
                        bookmarkRepository
                                .existsByUserIdAndNewsId(
                                        user.getId(),
                                        news.getId()
                                );
            }
        }


        model.addAttribute(
                "news",
                news
        );


        model.addAttribute(
                "relatedNews",
                relatedNews
        );


        model.addAttribute(
                "isBookmarked",
                isBookmarked
        );


        return "viewNews";
    }


    // =====================================================
    // EDIT NEWS
    // =====================================================

    @GetMapping("/editNews/{id}")
    public String editNews(

            @PathVariable Long id,

            Model model) {


        News news =
                newsRepository
                        .findById(id)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "News not found with id: "
                                                + id
                                )
                        );


        model.addAttribute(
                "news",
                news
        );


        return "editNews";
    }


    // =====================================================
    // UPDATE NEWS
    // =====================================================

    @PostMapping("/updateNews")
    public String updateNews(
            @ModelAttribute News news) {


        News existingNews =
                newsRepository
                        .findById(
                                news.getId()
                        )
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "News not found with id: "
                                                + news.getId()
                                )
                        );


        // =================================================
        // PRESERVE VIEW COUNT
        // =================================================

        news.setViewCount(
                existingNews.getViewCount()
        );


        // =================================================
        // PRESERVE IMAGE
        // =================================================

        if (news.getImageUrl() == null
                || news.getImageUrl()
                        .trim()
                        .isEmpty()) {

            news.setImageUrl(
                    existingNews.getImageUrl()
            );
        }


        newsRepository.save(news);


        return "redirect:/newsList";
    }


    // =====================================================
    // DELETE NEWS
    // =====================================================

    @GetMapping("/deleteNews/{id}")
    public String deleteNews(
            @PathVariable Long id) {


        newsRepository.deleteById(id);


        return "redirect:/newsList";
    }
}