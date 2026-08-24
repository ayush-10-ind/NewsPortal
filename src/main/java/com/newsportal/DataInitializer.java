package com.newsportal;

import com.newsportal.entity.Category;
import com.newsportal.entity.Role;
import com.newsportal.entity.User;

import com.newsportal.repository.CategoryRepository;
import com.newsportal.repository.RoleRepository;
import com.newsportal.repository.UserRepository;

import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {

    private final RoleRepository roleRepository;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(
            RoleRepository roleRepository,
            CategoryRepository categoryRepository,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder) {

        this.roleRepository = roleRepository;
        this.categoryRepository = categoryRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {

        // =========================
        // DEFAULT ROLES
        // =========================

        createRoleIfNotExists("ROLE_ADMIN");
        createRoleIfNotExists("ROLE_EDITOR");
        createRoleIfNotExists("ROLE_USER");


        // =========================
        // DEFAULT CATEGORIES
        // =========================

        createCategoryIfNotExists(
                "Technology",
                "Technology, software, AI and gadgets."
        );

        createCategoryIfNotExists(
                "Sports",
                "Sports news, matches and events."
        );

        createCategoryIfNotExists(
                "Business",
                "Business, finance and economy news."
        );

        createCategoryIfNotExists(
                "Politics",
                "Political news and current affairs."
        );

        createCategoryIfNotExists(
                "Entertainment",
                "Movies, music, television and entertainment."
        );

        createCategoryIfNotExists(
                "Science",
                "Science, research and discoveries."
        );

        createCategoryIfNotExists(
                "World",
                "International news and global events."
        );


        // =========================
        // DEFAULT ADMIN USER
        // =========================

        createDefaultAdmin();
    }


    // =========================
    // CREATE ROLE
    // =========================

    private void createRoleIfNotExists(String roleName) {

        if (roleRepository.findByName(roleName).isEmpty()) {

            Role role = new Role(roleName);

            roleRepository.save(role);

            System.out.println("Created role: " + roleName);
        }
    }


    // =========================
    // CREATE CATEGORY
    // =========================

    private void createCategoryIfNotExists(
            String name,
            String description) {

        if (!categoryRepository.existsByNameIgnoreCase(name)) {

            Category category =
                    new Category(name, description);

            categoryRepository.save(category);

            System.out.println(
                    "Created category: " + name
            );
        }
    }


    // =========================
    // CREATE ADMIN
    // =========================

    private void createDefaultAdmin() {

        String adminEmail = "admin@newsportal.com";

        if (userRepository.findByEmail(adminEmail).isEmpty()) {

            Role adminRole = roleRepository
                    .findByName("ROLE_ADMIN")
                    .orElseThrow(() ->
                            new RuntimeException(
                                    "ROLE_ADMIN not found"
                            )
                    );

            User admin = new User();

            admin.setName("News Portal Admin");
            admin.setEmail(adminEmail);

            admin.setPassword(
                    passwordEncoder.encode("Admin@123")
            );

            admin.setEnabled(true);

            admin.addRole(adminRole);

            userRepository.save(admin);

            System.out.println(
                    "Created default admin user: "
                    + adminEmail
            );
        }
    }
}