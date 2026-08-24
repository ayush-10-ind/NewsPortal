package com.newsportal.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    private final CustomAccessDeniedHandler accessDeniedHandler;

    private final CustomAuthenticationFailureHandler authenticationFailureHandler;


    // =====================================================
    // CONSTRUCTOR
    // =====================================================

    public SecurityConfig(
            CustomAccessDeniedHandler accessDeniedHandler,
            CustomAuthenticationFailureHandler authenticationFailureHandler) {

        this.accessDeniedHandler =
                accessDeniedHandler;

        this.authenticationFailureHandler =
                authenticationFailureHandler;
    }


    // =====================================================
    // PASSWORD ENCODER
    // =====================================================

    @Bean
    public PasswordEncoder passwordEncoder() {

        return new BCryptPasswordEncoder();
    }


    // =====================================================
    // SECURITY FILTER CHAIN
    // =====================================================

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http) throws Exception {

        http


            // =================================================
            // AUTHORIZATION
            // =================================================

            .authorizeHttpRequests(auth -> auth


                // =============================================
                // PUBLIC PAGES + STATIC RESOURCES
                // =============================================

                .requestMatchers(

                    "/",

                    "/login",

                    "/register",

                    "/access-denied",


                    // CSS

                    "/css/**",

                    "/newsportal.css",


                    // JavaScript

                    "/js/**",

                    "/newsportal.js",


                    // Images

                    "/images/**",

                    "/uploads/**",
                    
                    // weather
                    
                    "/weather",
                    "/api/weather/**",


                    // =========================================
                    // IMPORTANT:
                    // OAuth2 authorization + callback
                    // =========================================

                    "/oauth2/**",

                    "/login/oauth2/**",


                    // =========================================
                    // API TESTING
                    // =========================================

                    "/api-integration/**",


                    // =========================================
                    // REST NEWS API
                    // =========================================

                    "/api/news/**",


                    // =========================================
                    // PUBLIC NEWS
                    // =========================================

                    "/news",

                    "/newsList",

                    "/viewNews/**",

                    "/news/**"

                ).permitAll()


                // =============================================
                // BOOKMARKS
                // =============================================

                .requestMatchers(

                    "/bookmarks",

                    "/bookmarks/**"

                ).authenticated()


                // =============================================
                // ADMIN
                // =============================================

                .requestMatchers(

                    "/admin/**"

                ).hasRole("ADMIN")


                // =============================================
                // EDITOR + ADMIN
                // =============================================

                .requestMatchers(

                    "/addNews",

                    "/saveNews",

                    "/editNews/**",

                    "/updateNews",

                    "/deleteNews/**"

                ).hasAnyRole(

                    "EDITOR",

                    "ADMIN"

                )


                // =============================================
                // EVERYTHING ELSE
                // =============================================

                .anyRequest().authenticated()
            )


            // =================================================
            // FORM LOGIN
            // =================================================

            .formLogin(form -> form

                .loginPage("/login")

                .defaultSuccessUrl(
                    "/",
                    true
                )

                .failureHandler(
                    authenticationFailureHandler
                )

                .permitAll()
            )


            // =================================================
            // GOOGLE + GITHUB OAUTH2 LOGIN
            // =================================================

            .oauth2Login(oauth -> oauth

                // ---------------------------------------------
                // OAuth login entry point
                // ---------------------------------------------

                .loginPage("/login")


                // ---------------------------------------------
                // Successful OAuth login
                // ---------------------------------------------

                .defaultSuccessUrl(
                    "/",
                    true
                )


                // ---------------------------------------------
                // OAuth failure
                // ---------------------------------------------

                .failureUrl(
                    "/login?oauth2Error=true"
                )
            )


            // =================================================
            // LOGOUT
            // =================================================

            .logout(logout -> logout

                .logoutSuccessUrl("/")

                .permitAll()
            )


            // =================================================
            // ACCESS DENIED
            // =================================================

            .exceptionHandling(exception -> exception

                .accessDeniedHandler(
                    accessDeniedHandler
                )
            );


        return http.build();
    }
}