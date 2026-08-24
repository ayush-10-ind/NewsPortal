package com.newsportal.controller;

import com.newsportal.entity.Role;
import com.newsportal.entity.User;
import com.newsportal.repository.RoleRepository;
import com.newsportal.repository.UserRepository;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/admin")
public class AdminController {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    public AdminController(
            UserRepository userRepository,
            RoleRepository roleRepository) {

        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
    }


    // ============================
    // SHOW ALL USERS
    // ============================

    @GetMapping("/users")
    public String users(Model model) {

        model.addAttribute(
                "users",
                userRepository.findAll()
        );

        return "admin/users";
    }


    // ============================
    // CHANGE USER ROLE
    // ============================

    @PostMapping("/users/change-role")
    public String changeUserRole(
            @RequestParam Long userId,
            @RequestParam String roleName) {

        // Find user
        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new RuntimeException("User not found"));


        // Protect ADMIN accounts
        boolean isAdmin = user.getRoles()
                .stream()
                .anyMatch(role ->
                        role.getName().equals("ROLE_ADMIN"));

        if (isAdmin) {
            return "redirect:/admin/users?error=adminProtected";
        }


        // Only USER and EDITOR roles can be assigned
        if (!roleName.equals("ROLE_USER")
                && !roleName.equals("ROLE_EDITOR")) {

            return "redirect:/admin/users?error=invalidRole";
        }


        // Find requested role
        Role newRole = roleRepository
                .findByName(roleName)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Role not found: " + roleName
                        ));


        // Remove existing roles
        user.getRoles().clear();


        // Assign new role
        user.addRole(newRole);


        // Save user
        userRepository.save(user);


        return "redirect:/admin/users?success=roleChanged";
    }


    // ============================
    // ENABLE / DISABLE USER
    // ============================

    @PostMapping("/users/toggle-status")
    public String toggleUserStatus(
            @RequestParam Long userId) {

        // Find user
        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new RuntimeException("User not found"));


        // Protect ADMIN accounts
        boolean isAdmin = user.getRoles()
                .stream()
                .anyMatch(role ->
                        role.getName().equals("ROLE_ADMIN"));

        if (isAdmin) {
            return "redirect:/admin/users?error=adminProtected";
        }


        // Toggle status
        user.setEnabled(!user.isEnabled());


        // Save user
        userRepository.save(user);


        return "redirect:/admin/users?success=statusChanged";
    }
}