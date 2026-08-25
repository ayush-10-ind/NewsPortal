package com.newsportal.security;

import com.newsportal.entity.Role;
import com.newsportal.entity.User;
import com.newsportal.repository.RoleRepository;
import com.newsportal.repository.UserRepository;

import org.springframework.http.HttpHeaders;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class CustomOAuth2UserService
        extends DefaultOAuth2UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RestClient restClient;

    public CustomOAuth2UserService(
            UserRepository userRepository,
            RoleRepository roleRepository) {

        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.restClient = RestClient.builder().build();
    }

    // =====================================================
    // LOAD OAUTH USER
    // =====================================================

    @Override
    public OAuth2User loadUser(
            OAuth2UserRequest userRequest)
            throws OAuth2AuthenticationException {

        OAuth2User oauthUser =
                super.loadUser(userRequest);

        String registrationId =
                userRequest
                        .getClientRegistration()
                        .getRegistrationId();

        String email =
                extractEmail(
                        oauthUser,
                        userRequest,
                        registrationId
                );

        if (email == null || email.isBlank()) {

            throw oauthException(
                    "email_not_found",
                    "Unable to retrieve a verified email from "
                            + registrationId
            );
        }

        email = email.trim().toLowerCase();

        // =================================================
        // FIND EXISTING USER BY EMAIL
        // =================================================

        User user =
                userRepository
                        .findByEmail(email)
                        .orElse(null);

        // =================================================
        // EXISTING USER
        // =================================================

        if (user != null) {

            if (!user.isEnabled()) {

                throw oauthException(
                        "account_disabled",
                        "Your News Portal account has been disabled."
                );
            }

            // OAuth authentication proves ownership
            // of the verified provider email.
            if (!user.isEmailVerified()) {

                user.setEmailVerified(true);
                userRepository.save(user);
            }

            return buildOAuthUser(
                    oauthUser,
                    user
            );
        }

        // =================================================
        // NEW OAUTH USER
        // =================================================

        user =
                createNewOAuthUser(
                        oauthUser,
                        email,
                        registrationId
                );

        return buildOAuthUser(
                oauthUser,
                user
        );
    }

    // =====================================================
    // CREATE NEW OAUTH USER
    // =====================================================

    private User createNewOAuthUser(
            OAuth2User oauthUser,
            String email,
            String registrationId) {

        Role userRole =
                roleRepository
                        .findByName("ROLE_USER")
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "ROLE_USER not found"
                                )
                        );

        User user = new User();

        // =================================================
        // DISPLAY NAME
        // =================================================

        String name =
                extractName(oauthUser);

        if (name == null || name.isBlank()) {

            int atIndex = email.indexOf("@");

            if (atIndex > 0) {

                name =
                        email.substring(
                                0,
                                atIndex
                        );

            } else {

                name = "News Portal User";
            }
        }

        user.setName(name);

        // =================================================
        // UNIQUE NEWS PORTAL USERNAME
        // =================================================

        String username =
                generateUniqueUsername(
                        oauthUser,
                        email,
                        registrationId
                );

        user.setUsername(username);

        // =================================================
        // EMAIL
        // =================================================

        user.setEmail(email);

        // =================================================
        // PASSWORD
        // =================================================

        user.setPassword(null);

        // =================================================
        // STATUS
        // =================================================

        user.setEnabled(true);

        // OAuth provider has authenticated the email.
        user.setEmailVerified(true);

        // =================================================
        // ROLE
        // =================================================

        user.addRole(userRole);

        return userRepository.save(user);
    }

    // =====================================================
    // GENERATE UNIQUE USERNAME
    // =====================================================

    private String generateUniqueUsername(
            OAuth2User oauthUser,
            String email,
            String registrationId) {

        String baseUsername = null;

        // -------------------------------------------------
        // GITHUB LOGIN
        // -------------------------------------------------

        if ("github".equalsIgnoreCase(registrationId)) {

            Object login =
                    oauthUser
                            .getAttributes()
                            .get("login");

            if (login != null
                    && !login.toString().isBlank()) {

                baseUsername =
                        login.toString();
            }
        }

        // -------------------------------------------------
        // PROVIDER NAME
        // -------------------------------------------------

        if (baseUsername == null
                || baseUsername.isBlank()) {

            Object name =
                    oauthUser
                            .getAttributes()
                            .get("name");

            if (name != null
                    && !name.toString().isBlank()) {

                baseUsername =
                        name.toString();
            }
        }

        // -------------------------------------------------
        // EMAIL FALLBACK
        // -------------------------------------------------

        if (baseUsername == null
                || baseUsername.isBlank()) {

            int atIndex =
                    email.indexOf("@");

            if (atIndex > 0) {

                baseUsername =
                        email.substring(
                                0,
                                atIndex
                        );

            } else {

                baseUsername =
                        "user";
            }
        }

        // -------------------------------------------------
        // CLEAN USERNAME
        // -------------------------------------------------

        baseUsername =
                baseUsername
                        .toLowerCase()
                        .replaceAll(
                                "[^a-z0-9_]",
                                "_"
                        )
                        .replaceAll(
                                "_+",
                                "_"
                        )
                        .replaceAll(
                                "^_+|_+$",
                                ""
                        );

        if (baseUsername.isBlank()) {
            baseUsername = "user";
        }

        if (baseUsername.length() > 40) {

            baseUsername =
                    baseUsername.substring(
                            0,
                            40
                    );
        }

        // -------------------------------------------------
        // UNIQUE CHECK
        // -------------------------------------------------

        String username =
                baseUsername;

        int counter = 1;

        while (userRepository.existsByUsername(username)) {

            String suffix =
                    "_" + counter;

            int maxBaseLength =
                    50 - suffix.length();

            String shortenedBase =
                    baseUsername.length() > maxBaseLength
                            ? baseUsername.substring(
                                    0,
                                    maxBaseLength
                            )
                            : baseUsername;

            username =
                    shortenedBase + suffix;

            counter++;
        }

        return username;
    }

    // =====================================================
    // BUILD OAUTH USER
    // =====================================================

    private OAuth2User buildOAuthUser(
            OAuth2User oauthUser,
            User user) {

        List<SimpleGrantedAuthority> authorities =
                new ArrayList<>();

        for (Role role : user.getRoles()) {

            authorities.add(
                    new SimpleGrantedAuthority(
                            role.getName()
                    )
            );
        }

        Map<String, Object> attributes =
                new HashMap<>(
                        oauthUser.getAttributes()
                );

        // -------------------------------------------------
        // APPLICATION EMAIL
        // -------------------------------------------------

        attributes.put(
                "email",
                user.getEmail()
        );

        // -------------------------------------------------
        // APPLICATION USERNAME
        // -------------------------------------------------

        attributes.put(
                "username",
                user.getUsername()
        );

        // -------------------------------------------------
        // APPLICATION DISPLAY NAME
        // -------------------------------------------------

        attributes.put(
                "displayName",
                user.getName()
        );

        // -------------------------------------------------
        // PROFILE IMAGE
        // -------------------------------------------------

        if (user.getProfileImage() != null
                && !user.getProfileImage().isBlank()) {

            attributes.put(
                    "profileImage",
                    user.getProfileImage()
            );
        }

        return new DefaultOAuth2User(
                authorities,
                attributes,
                "email"
        );
    }

    // =====================================================
    // EXTRACT EMAIL
    // =====================================================

    private String extractEmail(
            OAuth2User oauthUser,
            OAuth2UserRequest userRequest,
            String registrationId) {

        // =================================================
        // GOOGLE
        // =================================================

        if ("google".equalsIgnoreCase(
                registrationId)) {

            Object email =
                    oauthUser
                            .getAttributes()
                            .get("email");

            Object verified =
                    oauthUser
                            .getAttributes()
                            .get("email_verified");

            if (email != null) {

                if (Boolean.TRUE.equals(verified)
                        || "true".equalsIgnoreCase(
                                String.valueOf(verified)
                        )) {

                    return email.toString();
                }
            }

            return null;
        }

        // =================================================
        // GITHUB
        // =================================================

        if ("github".equalsIgnoreCase(
                registrationId)) {

            Object email =
                    oauthUser
                            .getAttributes()
                            .get("email");

            if (email != null
                    && !email.toString().isBlank()) {

                return email.toString();
            }

            return extractGitHubEmail(
                    userRequest
            );
        }

        // =================================================
        // GENERIC
        // =================================================

        Object email =
                oauthUser
                        .getAttributes()
                        .get("email");

        if (email != null
                && !email.toString().isBlank()) {

            return email.toString();
        }

        return null;
    }

    // =====================================================
    // GITHUB EMAIL
    // =====================================================

    private String extractGitHubEmail(
            OAuth2UserRequest userRequest) {

        try {

            String accessToken =
                    userRequest
                            .getAccessToken()
                            .getTokenValue();

            List<Map<String, Object>> emails =
                    restClient
                            .get()
                            .uri(
                                    "https://api.github.com/user/emails"
                            )
                            .header(
                                    HttpHeaders.AUTHORIZATION,
                                    "Bearer " + accessToken
                            )
                            .header(
                                    HttpHeaders.ACCEPT,
                                    "application/vnd.github+json"
                            )
                            .header(
                                    "X-GitHub-Api-Version",
                                    "2026-03-10"
                            )
                            .retrieve()
                            .body(List.class);

            if (emails == null
                    || emails.isEmpty()) {

                return null;
            }

            // =================================================
            // PRIMARY + VERIFIED
            // =================================================

            for (Map<String, Object> emailData :
                    emails) {

                Object email =
                        emailData.get("email");

                Object primary =
                        emailData.get("primary");

                Object verified =
                        emailData.get("verified");

                if (email != null
                        && Boolean.TRUE.equals(primary)
                        && Boolean.TRUE.equals(verified)) {

                    return email.toString();
                }
            }

            // =================================================
            // ANY VERIFIED EMAIL
            // =================================================

            for (Map<String, Object> emailData :
                    emails) {

                Object email =
                        emailData.get("email");

                Object verified =
                        emailData.get("verified");

                if (email != null
                        && Boolean.TRUE.equals(verified)) {

                    return email.toString();
                }
            }

        } catch (Exception e) {

            throw oauthException(
                    "github_email_error",
                    "Could not retrieve email from GitHub."
            );
        }

        return null;
    }

    // =====================================================
    // EXTRACT NAME
    // =====================================================

    private String extractName(
            OAuth2User oauthUser) {

        Map<String, Object> attributes =
                oauthUser.getAttributes();

        Object name =
                attributes.get("name");

        if (name != null
                && !name.toString().isBlank()) {

            return name.toString();
        }

        Object login =
                attributes.get("login");

        if (login != null
                && !login.toString().isBlank()) {

            return login.toString();
        }

        Object username =
                attributes.get(
                        "preferred_username"
                );

        if (username != null
                && !username.toString().isBlank()) {

            return username.toString();
        }

        return null;
    }

    // =====================================================
    // OAUTH EXCEPTION
    // =====================================================

    private OAuth2AuthenticationException oauthException(
            String errorCode,
            String message) {

        return new OAuth2AuthenticationException(
                new OAuth2Error(errorCode),
                message
        );
    }
}