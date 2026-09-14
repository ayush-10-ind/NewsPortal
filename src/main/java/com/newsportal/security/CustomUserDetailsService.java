package com.newsportal.security;

import com.newsportal.entity.User;
import com.newsportal.repository.UserRepository;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class CustomUserDetailsService
        implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email)
            throws UsernameNotFoundException {

        String normalizedEmail = email == null
                ? ""
                : email.trim().toLowerCase();

        User user = userRepository
                .findByEmail(normalizedEmail)
                .orElseThrow(() ->
                        new UsernameNotFoundException("Invalid login credentials.")
                );

        // OAuth-only accounts intentionally have no local password.
        // Treat a password login attempt as invalid instead of passing
        // a null password into Spring Security's User builder.
        if (user.getPassword() == null || user.getPassword().isBlank()) {
            throw new UsernameNotFoundException("Invalid login credentials.");
        }

        return org.springframework.security.core.userdetails.User
                .withUsername(user.getEmail())
                .password(user.getPassword())
                .authorities(
                        user.getRoles()
                                .stream()
                                .map(role ->
                                        new SimpleGrantedAuthority(role.getName())
                                )
                                .toList()
                )
                .disabled(!user.isEnabled() || !user.isEmailVerified())
                .build();
    }
}
