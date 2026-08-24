package com.newsportal.security;

import com.newsportal.entity.Role;
import com.newsportal.entity.User;
import com.newsportal.repository.RoleRepository;
import com.newsportal.repository.UserRepository;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class CustomOAuth2UserService
        implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

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

        DefaultOAuth2UserService delegate =
                new DefaultOAuth2UserService();

        OAuth2User oauthUser =
                delegate.loadUser(userRequest);


        Map<String, Object> attributes =
                new HashMap<>(
                        oauthUser.getAttributes()
                );


        String registrationId =
                userRequest
                        .getClientRegistration()
                        .getRegistrationId();


        // =================================================
        // EXTRACT EMAIL
        // =================================================

        String email = null;


        if ("github".equalsIgnoreCase(registrationId)) {

            email =
                    getGitHubEmail(
                            userRequest,
                            attributes
                    );

        } else {

            Object emailAttribute =
                    attributes.get("email");

            if (emailAttribute != null) {

                email =
                        emailAttribute.toString();
            }
        }


        // =================================================
        // EMAIL REQUIRED
        // =================================================

        if (email == null
                || email.trim().isEmpty()) {

            throw new OAuth2AuthenticationException(
                    new OAuth2Error(
                            "email_not_found"
                    ),
                    "Could not obtain email address from "
                            + registrationId
            );
        }


        // =================================================
        // EXTRACT NAME
        // =================================================

        String name =
                extractName(
                        registrationId,
                        attributes,
                        email
                );


        // =================================================
        // PROFILE IMAGE
        // =================================================

        String profileImage =
                extractProfileImage(
                        registrationId,
                        attributes
                );


        // =================================================
        // FIND EXISTING USER
        // =================================================

        User user =
                userRepository
                        .findByEmail(email)
                        .orElse(null);


        // =================================================
        // CREATE OR UPDATE USER
        // =================================================

        if (user == null) {

            user =
                    createNewUser(
                            name,
                            email,
                            profileImage
                    );

        } else {

            boolean changed = false;


            // Update name

            if (name != null
                    && !name.trim().isEmpty()
                    && !name.equals(user.getName())) {

                user.setName(name);

                changed = true;
            }


            // Update profile image

            if (profileImage != null
                    && !profileImage.trim().isEmpty()
                    && !profileImage.equals(
                            user.getProfileImage())) {

                user.setProfileImage(
                        profileImage
                );

                changed = true;
            }


            if (changed) {

                userRepository.save(user);
            }
        }


        // =================================================
        // AUTHORITIES
        // =================================================

        Set<SimpleGrantedAuthority> authorities =
                new HashSet<>();


        user.getRoles()
                .forEach(role ->
                        authorities.add(
                                new SimpleGrantedAuthority(
                                        role.getName()
                                )
                        )
                );


        // =================================================
        // MAKE EMAIL THE PRINCIPAL NAME
        // =================================================

        attributes.put(
                "email",
                email
        );


        return new DefaultOAuth2User(
                authorities,
                attributes,
                "email"
        );
    }


    // =====================================================
    // GITHUB EMAIL
    // =====================================================

    private String getGitHubEmail(
            OAuth2UserRequest userRequest,
            Map<String, Object> attributes) {


        // -------------------------------------------------
        // First try the normal GitHub user response
        // -------------------------------------------------

        Object directEmail =
                attributes.get("email");


        if (directEmail != null
                && !directEmail.toString()
                        .trim()
                        .isEmpty()) {

            return directEmail.toString();
        }


        // -------------------------------------------------
        // GitHub email is private
        //
        // Get it from:
        //
        // GET https://api.github.com/user/emails
        //
        // The user:email scope allows this request.
        // -------------------------------------------------

        String accessToken =
                userRequest
                        .getAccessToken()
                        .getTokenValue();


        try {

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


            // -------------------------------------------------
            // Prefer primary + verified email
            // -------------------------------------------------

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


            // -------------------------------------------------
            // Fallback: any verified email
            // -------------------------------------------------

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


            // -------------------------------------------------
            // Final fallback: first email
            // -------------------------------------------------

            Map<String, Object> first =
                    emails.get(0);


            Object firstEmail =
                    first.get("email");


            if (firstEmail != null) {

                return firstEmail.toString();
            }


        } catch (Exception e) {

            throw new OAuth2AuthenticationException(
                    new OAuth2Error(
                            "github_email_error"
                    ),
                    "Could not retrieve email from GitHub.",
                    e
            );
        }


        return null;
    }


    // =====================================================
    // CREATE USER
    // =====================================================

    private User createNewUser(
            String name,
            String email,
            String profileImage) {


        User user =
                new User();


        user.setName(name);

        user.setEmail(email);

        // OAuth user does not have a local password

        user.setPassword(null);

        user.setProfileImage(
                profileImage
        );

        user.setEnabled(true);


        // =================================================
        // ROLE_USER
        // =================================================

        Role userRole =
                roleRepository
                        .findByName("ROLE_USER")
                        .orElseGet(() -> {

                            Role role =
                                    new Role();

                            role.setName(
                                    "ROLE_USER"
                            );

                            return roleRepository.save(
                                    role
                            );
                        });


        Set<Role> roles =
                new HashSet<>();


        roles.add(userRole);


        user.setRoles(roles);


        return userRepository.save(user);
    }


    // =====================================================
    // NAME
    // =====================================================

    private String extractName(
            String provider,
            Map<String, Object> attributes,
            String email) {


        // -------------------------------------------------
        // GITHUB
        // -------------------------------------------------

        if ("github".equalsIgnoreCase(provider)) {

            Object name =
                    attributes.get("name");


            if (name != null
                    && !name.toString()
                            .trim()
                            .isEmpty()) {

                return name.toString();
            }


            Object login =
                    attributes.get("login");


            if (login != null) {

                return login.toString();
            }
        }


        // -------------------------------------------------
        // OTHER PROVIDERS
        // -------------------------------------------------

        Object name =
                attributes.get("name");


        if (name != null
                && !name.toString()
                        .trim()
                        .isEmpty()) {

            return name.toString();
        }


        // -------------------------------------------------
        // EMAIL FALLBACK
        // -------------------------------------------------

        int atIndex =
                email.indexOf("@");


        if (atIndex > 0) {

            return email.substring(
                    0,
                    atIndex
            );
        }


        return email;
    }


    // =====================================================
    // PROFILE IMAGE
    // =====================================================

    private String extractProfileImage(
            String provider,
            Map<String, Object> attributes) {


        // GitHub

        if ("github".equalsIgnoreCase(provider)) {

            Object avatar =
                    attributes.get("avatar_url");


            if (avatar != null) {

                return avatar.toString();
            }
        }


        // Generic

        Object picture =
                attributes.get("picture");


        if (picture != null) {

            return picture.toString();
        }


        return null;
    }
}