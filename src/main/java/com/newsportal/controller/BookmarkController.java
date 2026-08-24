package com.newsportal.controller;

import com.newsportal.entity.Bookmark;
import com.newsportal.entity.News;
import com.newsportal.entity.User;
import com.newsportal.repository.BookmarkRepository;
import com.newsportal.repository.NewsRepository;
import com.newsportal.repository.UserRepository;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@Controller
@RequestMapping("/bookmarks")
public class BookmarkController {

    private final BookmarkRepository bookmarkRepository;
    private final NewsRepository newsRepository;
    private final UserRepository userRepository;

    public BookmarkController(
            BookmarkRepository bookmarkRepository,
            NewsRepository newsRepository,
            UserRepository userRepository) {

        this.bookmarkRepository = bookmarkRepository;
        this.newsRepository = newsRepository;
        this.userRepository = userRepository;
    }


    // =====================================================
    // SAVED NEWS PAGE
    // =====================================================

    @GetMapping
    public String bookmarks(
            Principal principal,
            Model model) {

        User user = userRepository
                .findByEmail(principal.getName())
                .orElseThrow(() ->
                        new RuntimeException("User not found")
                );

        List<Bookmark> bookmarks =
                bookmarkRepository
                        .findByUserOrderByCreatedAtDesc(user);

        model.addAttribute(
                "bookmarks",
                bookmarks
        );

        return "bookmarks";
    }


    // =====================================================
    // SAVE BOOKMARK
    // =====================================================

    @PostMapping("/save/{newsId}")
    public String saveBookmark(
            @PathVariable Long newsId,
            Principal principal) {

        User user = userRepository
                .findByEmail(principal.getName())
                .orElseThrow(() ->
                        new RuntimeException("User not found")
                );

        News news = newsRepository
                .findById(newsId)
                .orElseThrow(() ->
                        new RuntimeException("News not found")
                );


        // Prevent duplicate bookmarks

        boolean exists =
                bookmarkRepository
                        .existsByUserIdAndNewsId(
                                user.getId(),
                                news.getId()
                        );

        if (!exists) {

            Bookmark bookmark =
                    new Bookmark();

            bookmark.setUser(user);
            bookmark.setNews(news);

            bookmarkRepository.save(bookmark);
        }


        return "redirect:/viewNews/" + newsId;
    }


    // =====================================================
    // REMOVE BOOKMARK
    // =====================================================

    @PostMapping("/remove/{newsId}")
    public String removeBookmark(
            @PathVariable Long newsId,
            Principal principal) {

        User user = userRepository
                .findByEmail(principal.getName())
                .orElseThrow(() ->
                        new RuntimeException("User not found")
                );


        bookmarkRepository.deleteByUserIdAndNewsId(
                user.getId(),
                newsId
        );


        return "redirect:/viewNews/" + newsId;
    }

}