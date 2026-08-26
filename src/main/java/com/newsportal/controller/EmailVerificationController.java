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

            return "emailVerification";
        }

        // =================================================
        // EXPIRED TOKEN
        // =================================================

        if (verificationToken.isExpired()) {

            model.addAttribute(
                    "error",
                    "This verification link has expired. Please register again."
            );

            return "emailVerification";
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

        return "emailVerification";
    }

    // =====================================================
    // CREATE PASSWORD
    // =====================================================

    @Transactional
    @PostMapping("/verify-email")
    public String completeVerification(
            @RequestParam("token") String token,

            @Valid
            @ModelAttribute("setPasswordRequest")
            SetPasswordRequestDTO request,

            BindingResult bindingResult,
            Model model) {

        // =================================================
        // VALIDATE TOKEN
        // =================================================

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

            return "emailVerification";
        }

        // =================================================
        // EXPIRED TOKEN
        // =================================================

        if (verificationToken.isExpired()) {

            model.addAttribute(
                    "error",
                    "This verification link has expired."
            );

            return "emailVerification";
        }

        // =================================================
        // FORM VALIDATION
        // =================================================

        if (bindingResult.hasErrors()) {

            User user = verificationToken.getUser();

            model.addAttribute(
                    "token",
                    token
            );

            model.addAttribute(
                    "userName",
                    user.getName()
            );

            return "emailVerification";
        }

        // =================================================
        // GET USER
        // =================================================

        User user = verificationToken.getUser();

        // =================================================
        // SET PASSWORD
        // =================================================

        try {

            userService.setPassword(
                    user,
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

            model.addAttribute(
                    "userName",
                    user.getName()
            );

            return "emailVerification";
        }

        // =================================================
        // DELETE USED TOKEN
        // =================================================

        tokenRepository.delete(
                verificationToken
        );

        // =================================================
        // SUCCESS
        // =================================================

        model.addAttribute(
                "success",
                "Your email has been verified and your account is ready. "
                + "You can now sign in."
        );

        return "emailVerification";
    }
}