package com.newsportal.service;

import com.newsportal.dto.RegisterRequestDTO;
import com.newsportal.dto.SetPasswordRequestDTO;
import com.newsportal.entity.EmailVerificationToken;
import com.newsportal.entity.Role;
import com.newsportal.entity.User;
import com.newsportal.repository.EmailVerificationTokenRepository;
import com.newsportal.repository.RoleRepository;
import com.newsportal.repository.UserRepository;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailVerificationTokenRepository tokenRepository;
    private final EmailService emailService;

    public UserService(
            UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            EmailVerificationTokenRepository tokenRepository,
            EmailService emailService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenRepository = tokenRepository;
        this.emailService = emailService;
    }

    public User registerUser(RegisterRequestDTO request) {

        String email = normalize(request.getEmail());
        String username = normalize(request.getUsername());
        String name = request.getName().trim();

        User existingUser = userRepository.findByEmail(email).orElse(null);

        if (existingUser != null) {

            // A verified account includes OAuth-only accounts, whose local
            // password is intentionally null. Never reset such an account.
            if (existingUser.isEmailVerified()) {
                throw new RuntimeException(
                        "An account with this email already exists. Please sign in or use your social login."
                );
            }

            if (!existingUser.getUsername().equalsIgnoreCase(username)
                    && userRepository.existsByUsername(username)) {
                throw new RuntimeException(
                        "This username is already taken. Please choose another."
                );
            }

            existingUser.setUsername(username);
            existingUser.setName(name);
            existingUser.setPassword(null);
            existingUser.setEnabled(false);
            existingUser.setEmailVerified(false);

            User savedUser = userRepository.save(existingUser);

            tokenRepository.deleteAllByUser(savedUser);
            tokenRepository.flush();

            EmailVerificationToken token =
                    new EmailVerificationToken(savedUser);

            tokenRepository.save(token);
            sendVerificationEmailOrThrow(savedUser, token);

            return savedUser;
        }

        if (userRepository.existsByUsername(username)) {
            throw new RuntimeException(
                    "This username is already taken. Please choose another."
            );
        }

        Role userRole = roleRepository
                .findByName("ROLE_USER")
                .orElseThrow(() ->
                        new RuntimeException("ROLE_USER not found")
                );

        User user = new User();
        user.setName(name);
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword(null);
        user.setEnabled(false);
        user.setEmailVerified(false);
        user.addRole(userRole);

        User savedUser = userRepository.save(user);

        EmailVerificationToken token =
                new EmailVerificationToken(savedUser);

        tokenRepository.save(token);

        // Email delivery happens after the account/token persistence so a
        // slow or failing mail provider cannot roll back the account.
        sendVerificationEmailOrThrow(savedUser, token);

        return savedUser;
    }

    private void sendVerificationEmailOrThrow(
            User user,
            EmailVerificationToken token) {

        try {
            emailService.sendVerificationEmail(user, token);
        } catch (Exception e) {
            throw new RuntimeException(
                    "Your account was created, but we could not send the verification email. "
                    + "Please try the resend verification option.",
                    e
            );
        }
    }

    public boolean resendVerificationEmail(String email) {

        String normalizedEmail = normalize(email);

        User user = userRepository
                .findByEmail(normalizedEmail)
                .orElse(null);

        if (user == null
                || user.isEmailVerified()
                || (user.getPassword() != null && !user.getPassword().isBlank())) {
            return false;
        }

        tokenRepository.deleteAllByUser(user);
        tokenRepository.flush();

        EmailVerificationToken token =
                new EmailVerificationToken(user);

        tokenRepository.save(token);
        sendVerificationEmailOrThrow(user, token);

        return true;
    }

    private String normalize(String value) {
        return value == null
                ? ""
                : value.trim().toLowerCase();
    }

    @Transactional
    public void completeEmailVerification(
            String token,
            SetPasswordRequestDTO request) {

        EmailVerificationToken verificationToken = tokenRepository
                .findByToken(token)
                .orElse(null);

        if (verificationToken == null) {
            throw new RuntimeException(
                    "This verification link is invalid or has already been used."
            );
        }

        if (verificationToken.isExpired()) {
            throw new RuntimeException(
                    "This verification link has expired. Please request a new verification email."
            );
        }

        if (request.getPassword() == null
                || request.getConfirmPassword() == null
                || !request.getPassword().equals(request.getConfirmPassword())) {
            throw new RuntimeException("Passwords do not match.");
        }

        User user = verificationToken.getUser();

        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setEmailVerified(true);
        user.setEnabled(true);

        userRepository.save(user);
        tokenRepository.delete(verificationToken);
    }

    @Transactional
    public void setPassword(
            User user,
            SetPasswordRequestDTO request) {

        if (request.getPassword() == null
                || request.getConfirmPassword() == null
                || !request.getPassword().equals(request.getConfirmPassword())) {
            throw new RuntimeException("Passwords do not match.");
        }

        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setEmailVerified(true);
        user.setEnabled(true);

        userRepository.save(user);
    }
}
