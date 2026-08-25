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
    // MANUAL / NORMAL ARTICLE NOTIFICATION
    // =====================================================

    @Transactional
    public void notifyUsersAboutNewArticle(
            News news,
            String authorEmail) {

        if (news == null) {
            return;
        }


        List<User> users =
                userRepository.findAll()
                        .stream()
                        .filter(User::isEnabled)
                        .toList();


        String category =
                news.getCategory();


        if (category == null
                || category.trim().isEmpty()) {

            category = "General";
        }


        for (User user : users) {

            // Don't notify the person who created
            // the article manually.
            if (authorEmail != null
                    && user.getEmail() != null
                    && user.getEmail()
                            .equalsIgnoreCase(authorEmail)) {

                continue;
            }


            Notification notification =
                    new Notification(

                            user,

                            "New Article Published",

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


    // =====================================================
    // API IMPORT NOTIFICATION
    // =====================================================

    @Transactional
    public void notifyUsersAboutImportedNews(
            int importedCount) {

        if (importedCount <= 0) {
            return;
        }


        List<User> users =
                userRepository.findAll()
                        .stream()
                        .filter(User::isEnabled)
                        .toList();


        // ---------------------------------------------
        // SMART MESSAGE
        // ---------------------------------------------

        String title;

        String message;


        if (importedCount == 1) {

            title =
                    "New Story Added";

            message =
                    "1 new story has been added "
                            + "to News Portal.";

        } else {

            title =
                    "New Stories Added";

            message =
                    importedCount
                            + " new stories have been added "
                            + "to News Portal.";
        }


        // ---------------------------------------------
        // CREATE ONE NOTIFICATION PER USER
        // ---------------------------------------------

        for (User user : users) {

            Notification notification =
                    new Notification(

                            user,

                            title,

                            message,

                            "/newsList"
                    );


            notificationRepository.save(
                    notification
            );
        }
    }
}