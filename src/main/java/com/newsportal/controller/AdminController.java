package com.newsportal.controller;

import com.newsportal.entity.Role;
import com.newsportal.entity.User;
import com.newsportal.repository.RoleRepository;
import com.newsportal.repository.UserRepository;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    public AdminController(
            UserRepository userRepository,
            RoleRepository roleRepository) {

        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
    }


    // =========================================================
    // ADMIN USER MANAGEMENT
    // =========================================================

    @GetMapping("/users")
    public String users(Model model) {

        var users = userRepository.findAll();

        long activeUsers =
                users.stream()
                        .filter(User::isEnabled)
                        .count();

        long disabledUsers =
                users.stream()
                        .filter(user -> !user.isEnabled())
                        .count();

        long adminUsers =
                users.stream()
                        .filter(user ->
                                user.getRoles()
                                        .stream()
                                        .anyMatch(role ->
                                                "ROLE_ADMIN"
                                                        .equals(role.getName())
                                        )
                        )
                        .count();

        long editorUsers =
                users.stream()
                        .filter(user ->
                                user.getRoles()
                                        .stream()
                                        .anyMatch(role ->
                                                "ROLE_EDITOR"
                                                        .equals(role.getName())
                                        )
                        )
                        .count();

        long normalUsers =
                users.stream()
                        .filter(user ->
                                user.getRoles()
                                        .stream()
                                        .anyMatch(role ->
                                                "ROLE_USER"
                                                        .equals(role.getName())
                                        )
                        )
                        .count();


        model.addAttribute("users", users);

        model.addAttribute(
                "activeUsers",
                activeUsers
        );

        model.addAttribute(
                "disabledUsers",
                disabledUsers
        );

        model.addAttribute(
                "adminUsers",
                adminUsers
        );

        model.addAttribute(
                "editorUsers",
                editorUsers
        );

        model.addAttribute(
                "normalUsers",
                normalUsers
        );


        return "admin/users";
    }

    // =========================================================
    // CHANGE USER ROLE
    // =========================================================

    @PostMapping("/users/change-role")
    public String changeUserRole(
            @RequestParam Long userId,
            @RequestParam String roleName) {

        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new RuntimeException(
                                "User not found"
                        )
                );


        // -----------------------------------------------------
        // PROTECT ADMIN ACCOUNTS
        // -----------------------------------------------------

        boolean isAdmin = user.getRoles()
                .stream()
                .anyMatch(role ->
                        "ROLE_ADMIN".equals(role.getName())
                );

        if (isAdmin) {

            return "redirect:/admin/users?error=adminProtected";
        }


        // -----------------------------------------------------
        // ONLY USER / EDITOR ALLOWED
        // -----------------------------------------------------

        if (!"ROLE_USER".equals(roleName)
                && !"ROLE_EDITOR".equals(roleName)) {

            return "redirect:/admin/users?error=invalidRole";
        }


        // -----------------------------------------------------
        // FIND ROLE
        // -----------------------------------------------------

        Role newRole = roleRepository
                .findByName(roleName)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Role not found: " + roleName
                        )
                );


        // -----------------------------------------------------
        // REPLACE EXISTING ROLE
        // -----------------------------------------------------

        user.getRoles().clear();

        user.addRole(newRole);


        // -----------------------------------------------------
        // SAVE
        // -----------------------------------------------------

        userRepository.save(user);


        return "redirect:/admin/users?success=roleChanged";
    }


    // =========================================================
    // ENABLE / DISABLE USER
    // =========================================================

    @PostMapping("/users/toggle-status")
    public String toggleUserStatus(
            @RequestParam Long userId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new RuntimeException(
                                "User not found"
                        )
                );


        // -----------------------------------------------------
        // PROTECT ADMIN ACCOUNTS
        // -----------------------------------------------------

        boolean isAdmin = user.getRoles()
                .stream()
                .anyMatch(role ->
                        "ROLE_ADMIN".equals(role.getName())
                );

        if (isAdmin) {

            return "redirect:/admin/users?error=adminProtected";
        }


        // -----------------------------------------------------
        // TOGGLE ACCOUNT
        // -----------------------------------------------------

        user.setEnabled(!user.isEnabled());


        // -----------------------------------------------------
        // SAVE
        // -----------------------------------------------------

        userRepository.save(user);


        return "redirect:/admin/users?success=statusChanged";
    }
}