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


    // =====================================================
    // CONSTRUCTOR
    // =====================================================

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


    // =====================================================
    // REGISTER / RESEND VERIFICATION
    // =====================================================

    @Transactional
    public User registerUser(
            RegisterRequestDTO request) {

        String email =
                request.getEmail()
                        .trim()
                        .toLowerCase();

        String username =
                request.getUsername()
                        .trim()
                        .toLowerCase();

        // =================================================
        // CHECK IF EMAIL ALREADY EXISTS
        // =================================================

        User existingUser =
                userRepository
                        .findByEmail(email)
                        .orElse(null);

        // =================================================
        // EXISTING USER
        // =================================================

        if (existingUser != null) {

            if (existingUser.isEmailVerified()
                    && existingUser.getPassword() != null
                    && !existingUser.getPassword().isBlank()) {

                throw new RuntimeException(
                        "An account with this email already exists. "
                        + "Please sign in instead."
                );
            }

            // -------------------------------------------------
            // CHECK USERNAME
            // -------------------------------------------------

            if (!existingUser.getUsername()
                    .equalsIgnoreCase(username)) {

                if (userRepository.existsByUsername(username)) {

                    throw new RuntimeException(
                            "This username is already taken. "
                            + "Please choose another."
                    );
                }

                existingUser.setUsername(username);
            }

            // -------------------------------------------------
            // UPDATE PENDING ACCOUNT
            // -------------------------------------------------

            existingUser.setName(
                    request.getName().trim()
            );

            existingUser.setPassword(null);
            existingUser.setEnabled(false);
            existingUser.setEmailVerified(false);

            User savedUser =
                    userRepository.save(existingUser);

            // -------------------------------------------------
            // REPLACE OLD TOKEN
            // -------------------------------------------------

            tokenRepository.deleteAllByUser(savedUser);
            tokenRepository.flush();

            EmailVerificationToken newToken =
                    new EmailVerificationToken(savedUser);

            tokenRepository.save(newToken);

            // -------------------------------------------------
            // SEND EMAIL
            // -------------------------------------------------

            try {

                emailService.sendVerificationEmail(
                        savedUser,
                        newToken
                );

            } catch (Exception e) {

                tokenRepository.delete(newToken);

                throw new RuntimeException(
                        "We could not send a verification email "
                        + "to this address. Please check the "
                        + "email address and try again."
                );
            }

            return savedUser;
        }

        // =====================================================
        // NEW USER
        // =====================================================

        if (userRepository.existsByUsername(username)) {

            throw new RuntimeException(
                    "This username is already taken. "
                    + "Please choose another."
            );
        }

        Role userRole =
                roleRepository
                        .findByName("ROLE_USER")
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "ROLE_USER not found"
                                )
                        );

        User user = new User();

        user.setName(
                request.getName().trim()
        );

        user.setUsername(username);
        user.setEmail(email);
        user.setPassword(null);
        user.setEnabled(false);
        user.setEmailVerified(false);
        user.addRole(userRole);

        User savedUser =
                userRepository.save(user);

        // =====================================================
        // CREATE VERIFICATION TOKEN
        // =====================================================

        EmailVerificationToken token =
                new EmailVerificationToken(savedUser);

        tokenRepository.save(token);

        // =====================================================
        // SEND VERIFICATION EMAIL
        // =====================================================

        try {

            emailService.sendVerificationEmail(
                    savedUser,
                    token
            );

        } catch (Exception e) {

            tokenRepository.delete(token);
            userRepository.delete(savedUser);

            throw new RuntimeException(
                    "We could not send a verification email "
                    + "to this address. Please check the "
                    + "email address and try again."
            );
        }

        return savedUser;
    }


    // =====================================================
    // COMPLETE EMAIL VERIFICATION + PASSWORD CREATION
    // =====================================================

    @Transactional
    public void completeEmailVerification(
            String token,
            SetPasswordRequestDTO request) {

        // -------------------------------------------------
        // FIND TOKEN INSIDE THE SAME TRANSACTION
        // -------------------------------------------------

        EmailVerificationToken verificationToken =
                tokenRepository
                        .findByToken(token)
                        .orElse(null);

        if (verificationToken == null) {

            throw new RuntimeException(
                    "This verification link is invalid or has already been used."
            );
        }

        if (verificationToken.isExpired()) {

            throw new RuntimeException(
                    "This verification link has expired. Please register again."
            );
        }

        // -------------------------------------------------
        // PASSWORD MATCH
        // -------------------------------------------------

        if (request.getPassword() == null
                || request.getConfirmPassword() == null
                || !request.getPassword()
                        .equals(request.getConfirmPassword())) {

            throw new RuntimeException(
                    "Passwords do not match."
            );
        }

        // -------------------------------------------------
        // GET USER
        // -------------------------------------------------

        User user = verificationToken.getUser();

        // -------------------------------------------------
        // SET PASSWORD
        // -------------------------------------------------

        user.setPassword(
                passwordEncoder.encode(
                        request.getPassword()
                )
        );

        user.setEmailVerified(true);
        user.setEnabled(true);

        userRepository.save(user);

        // -------------------------------------------------
        // DELETE TOKEN ATOMICALLY
        // -------------------------------------------------

        tokenRepository.delete(verificationToken);
    }


    // =====================================================
    // LEGACY PASSWORD METHOD
    // =====================================================

    @Transactional
    public void setPassword(
            User user,
            SetPasswordRequestDTO request) {

        if (request.getPassword() == null
                || request.getConfirmPassword() == null
                || !request.getPassword()
                        .equals(request.getConfirmPassword())) {

            throw new RuntimeException(
                    "Passwords do not match."
            );
        }

        user.setPassword(
                passwordEncoder.encode(
                        request.getPassword()
                )
        );

        user.setEmailVerified(true);
        user.setEnabled(true);

        userRepository.save(user);
    }
}
