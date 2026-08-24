package com.newsportal.security;

import com.newsportal.entity.Role;
import com.newsportal.entity.User;
import com.newsportal.repository.RoleRepository;
import com.newsportal.repository.UserRepository;

import org.springframework.security.core.authority.SimpleGrantedAuthority;

import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2UserAuthority;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;

@Service
public class CustomOidcUserService
        extends OidcUserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;


    public CustomOidcUserService(
            UserRepository userRepository,
            RoleRepository roleRepository) {

        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
    }


    // =====================================================
    // LOAD GOOGLE USER
    // =====================================================

    @Override
    public OidcUser loadUser(
            OidcUserRequest userRequest)
            throws OAuth2AuthenticationException {


        // Let Spring retrieve the Google user first

        OidcUser oidcUser =
                super.loadUser(userRequest);


        // =================================================
        // GOOGLE USER INFORMATION
        // =================================================

        String email =
                oidcUser.getEmail();


        String name =
                oidcUser.getFullName();


        String profileImage =
                oidcUser.getPicture();


        // =================================================
        // VALIDATE EMAIL
        // =================================================

        if (email == null
                || email.trim().isEmpty()) {

            throw new OAuth2AuthenticationException(
                    new org.springframework.security.oauth2.core.OAuth2Error(
                            "email_not_found"
                    ),
                    "Google account did not provide an email address."
            );
        }


        // =================================================
        // FALLBACK NAME
        // =================================================

        if (name == null
                || name.trim().isEmpty()) {

            int atIndex =
                    email.indexOf("@");


            if (atIndex > 0) {

                name =
                        email.substring(
                                0,
                                atIndex
                        );

            } else {

                name = email;
            }
        }


        // =================================================
        // FIND USER
        // =================================================

        User user =
                userRepository
                        .findByEmail(email)
                        .orElse(null);


        // =================================================
        // CREATE USER
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


            // ---------------------------------------------
            // UPDATE NAME
            // ---------------------------------------------

            if (name != null
                    && !name.equals(user.getName())) {

                user.setName(name);

                changed = true;
            }


            // ---------------------------------------------
            // UPDATE PROFILE IMAGE
            // ---------------------------------------------

            if (profileImage != null
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
        // APPLICATION ROLES
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
        // IMPORTANT
        // =================================================
        //
        // We use "email" as the principal name.
        //
        // This makes:
        //
        // authentication.getName()
        //
        // return:
        //
        // your@gmail.com
        //
        // instead of Google's numeric "sub".
        // =================================================

        return new DefaultOidcUser(
                authorities,
                oidcUser.getIdToken(),
                oidcUser.getUserInfo(),
                "email"
        );
    }


    // =====================================================
    // CREATE NEW USER
    // =====================================================

    private User createNewUser(
            String name,
            String email,
            String profileImage) {


        User user =
                new User();


        user.setName(name);

        user.setEmail(email);

        // Google users don't have a local password

        user.setPassword(null);

        user.setProfileImage(
                profileImage
        );

        user.setEnabled(true);


        // =================================================
        // DEFAULT ROLE
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
}