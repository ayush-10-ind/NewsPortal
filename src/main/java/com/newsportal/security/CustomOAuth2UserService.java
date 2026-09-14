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
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final WebClient webClient;

    public CustomOAuth2UserService(
            UserRepository userRepository,
            RoleRepository roleRepository,
            WebClient.Builder webClientBuilder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.webClient = webClientBuilder.build();
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest)
            throws OAuth2AuthenticationException {

        OAuth2User oauthUser = super.loadUser(userRequest);

        String registrationId = userRequest
                .getClientRegistration()
                .getRegistrationId();

        String email = extractEmail(
                oauthUser,
                userRequest,
                registrationId
        );

        if (email == null || email.isBlank()) {
            throw oauthException(
                    "email_not_found",
                    "Unable to retrieve a verified email from " + registrationId
            );
        }

        email = email.trim().toLowerCase();

        User user = userRepository.findByEmail(email).orElse(null);

        if (user == null) {
            user = createNewOAuthUser(oauthUser, email, registrationId);
        } else {
            updateExistingOAuthUser(user, oauthUser, registrationId);
        }

        return buildOAuthUser(oauthUser, user);
    }

    private void updateExistingOAuthUser(
            User user,
            OAuth2User oauthUser,
            String registrationId) {

        if (!user.isEnabled() && !user.isEmailVerified()) {
            user.setEmailVerified(true);
            user.setEnabled(true);
        } else if (!user.isEnabled()) {
            throw oauthException(
                    "account_disabled",
                    "Your AgniPress account has been disabled."
            );
        } else if (!user.isEmailVerified()) {
            user.setEmailVerified(true);
        }

        String name = extractName(oauthUser);
        if (name != null && !name.isBlank()) {
            user.setName(name);
        }

        Object avatar = oauthUser.getAttributes().get("avatar_url");
        if (avatar != null && !avatar.toString().isBlank()) {
            user.setProfileImage(avatar.toString());
        }

        if (user.getUsername() == null || user.getUsername().isBlank()) {
            user.setUsername(
                    generateUniqueUsername(
                            oauthUser,
                            user.getEmail(),
                            registrationId
                    )
            );
        }

        if (user.getRoles() == null || user.getRoles().isEmpty()) {
            user.addRole(getUserRole());
        }

        userRepository.save(user);
    }

    private User createNewOAuthUser(
            OAuth2User oauthUser,
            String email,
            String registrationId) {

        User user = new User();

        String name = extractName(oauthUser);
        if (name == null || name.isBlank()) {
            int atIndex = email.indexOf("@");
            name = atIndex > 0 ? email.substring(0, atIndex) : "News Portal User";
        }

        user.setName(name);
        user.setUsername(generateUniqueUsername(oauthUser, email, registrationId));
        user.setEmail(email);
        user.setPassword(null);
        user.setEnabled(true);
        user.setEmailVerified(true);

        Object avatar = oauthUser.getAttributes().get("avatar_url");
        if (avatar != null && !avatar.toString().isBlank()) {
            user.setProfileImage(avatar.toString());
        }

        user.addRole(getUserRole());

        return userRepository.save(user);
    }

    private Role getUserRole() {
        return roleRepository
                .findByName("ROLE_USER")
                .orElseThrow(() ->
                        new RuntimeException("ROLE_USER not found")
                );
    }

    private String generateUniqueUsername(
            OAuth2User oauthUser,
            String email,
            String registrationId) {

        String baseUsername = null;

        if ("github".equalsIgnoreCase(registrationId)) {
            Object login = oauthUser.getAttributes().get("login");
            if (login != null && !login.toString().isBlank()) {
                baseUsername = login.toString();
            }
        }

        if (baseUsername == null || baseUsername.isBlank()) {
            baseUsername = extractName(oauthUser);
        }

        if (baseUsername == null || baseUsername.isBlank()) {
            int atIndex = email.indexOf("@");
            baseUsername = atIndex > 0 ? email.substring(0, atIndex) : "user";
        }

        baseUsername = baseUsername
                .toLowerCase()
                .replaceAll("[^a-z0-9_]", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_+|_+$", "");

        if (baseUsername.isBlank()) {
            baseUsername = "user";
        }

        if (baseUsername.length() > 40) {
            baseUsername = baseUsername.substring(0, 40);
        }

        String username = baseUsername;
        int counter = 1;

        while (userRepository.existsByUsername(username)) {
            String suffix = "_" + counter;
            int maxBaseLength = 50 - suffix.length();
            String shortenedBase = baseUsername.length() > maxBaseLength
                    ? baseUsername.substring(0, maxBaseLength)
                    : baseUsername;
            username = shortenedBase + suffix;
            counter++;
        }

        return username;
    }

    private OAuth2User buildOAuthUser(
            OAuth2User oauthUser,
            User user) {

        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        for (Role role : user.getRoles()) {
            authorities.add(new SimpleGrantedAuthority(role.getName()));
        }

        Map<String, Object> attributes =
                new HashMap<>(oauthUser.getAttributes());

        attributes.put("email", user.getEmail());
        attributes.put("username", user.getUsername());
        attributes.put("displayName", user.getName());

        if (user.getProfileImage() != null
                && !user.getProfileImage().isBlank()) {
            attributes.put("profileImage", user.getProfileImage());
        }

        return new DefaultOAuth2User(
                authorities,
                attributes,
                "email"
        );
    }

    private String extractEmail(
            OAuth2User oauthUser,
            OAuth2UserRequest userRequest,
            String registrationId) {

        if ("github".equalsIgnoreCase(registrationId)) {
            // The GitHub profile's email field is not enough to prove
            // verification, so always use the email endpoint for GitHub.
            return extractGitHubEmail(userRequest);
        }

        Object email = oauthUser.getAttributes().get("email");
        if (email != null && !email.toString().isBlank()) {
            return email.toString();
        }

        return null;
    }

    @SuppressWarnings("unchecked")
    private String extractGitHubEmail(
            OAuth2UserRequest userRequest) {

        try {
            String accessToken = userRequest
                    .getAccessToken()
                    .getTokenValue();

            List<Map<String, Object>> emails = webClient
                    .get()
                    .uri("https://api.github.com/user/emails")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .header(HttpHeaders.ACCEPT, "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .retrieve()
                    .bodyToMono(List.class)
                    .block(Duration.ofSeconds(5));

            if (emails == null || emails.isEmpty()) {
                return null;
            }

            for (Map<String, Object> emailData : emails) {
                Object email = emailData.get("email");
                Object primary = emailData.get("primary");
                Object verified = emailData.get("verified");

                if (email != null
                        && Boolean.TRUE.equals(primary)
                        && Boolean.TRUE.equals(verified)) {
                    return email.toString();
                }
            }

            for (Map<String, Object> emailData : emails) {
                Object email = emailData.get("email");
                Object verified = emailData.get("verified");

                if (email != null && Boolean.TRUE.equals(verified)) {
                    return email.toString();
                }
            }

            return null;

        } catch (Exception e) {
            throw oauthException(
                    "github_email_error",
                    "Could not retrieve a verified email from GitHub."
            );
        }
    }

    private String extractName(OAuth2User oauthUser) {
        Map<String, Object> attributes = oauthUser.getAttributes();

        Object name = attributes.get("name");
        if (name != null && !name.toString().isBlank()) {
            return name.toString();
        }

        Object login = attributes.get("login");
        if (login != null && !login.toString().isBlank()) {
            return login.toString();
        }

        Object username = attributes.get("preferred_username");
        if (username != null && !username.toString().isBlank()) {
            return username.toString();
        }

        return null;
    }

    private OAuth2AuthenticationException oauthException(
            String errorCode,
            String message) {

        return new OAuth2AuthenticationException(
                new OAuth2Error(errorCode),
                message
        );
    }
}
