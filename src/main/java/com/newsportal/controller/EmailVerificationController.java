package com.newsportal.controller;

import com.newsportal.dto.SetPasswordRequestDTO;
import com.newsportal.entity.EmailVerificationToken;
import com.newsportal.entity.User;
import com.newsportal.repository.EmailVerificationTokenRepository;
import com.newsportal.service.UserService;

import jakarta.validation.Valid;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;

import org.springframework.transaction.annotation.Transactional;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class EmailVerificationController {

    private final EmailVerificationTokenRepository tokenRepository;
    private final UserService userService;

    public EmailVerificationController(
            EmailVerificationTokenRepository tokenRepository,
            UserService userService) {

        this.tokenRepository = tokenRepository;
        this.userService = userService;
    }

    // =====================================================
    // OPEN VERIFICATION LINK
    // =====================================================

    @Transactional
    @GetMapping("/verify-email")
    public String verifyEmail(
            @RequestParam("token") String token,
            Model model) {

        EmailVerificationToken verificationToken =
                tokenRepository
                        .findByToken(token)
                        .orElse(null);

        // =================================================
        // INVALID TOKEN
        // =================================================

        if (verificationToken == null) {

            model.addAttribute(
                    "error",
                    "This verification link is invalid or has already been used."
            );

            return "email-verification-page";
        }

        // =================================================
        // EXPIRED TOKEN
        // =================================================

        if (verificationToken.isExpired()) {

            model.addAttribute(
                    "error",
                    "This verification link has expired. Please register again."
            );

            return "email-verification-page";
        }

        // =================================================
        // GET USER WHILE DATABASE SESSION IS ACTIVE
        // =================================================

        User user = verificationToken.getUser();

        String userName = user.getName();

        // =================================================
        // SHOW PASSWORD PAGE
        // =================================================

        model.addAttribute(
                "token",
                token
        );

        model.addAttribute(
                "setPasswordRequest",
                new SetPasswordRequestDTO()
        );

        model.addAttribute(
                "userName",
                userName
        );

        return "email-verification-page";
    }

    // =====================================================
    // CREATE PASSWORD
    // =====================================================

    @PostMapping("/verify-email")
    public String completeVerification(
            @RequestParam("token") String token,

            @Valid
            @ModelAttribute("setPasswordRequest")
            SetPasswordRequestDTO request,

            BindingResult bindingResult,
            Model model) {

        // =================================================
        // FORM VALIDATION
        // =================================================

        if (bindingResult.hasErrors()) {

            EmailVerificationToken verificationToken =
                    tokenRepository
                            .findByToken(token)
                            .orElse(null);

            model.addAttribute(
                    "token",
                    token
            );

            if (verificationToken != null) {

                User user = verificationToken.getUser();

                model.addAttribute(
                        "userName",
                        user.getName()
                );
            }

            return "email-verification-page";
        }

        // =================================================
        // COMPLETE VERIFICATION
        // =================================================

        /*
         * IMPORTANT:
         *
         * The database transaction now lives entirely inside
         * UserService. The controller deliberately does NOT
         * start an outer transaction and then catch exceptions
         * from the service transaction.
         *
         * This prevents Spring from attempting to commit a
         * transaction that has already been marked rollback-only,
         * which caused the HTTP 500:
         *
         * "Transaction silently rolled back because it has been
         * marked as rollback-only."
         */
        try {

            userService.completeEmailVerification(
                    token,
                    request
            );

        } catch (RuntimeException e) {

            model.addAttribute(
                    "error",
                    e.getMessage()
            );

            model.addAttribute(
                    "token",
                    token
            );

            EmailVerificationToken verificationToken =
                    tokenRepository
                            .findByToken(token)
                            .orElse(null);

            if (verificationToken != null) {

                User user = verificationToken.getUser();

                model.addAttribute(
                        "userName",
                        user.getName()
                );
            }

            return "email-verification-page";
        }

        // =================================================
        // SUCCESS
        // =================================================

        model.addAttribute(
                "success",
                "Your email has been verified and your account is ready. "
                + "You can now sign in."
        );

        return "email-verification-page";
    }
}
