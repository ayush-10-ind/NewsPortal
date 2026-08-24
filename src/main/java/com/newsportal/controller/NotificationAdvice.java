package com.newsportal.controller;

import com.newsportal.entity.User;
import com.newsportal.repository.NotificationRepository;
import com.newsportal.repository.UserRepository;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class NotificationAdvice {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;


    public NotificationAdvice(
            NotificationRepository notificationRepository,
            UserRepository userRepository) {

        this.notificationRepository =
                notificationRepository;

        this.userRepository =
                userRepository;
    }


    // =====================================================
    // ADD NOTIFICATION COUNT TO ALL THYMELEAF PAGES
    // =====================================================

    @ModelAttribute("unreadNotificationCount")
    public long unreadNotificationCount(
            Authentication authentication) {

        // Logged-out users have no notifications

        if (authentication == null
                || !authentication.isAuthenticated()) {

            return 0;
        }


        User user =
                userRepository
                        .findByEmail(
                                authentication.getName()
                        )
                        .orElse(null);


        if (user == null) {

            return 0;
        }


        return notificationRepository
                .countByUserAndReadFalse(user);
    }
}