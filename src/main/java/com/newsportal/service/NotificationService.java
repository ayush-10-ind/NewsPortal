package com.newsportal.service;

import com.newsportal.entity.News;
import com.newsportal.entity.Notification;
import com.newsportal.entity.User;

import com.newsportal.repository.NotificationRepository;
import com.newsportal.repository.UserRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;


    public NotificationService(
            NotificationRepository notificationRepository,
            UserRepository userRepository) {

        this.notificationRepository =
                notificationRepository;

        this.userRepository =
                userRepository;
    }


    // =====================================================
    // NOTIFY USERS ABOUT NEW ARTICLE
    // =====================================================

    @Transactional
    public void notifyUsersAboutNewArticle(
            News news,
            String authorEmail) {


        if (news == null) {
            return;
        }


        // =================================================
        // FIND ALL ENABLED USERS
        // =================================================

        List<User> users =
                userRepository.findAll()
                        .stream()
                        .filter(User::isEnabled)
                        .toList();


        // =================================================
        // CREATE NOTIFICATION
        // =================================================

        for (User user : users) {


            // ---------------------------------------------
            // DON'T NOTIFY THE AUTHOR
            // ---------------------------------------------

            if (authorEmail != null
                    && user.getEmail() != null
                    && user.getEmail()
                            .equalsIgnoreCase(authorEmail)) {

                continue;
            }


            String category =
                    news.getCategory();


            if (category == null
                    || category.trim().isEmpty()) {

                category = "General";
            }


            Notification notification =
                    new Notification(

                            user,

                            "📰 New Article Published",

                            "A new "
                                    + category
                                    + " article \""
                                    + news.getTitle()
                                    + "\" has been published.",

                            "/viewNews/"
                                    + news.getId()
                    );


            notificationRepository.save(
                    notification
            );
        }
    }
}