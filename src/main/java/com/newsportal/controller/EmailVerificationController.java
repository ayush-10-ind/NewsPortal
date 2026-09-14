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

    @GetMapping("/verify-email")
    public String verifyEmail(
            @RequestParam("token") String token,
            Model model) {

        EmailVerificationToken verificationToken = tokenRepository
                .findByToken(token)
                .orElse(null);

        if (verificationToken == null) {
            model.addAttribute(
                    "error",
                    "This verification link is invalid or has already been used."
            );
            return "email-verification-page";
        }

        User user = verificationToken.getUser();
        model.addAttribute("email", user.getEmail());

        if (verificationToken.isExpired()) {
            model.addAttribute(
                    "error",
                    "This verification link has expired. Request a new verification email below."
            );
            return "email-verification-page";
        }

        model.addAttribute("token", token);
        model.addAttribute("setPasswordRequest", new SetPasswordRequestDTO());
        model.addAttribute("userName", user.getName());

        return "email-verification-page";
    }

    @PostMapping("/verify-email")
    public String completeVerification(
            @RequestParam("token") String token,
            @Valid
            @ModelAttribute("setPasswordRequest")
            SetPasswordRequestDTO request,
            BindingResult bindingResult,
            Model model) {

        if (bindingResult.hasErrors()) {
            restoreVerificationContext(token, model);
            return "email-verification-page";
        }

        try {
            userService.completeEmailVerification(token, request);

        } catch (RuntimeException e) {
            model.addAttribute("error", e.getMessage());
            restoreVerificationContext(token, model);
            return "email-verification-page";
        }

        model.addAttribute(
                "success",
                "Your email has been verified and your account is ready. You can now sign in."
        );

        return "email-verification-page";
    }

    private void restoreVerificationContext(
            String token,
            Model model) {

        model.addAttribute("token", token);

        EmailVerificationToken verificationToken = tokenRepository
                .findByToken(token)
                .orElse(null);

        if (verificationToken != null) {
            User user = verificationToken.getUser();
            model.addAttribute("userName", user.getName());
            model.addAttribute("email", user.getEmail());
        }
    }
}
