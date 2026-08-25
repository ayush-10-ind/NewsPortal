package com.newsportal.controller;

import com.newsportal.dto.PasswordChangeDTO;
import com.newsportal.dto.ProfileUpdateDTO;
import com.newsportal.entity.User;
import com.newsportal.repository.UserRepository;

import jakarta.validation.Valid;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/profile")
public class ProfileController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public ProfileController(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder) {

        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    // =========================================================
    // VIEW PROFILE
    // =========================================================

    @GetMapping
    public String profile(
            Authentication authentication,
            Model model) {

        User user = getCurrentUser(authentication);

        model.addAttribute("user", user);

        return "profile";
    }

    // =========================================================
    // EDIT PROFILE PAGE
    // =========================================================

    @GetMapping("/edit")
    public String editProfile(
            Authentication authentication,
            Model model) {

        User user = getCurrentUser(authentication);

        ProfileUpdateDTO dto = new ProfileUpdateDTO(
                user.getName(),
                user.getPhone()
        );

        model.addAttribute("profileUpdate", dto);
        model.addAttribute("user", user);

        return "editProfile";
    }

    // =========================================================
    // UPDATE PROFILE
    // =========================================================

    @PostMapping("/edit")
    public String updateProfile(
            Authentication authentication,
            @Valid @ModelAttribute("profileUpdate")
            ProfileUpdateDTO dto,
            BindingResult result,
            Model model) {

        User user = getCurrentUser(authentication);

        // ---------------------------------------------
        // VALIDATION ERRORS
        // ---------------------------------------------

        if (result.hasErrors()) {

            model.addAttribute("user", user);

            return "editProfile";
        }

        // ---------------------------------------------
        // NORMALIZE PHONE
        // ---------------------------------------------

        String phone = dto.getPhone();

        if (phone != null) {
            phone = phone.trim();

            if (phone.isEmpty()) {
                phone = null;
            }
        }

        // ---------------------------------------------
        // CHECK PHONE DUPLICATE
        // ---------------------------------------------

        if (phone != null) {

            boolean phoneTaken =
                    userRepository.existsByPhoneAndIdNot(
                            phone,
                            user.getId()
                    );

            if (phoneTaken) {

                result.rejectValue(
                        "phone",
                        "duplicate",
                        "This phone number is already associated with another account."
                );

                model.addAttribute("user", user);

                return "editProfile";
            }
        }

        // ---------------------------------------------
        // UPDATE USER
        // ---------------------------------------------

        user.setName(dto.getName().trim());
        user.setPhone(phone);

        try {

            userRepository.save(user);

        } catch (DataIntegrityViolationException ex) {

            result.rejectValue(
                    "phone",
                    "duplicate",
                    "This phone number is already in use."
            );

            model.addAttribute("user", user);

            return "editProfile";
        }

        return "redirect:/profile?updated=true";
    }

    // =========================================================
    // CHANGE PASSWORD PAGE
    // =========================================================

    @GetMapping("/change-password")
    public String changePasswordPage(
            Model model) {

        model.addAttribute(
                "passwordChange",
                new PasswordChangeDTO()
        );

        return "changePassword";
    }

    // =========================================================
    // CHANGE PASSWORD
    // =========================================================

    @PostMapping("/change-password")
    public String changePassword(
            Authentication authentication,
            @Valid @ModelAttribute("passwordChange")
            PasswordChangeDTO dto,
            BindingResult result,
            Model model) {

        User user = getCurrentUser(authentication);

        // ---------------------------------------------
        // VALIDATION
        // ---------------------------------------------

        if (result.hasErrors()) {
            return "changePassword";
        }

        // ---------------------------------------------
        // CURRENT PASSWORD
        // ---------------------------------------------

        if (user.getPassword() == null ||
                !passwordEncoder.matches(
                        dto.getCurrentPassword(),
                        user.getPassword())) {

            model.addAttribute(
                    "error",
                    "Current password is incorrect."
            );

            return "changePassword";
        }

        // ---------------------------------------------
        // CONFIRM NEW PASSWORD
        // ---------------------------------------------

        if (!dto.getNewPassword()
                .equals(dto.getConfirmPassword())) {

            model.addAttribute(
                    "error",
                    "New passwords do not match."
            );

            return "changePassword";
        }

        // ---------------------------------------------
        // SAVE NEW PASSWORD
        // ---------------------------------------------

        user.setPassword(
                passwordEncoder.encode(
                        dto.getNewPassword()
                )
        );

        userRepository.save(user);

        return "redirect:/profile?passwordChanged=true";
    }

    // =========================================================
    // CURRENT USER
    // =========================================================

    private User getCurrentUser(
            Authentication authentication) {

        if (authentication == null ||
                authentication.getName() == null) {

            throw new RuntimeException(
                    "User is not authenticated"
            );
        }

        return userRepository
                .findByEmail(authentication.getName())
                .orElseThrow(() ->
                        new RuntimeException(
                                "Current user not found"
                        )
                );
    }
}