package com.newsportal.security;

import com.newsportal.entity.Role;
import com.newsportal.entity.User;
import com.newsportal.repository.RoleRepository;
import com.newsportal.repository.UserRepository;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;

@Service
public class CustomOidcUserService extends OidcUserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    public CustomOidcUserService(
            UserRepository userRepository,
            RoleRepository roleRepository) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
    }

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest)
            throws OAuth2AuthenticationException {

        OidcUser oidcUser = super.loadUser(userRequest);
        String email = oidcUser.getEmail();

        if (email == null || email.isBlank()) {
            throw oauthException(
                    "email_not_found",
                    "Google account did not provide an email address."
            );
        }

        Boolean emailVerified = oidcUser.getClaimAsBoolean("email_verified");
        if (!Boolean.TRUE.equals(emailVerified)) {
            throw oauthException(
                    "email_not_verified",
                    "Google did not confirm that this email address is verified."
            );
        }

        email = email.trim().toLowerCase();

        String name = oidcUser.getFullName();
        if (name == null || name.isBlank()) {
            int atIndex = email.indexOf("@");
            name = atIndex > 0 ? email.substring(0, atIndex) : "News Portal User";
        }

        User user = userRepository.findByEmail(email).orElse(null);

        if (user == null) {
            user = createNewUser(name, email, oidcUser.getPicture());
        } else {
            updateExistingUser(user, name, oidcUser.getPicture());
        }

        Set<SimpleGrantedAuthority> authorities = new HashSet<>();
        user.getRoles().forEach(role ->
                authorities.add(new SimpleGrantedAuthority(role.getName()))
        );

        return new DefaultOidcUser(
                authorities,
                oidcUser.getIdToken(),
                oidcUser.getUserInfo(),
                "email"
        );
    }

    private void updateExistingUser(
            User user,
            String name,
            String profileImage) {

        boolean changed = false;

        if (!user.isEnabled() && !user.isEmailVerified()) {
            user.setEnabled(true);
            user.setEmailVerified(true);
            changed = true;
        } else if (!user.isEnabled()) {
            throw oauthException(
                    "account_disabled",
                    "Your AgniPress account has been disabled."
            );
        } else if (!user.isEmailVerified()) {
            user.setEmailVerified(true);
            changed = true;
        }

        if (name != null && !name.isBlank()
                && !name.equals(user.getName())) {
            user.setName(name);
            changed = true;
        }

        if (profileImage != null
                && !profileImage.equals(user.getProfileImage())) {
            user.setProfileImage(profileImage);
            changed = true;
        }

        if (user.getUsername() == null || user.getUsername().isBlank()) {
            user.setUsername(generateUniqueUsername(name, user.getEmail()));
            changed = true;
        }

        if (user.getRoles() == null || user.getRoles().isEmpty()) {
            user.addRole(getUserRole());
            changed = true;
        }

        if (changed) {
            userRepository.save(user);
        }
    }

    private User createNewUser(
            String name,
            String email,
            String profileImage) {

        User user = new User();
        user.setName(name);
        user.setEmail(email);
        user.setUsername(generateUniqueUsername(name, email));
        user.setPassword(null);
        user.setProfileImage(profileImage);
        user.setEnabled(true);
        user.setEmailVerified(true);
        user.addRole(getUserRole());

        return userRepository.save(user);
    }

    private Role getUserRole() {
        return roleRepository
                .findByName("ROLE_USER")
                .orElseGet(() -> {
                    Role role = new Role();
                    role.setName("ROLE_USER");
                    return roleRepository.save(role);
                });
    }

    private String generateUniqueUsername(
            String name,
            String email) {

        String base = name;

        if (base == null || base.isBlank()) {
            int atIndex = email.indexOf("@");
            base = atIndex > 0 ? email.substring(0, atIndex) : "user";
        }

        base = base
                .trim()
                .toLowerCase()
                .replaceAll("[^a-z0-9_]", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_+|_+$", "");

        if (base.isBlank()) {
            base = "user";
        }

        if (base.length() > 40) {
            base = base.substring(0, 40);
        }

        String username = base;
        int counter = 1;

        while (userRepository.existsByUsername(username)) {
            String suffix = "_" + counter;
            int maxBaseLength = 50 - suffix.length();
            String shortened = base.length() > maxBaseLength
                    ? base.substring(0, maxBaseLength)
                    : base;
            username = shortened + suffix;
            counter++;
        }

        return username;
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
