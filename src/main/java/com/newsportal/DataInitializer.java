package com.newsportal;

import com.newsportal.entity.Category;
import com.newsportal.entity.Role;
import com.newsportal.entity.User;

import com.newsportal.repository.CategoryRepository;
import com.newsportal.repository.RoleRepository;
import com.newsportal.repository.UserRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger logger =
            LoggerFactory.getLogger(DataInitializer.class);

    private final RoleRepository roleRepository;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.email}")
    private String adminEmail;

    @Value("${app.admin.password}")
    private String adminPassword;

    @Value("${app.admin.reset-password:false}")
    private boolean resetAdminPassword;

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

        createRoleIfNotExists("ROLE_ADMIN");
        createRoleIfNotExists("ROLE_EDITOR");
        createRoleIfNotExists("ROLE_USER");

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

        markExistingUsersAsVerified();
        createOrUpdateAdmin();
    }

    private void createRoleIfNotExists(
            String roleName) {

        if (roleRepository
                .findByName(roleName)
                .isEmpty()) {

            Role role = new Role(roleName);
            roleRepository.save(role);

            logger.info("Created role: {}", roleName);
        }
    }

    private void createCategoryIfNotExists(
            String name,
            String description) {

        if (!categoryRepository
                .existsByNameIgnoreCase(name)) {

            Category category =
                    new Category(
                            name,
                            description
                    );

            categoryRepository.save(category);

            logger.info("Created category: {}", name);
        }
    }

    private void markExistingUsersAsVerified() {

        int verifiedCount = 0;

        for (User user : userRepository.findAll()) {

            if (!user.isEmailVerified()) {

                user.setEmailVerified(true);
                userRepository.save(user);
                verifiedCount++;
            }
        }

        if (verifiedCount > 0) {
            logger.info("Marked {} existing users as verified", verifiedCount);
        }
    }

    private void createOrUpdateAdmin() {

        if (adminEmail == null
                || adminEmail.isBlank()) {

            throw new RuntimeException(
                    "app.admin.email is not configured."
            );
        }

        String normalizedAdminEmail =
                adminEmail
                        .trim()
                        .toLowerCase();

        Role adminRole =
                roleRepository
                        .findByName("ROLE_ADMIN")
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "ROLE_ADMIN not found"
                                )
                        );

        User configuredAdmin =
                userRepository
                        .findByEmail(
                                normalizedAdminEmail
                        )
                        .orElse(null);

        if (configuredAdmin != null) {

            configuredAdmin.addRole(adminRole);
            configuredAdmin.setEnabled(true);
            configuredAdmin.setEmailVerified(true);

            if (resetAdminPassword) {

                if (adminPassword == null
                        || adminPassword.isBlank()) {

                    throw new RuntimeException(
                            "app.admin.password is not configured."
                    );
                }

                configuredAdmin.setPassword(
                        passwordEncoder.encode(
                                adminPassword
                        )
                );

                logger.info("Admin password was reset");
            }

            userRepository.save(configuredAdmin);

            logger.info(
                    "Configured admin account found: {}",
                    normalizedAdminEmail
            );

            disableOtherAdminAccounts(
                    configuredAdmin.getId(),
                    adminRole
            );

            return;
        }

        User admin = new User();

        admin.setName(
                "News Portal Admin"
        );

        admin.setEmail(
                normalizedAdminEmail
        );

        if (adminPassword == null
                || adminPassword.isBlank()) {

            throw new RuntimeException(
                    "app.admin.password is not configured."
            );
        }

        admin.setPassword(
                passwordEncoder.encode(
                        adminPassword
                )
        );

        admin.setEnabled(true);
        admin.setEmailVerified(true);
        admin.addRole(adminRole);

        userRepository.save(admin);

        logger.info(
                "Created admin account: {}",
                normalizedAdminEmail
        );

        disableOtherAdminAccounts(
                admin.getId(),
                adminRole
        );
    }

    private void disableOtherAdminAccounts(
            Long activeAdminId,
            Role adminRole) {

        int disabledCount = 0;

        for (User user : userRepository.findAll()) {

            if (user.getId() == null) {
                continue;
            }

            if (user.getId().equals(activeAdminId)) {
                continue;
            }

            boolean isAdmin =
                    user.getRoles()
                            .stream()
                            .anyMatch(role ->
                                    "ROLE_ADMIN".equals(
                                            role.getName()
                                    )
                            );

            if (isAdmin && user.isEnabled()) {

                user.setEnabled(false);
                userRepository.save(user);
                disabledCount++;
            }
        }

        if (disabledCount > 0) {
            logger.info(
                    "Disabled {} old admin account(s)",
                    disabledCount
            );
        }
    }
}
