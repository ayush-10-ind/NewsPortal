package com.newsportal;

import com.newsportal.entity.Category;
import com.newsportal.entity.Role;
import com.newsportal.entity.User;

import com.newsportal.repository.CategoryRepository;
import com.newsportal.repository.RoleRepository;
import com.newsportal.repository.UserRepository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {

    private final RoleRepository roleRepository;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;


    // =====================================================
    // ADMIN CONFIGURATION
    // =====================================================

    @Value("${app.admin.email}")
    private String adminEmail;

    @Value("${app.admin.password}")
    private String adminPassword;

    /*
     * Set this to true ONLY when you intentionally want
     * to reset the configured admin password.
     *
     * After logging in successfully, change it back to false.
     */
    @Value("${app.admin.reset-password:false}")
    private boolean resetAdminPassword;


    // =====================================================
    // CONSTRUCTOR
    // =====================================================

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


    // =====================================================
    // STARTUP
    // =====================================================

    @Override
    public void run(String... args) {

        // =================================================
        // ROLES
        // =================================================

        createRoleIfNotExists("ROLE_ADMIN");
        createRoleIfNotExists("ROLE_EDITOR");
        createRoleIfNotExists("ROLE_USER");


        // =================================================
        // CATEGORIES
        // =================================================

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


        // =================================================
        // EXISTING USERS
        // =================================================

        markExistingUsersAsVerified();


        // =================================================
        // ADMIN
        // =================================================

        createOrUpdateAdmin();
    }


    // =====================================================
    // CREATE ROLE
    // =====================================================

    private void createRoleIfNotExists(
            String roleName) {

        if (roleRepository
                .findByName(roleName)
                .isEmpty()) {

            Role role = new Role(roleName);

            roleRepository.save(role);

            System.out.println(
                    "Created role: " + roleName
            );
        }
    }


    // =====================================================
    // CREATE CATEGORY
    // =====================================================

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

            System.out.println(
                    "Created category: " + name
            );
        }
    }


    // =====================================================
    // VERIFY EXISTING USERS
    // =====================================================

    private void markExistingUsersAsVerified() {

        for (User user : userRepository.findAll()) {

            /*
             * Existing accounts were created before
             * email verification existed.
             *
             * Keep them working.
             */

            if (!user.isEmailVerified()) {

                user.setEmailVerified(true);

                userRepository.save(user);
            }
        }
    }


    // =====================================================
    // CREATE / UPDATE ADMIN
    // =====================================================

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


        // =================================================
        // FIND ADMIN ROLE
        // =================================================

        Role adminRole =
                roleRepository
                        .findByName("ROLE_ADMIN")
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "ROLE_ADMIN not found"
                                )
                        );


        // =================================================
        // FIRST: FIND THE CONFIGURED ADMIN EMAIL
        // =================================================

        User configuredAdmin =
                userRepository
                        .findByEmail(
                                normalizedAdminEmail
                        )
                        .orElse(null);


        // =================================================
        // REAL ADMIN ACCOUNT ALREADY EXISTS
        // =================================================

        if (configuredAdmin != null) {

            /*
             * This is the important part.
             *
             * If the real admin email already belongs
             * to a normal USER/EDITOR account, promote
             * that existing account instead of creating
             * another account.
             */

            configuredAdmin.addRole(adminRole);

            configuredAdmin.setEnabled(true);

            configuredAdmin.setEmailVerified(true);


            /*
             * Password is reset ONLY when explicitly
             * requested through:
             *
             * app.admin.reset-password=true
             */

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

                System.out.println(
                        "Admin password was reset."
                );
            }


            userRepository.save(configuredAdmin);


            System.out.println(
                    "Configured admin account found: "
                            + normalizedAdminEmail
            );


            // ---------------------------------------------
            // DISABLE OLD ADMIN ACCOUNTS
            // ---------------------------------------------

            disableOtherAdminAccounts(
                    configuredAdmin.getId(),
                    adminRole
            );

            return;
        }


        // =================================================
        // CONFIGURED ADMIN DOES NOT EXIST
        // =================================================

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


        /*
         * Password comes from application configuration
         * and is never hard-coded in Java.
         */

        admin.setPassword(
                passwordEncoder.encode(
                        adminPassword
                )
        );

        admin.setEnabled(true);

        admin.setEmailVerified(true);

        admin.addRole(adminRole);


        userRepository.save(admin);


        System.out.println(
                "Created admin account: "
                        + normalizedAdminEmail
        );


        // ---------------------------------------------
        // DISABLE OLD ADMIN ACCOUNTS
        // ---------------------------------------------

        disableOtherAdminAccounts(
                admin.getId(),
                adminRole
        );
    }


    // =====================================================
    // DISABLE OLD ADMIN ACCOUNTS
    // =====================================================

    private void disableOtherAdminAccounts(
            Long activeAdminId,
            Role adminRole) {

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

                /*
                 * Disable old admin accounts so that
                 * the old credentials cannot be used.
                 */

                user.setEnabled(false);

                userRepository.save(user);


                System.out.println(
                        "Disabled old admin account: "
                                + user.getEmail()
                );
            }
        }
    }
}