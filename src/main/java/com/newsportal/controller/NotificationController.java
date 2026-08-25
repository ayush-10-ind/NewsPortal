package com.newsportal.controller;

import com.newsportal.entity.Notification;
import com.newsportal.entity.User;
import com.newsportal.repository.NotificationRepository;
import com.newsportal.repository.UserRepository;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import org.springframework.security.core.Authentication;

import java.util.List;

@Controller
public class NotificationController {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    public NotificationController(
            NotificationRepository notificationRepository,
            UserRepository userRepository) {

        this.notificationRepository =
                notificationRepository;

        this.userRepository =
                userRepository;
    }


    // =====================================================
    // NOTIFICATION PAGE
    // =====================================================

    @GetMapping("/notifications")
    public String notifications(
            Authentication authentication,
            Model model) {

        User user = getCurrentUser(authentication);

        List<Notification> notifications =
                notificationRepository
                        .findByUserOrderByCreatedAtDesc(user);

        long unreadCount =
                notificationRepository
                        .countByUserAndReadFalse(user);

        model.addAttribute(
                "notifications",
                notifications
        );

        model.addAttribute(
                "unreadCount",
                unreadCount
        );

        return "notifications";
    }


    // =====================================================
    // MARK AS READ
    // =====================================================

    @PostMapping("/notifications/read/{id}")
    public String markAsRead(
            @PathVariable Long id,
            Authentication authentication) {

        User user = getCurrentUser(authentication);

        Notification notification =
                notificationRepository
                        .findById(id)
                        .orElse(null);

        if (notification != null
                && notification.getUser() != null
                && notification.getUser().getId()
                        .equals(user.getId())) {

            notification.setRead(true);

            notificationRepository.save(notification);
        }

        return "redirect:/notifications";
    }


    // =====================================================
    // MARK ALL AS READ
    // =====================================================

    @PostMapping("/notifications/read-all")
    public String markAllAsRead(
            Authentication authentication) {

        User user = getCurrentUser(authentication);

        List<Notification> notifications =
                notificationRepository
                        .findByUserOrderByCreatedAtDesc(user);

        for (Notification notification : notifications) {

            if (!notification.isRead()) {
                notification.setRead(true);
            }
        }

        notificationRepository.saveAll(notifications);

        return "redirect:/notifications";
    }


    // =====================================================
    // DELETE NOTIFICATION
    // =====================================================

    @PostMapping("/notifications/delete/{id}")
    public String deleteNotification(
            @PathVariable Long id,
            Authentication authentication) {

        User user = getCurrentUser(authentication);

        Notification notification =
                notificationRepository
                        .findById(id)
                        .orElse(null);

        if (notification != null
                && notification.getUser() != null
                && notification.getUser().getId()
                        .equals(user.getId())) {

            notificationRepository.delete(notification);
        }

        return "redirect:/notifications";
    }


    // =====================================================
    // CURRENT USER
    // =====================================================

    private User getCurrentUser(
            Authentication authentication) {

        return userRepository
                .findByEmail(authentication.getName())
                .orElseThrow(() ->
                        new RuntimeException(
                                "Current user not found"
                        )
                );
    }
}