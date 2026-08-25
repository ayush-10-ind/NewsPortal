package com.newsportal.service;

import com.newsportal.entity.EmailVerificationToken;
import com.newsportal.entity.User;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String senderEmail;

    @Value("${app.base-url:http://localhost:8082}")
    private String baseUrl;


    public EmailService(
            JavaMailSender mailSender) {

        this.mailSender = mailSender;
    }


    public void sendVerificationEmail(
            User user,
            EmailVerificationToken token) {

        String verificationLink =
                baseUrl
                        + "/verify-email?token="
                        + token.getToken();


        SimpleMailMessage message =
                new SimpleMailMessage();


        message.setFrom(senderEmail);

        message.setTo(
                user.getEmail()
        );

        message.setSubject(
                "Verify your News Portal email"
        );


        message.setText(
                "Hello "
                        + user.getName()
                        + ",\n\n"

                        + "Welcome to News Portal.\n\n"

                        + "Please verify your email address "
                        + "by opening the link below:\n\n"

                        + verificationLink
                        + "\n\n"

                        + "This verification link expires "
                        + "in 24 hours.\n\n"

                        + "If you did not create this account, "
                        + "you can safely ignore this email.\n\n"

                        + "News Portal"
        );


        mailSender.send(message);
    }
}