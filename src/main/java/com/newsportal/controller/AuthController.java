package com.newsportal.controller;

import com.newsportal.dto.RegisterRequestDTO;
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
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/register")
    public String registerPage(Model model) {
        model.addAttribute("registerRequest", new RegisterRequestDTO());
        return "register";
    }

    @PostMapping("/register")
    public String register(
            @Valid
            @ModelAttribute("registerRequest")
            RegisterRequestDTO request,
            BindingResult bindingResult,
            Model model) {

        if (bindingResult.hasErrors()) {
            return "register";
        }

        try {
            userService.registerUser(request);

            model.addAttribute("email", request.getEmail());
            return "verification-sent";

        } catch (RuntimeException e) {
            model.addAttribute("error", e.getMessage());
            return "register";
        }
    }

    // =====================================================
    // RESEND VERIFICATION
    // =====================================================

    @PostMapping("/resend-verification")
    public String resendVerification(
            @RequestParam("email") String email,
            Model model) {

        try {
            boolean sent = userService.resendVerificationEmail(email);

            model.addAttribute("email", email.trim());
            model.addAttribute(
                    "resendMessage",
                    sent
                            ? "A new verification link has been sent."
                            : "If this email belongs to a pending AgniPress account, a new verification link will be sent."
            );

            return "verification-sent";

        } catch (RuntimeException e) {
            model.addAttribute("email", email.trim());
            model.addAttribute("error", e.getMessage());
            return "verification-sent";
        }
    }

    @GetMapping("/access-denied")
    public String accessDenied() {
        return "access-denied";
    }
}
