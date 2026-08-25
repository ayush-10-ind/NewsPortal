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

@Controller
public class AuthController {

    private final UserService userService;


    public AuthController(
            UserService userService) {

        this.userService = userService;
    }


    // =====================================================
    // LOGIN
    // =====================================================

    @GetMapping("/login")
    public String login() {

        return "login";
    }


    // =====================================================
    // REGISTER PAGE
    // =====================================================

    @GetMapping("/register")
    public String registerPage(
            Model model) {

        model.addAttribute(
                "registerRequest",
                new RegisterRequestDTO()
        );

        return "register";
    }


    // =====================================================
    // REGISTER
    // =====================================================

    @PostMapping("/register")
    public String register(

            @Valid
            @ModelAttribute("registerRequest")
            RegisterRequestDTO request,

            BindingResult bindingResult,

            Model model) {


        // =================================================
        // VALIDATION
        // =================================================

        if (bindingResult.hasErrors()) {

            return "register";
        }


        // =================================================
        // REGISTER
        // =================================================

        try {

            userService.registerUser(
                    request
            );


            return "redirect:/login?verificationSent=true";


        } catch (RuntimeException e) {

            model.addAttribute(
                    "error",
                    e.getMessage()
            );

            return "register";
        }
    }


    // =====================================================
    // ACCESS DENIED
    // =====================================================

    @GetMapping("/access-denied")
    public String accessDenied() {

        return "access-denied";
    }
}