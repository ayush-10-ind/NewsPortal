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


    // =====================================================
    // REGISTER
    // =====================================================

    @Transactional
    public User registerUser(RegisterRequestDTO request) {

        String email =
                request.getEmail()
                        .trim()
                        .toLowerCase();

        String username =
                request.getUsername()
                        .trim()
                        .toLowerCase();


        // =================================================
        // EMAIL DUPLICATE
        // =================================================

        if (userRepository.existsByEmail(email)) {

            throw new RuntimeException(
                    "An account with this email already exists."
            );
        }


        // =================================================
        // USERNAME DUPLICATE
        // =================================================

        if (userRepository.existsByUsername(username)) {

            throw new RuntimeException(
                    "This username is already taken. Please choose another."
            );
        }


        // =================================================
        // ROLE
        // =================================================

        Role userRole =
                roleRepository
                        .findByName("ROLE_USER")
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "ROLE_USER not found"
                                )
                        );


        // =================================================
        // CREATE USER
        // =================================================

        User user = new User();

        user.setName(
                request.getName().trim()
        );

        user.setUsername(username);

        user.setEmail(email);

        /*
         * Password remains NULL until
         * email verification.
         */
        user.setPassword(null);

        /*
         * Account cannot log in yet.
         */
        user.setEnabled(false);

        user.setEmailVerified(false);

        user.addRole(userRole);


        User savedUser =
                userRepository.save(user);


        // =================================================
        // CREATE VERIFICATION TOKEN
        // =================================================

        EmailVerificationToken token =
                new EmailVerificationToken(
                        savedUser
                );

        tokenRepository.save(token);


        // =================================================
        // SEND EMAIL
        // =================================================

        try {

            emailService.sendVerificationEmail(
                    savedUser,
                    token
            );

        } catch (Exception e) {

            /*
             * If the mail server immediately rejects
             * the email, don't leave a useless account.
             */

            tokenRepository.delete(token);
            userRepository.delete(savedUser);

            throw new RuntimeException(
                    "We could not send a verification email to this address. "
                    + "Please check the email address and try again."
            );
        }


        return savedUser;
    }


    // =====================================================
    // SET PASSWORD AFTER VERIFICATION
    // =====================================================

    @Transactional
    public void setPassword(
            User user,
            SetPasswordRequestDTO request) {

        if (!request.getPassword()
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