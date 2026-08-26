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

        this.userRepository =
                userRepository;

        this.roleRepository =
                roleRepository;

        this.passwordEncoder =
                passwordEncoder;

        this.tokenRepository =
                tokenRepository;

        this.emailService =
                emailService;
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

            /*
             * =================================================
             * COMPLETED ACCOUNT
             * =================================================
             *
             * If email has already been verified and a password
             * exists, this is a real registered account.
             */

            if (existingUser.isEmailVerified()
                    && existingUser.getPassword() != null
                    && !existingUser.getPassword().isBlank()) {

                throw new RuntimeException(
                        "An account with this email already exists. "
                        + "Please sign in instead."
                );
            }


            /*
             * =================================================
             * PENDING ACCOUNT
             * =================================================
             *
             * The user started registration but never completed
             * verification/password creation.
             *
             * We reuse this account instead of creating another
             * users row.
             */

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
            // UPDATE NAME
            // -------------------------------------------------

            existingUser.setName(
                    request.getName().trim()
            );


            // -------------------------------------------------
            // RESET ACCOUNT STATE
            // -------------------------------------------------

            existingUser.setPassword(null);

            existingUser.setEnabled(false);

            existingUser.setEmailVerified(false);


            // -------------------------------------------------
            // SAVE UPDATED USER
            // -------------------------------------------------

            User savedUser =
                    userRepository.save(existingUser);


            // -------------------------------------------------
            // DELETE OLD VERIFICATION TOKEN
            // -------------------------------------------------

            tokenRepository.deleteAllByUser(
                    savedUser
            );

            /*
             * Flush immediately so the old token's unique
             * user_id constraint is definitely removed before
             * inserting the new token.
             */

            tokenRepository.flush();


            // -------------------------------------------------
            // CREATE NEW TOKEN
            // -------------------------------------------------

            EmailVerificationToken newToken =
                    new EmailVerificationToken(
                            savedUser
                    );


            tokenRepository.save(newToken);


            // -------------------------------------------------
            // SEND NEW VERIFICATION EMAIL
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

        // -----------------------------------------------------
        // USERNAME DUPLICATE
        // -----------------------------------------------------

        if (userRepository.existsByUsername(username)) {

            throw new RuntimeException(
                    "This username is already taken. "
                    + "Please choose another."
            );
        }


        // =====================================================
        // ROLE
        // =====================================================

        Role userRole =
                roleRepository
                        .findByName("ROLE_USER")
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "ROLE_USER not found"
                                )
                        );


        // =====================================================
        // CREATE USER
        // =====================================================

        User user =
                new User();

        user.setName(
                request.getName().trim()
        );

        user.setUsername(
                username
        );

        user.setEmail(
                email
        );


        /*
         * Password is deliberately NULL until the user
         * verifies their email and creates a password.
         */

        user.setPassword(null);


        /*
         * Account cannot log in until verification
         * and password creation are completed.
         */

        user.setEnabled(false);

        user.setEmailVerified(false);


        // -----------------------------------------------------
        // ROLE
        // -----------------------------------------------------

        user.addRole(userRole);


        // -----------------------------------------------------
        // SAVE USER
        // -----------------------------------------------------

        User savedUser =
                userRepository.save(user);


        // =====================================================
        // CREATE VERIFICATION TOKEN
        // =====================================================

        EmailVerificationToken token =
                new EmailVerificationToken(
                        savedUser
                );


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

            /*
             * If the email cannot be sent, remove both the
             * token and the pending user.
             */

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
    // SET PASSWORD AFTER EMAIL VERIFICATION
    // =====================================================

    @Transactional
    public void setPassword(
            User user,
            SetPasswordRequestDTO request) {


        // =================================================
        // PASSWORD MATCH
        // =================================================

        if (!request.getPassword()
                .equals(request.getConfirmPassword())) {

            throw new RuntimeException(
                    "Passwords do not match."
            );
        }


        // =================================================
        // SET PASSWORD
        // =================================================

        user.setPassword(
                passwordEncoder.encode(
                        request.getPassword()
                )
        );


        // =================================================
        // VERIFY EMAIL
        // =================================================

        user.setEmailVerified(true);


        // =================================================
        // ENABLE ACCOUNT
        // =================================================

        user.setEnabled(true);


        // =================================================
        // SAVE USER
        // =================================================

        userRepository.save(user);
    }
}