package com.newsportal.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    private final CustomAccessDeniedHandler accessDeniedHandler;

    private final CustomAuthenticationFailureHandler authenticationFailureHandler;

    private final CustomOAuth2UserService customOAuth2UserService;


    // =====================================================
    // CONSTRUCTOR
    // =====================================================

    public SecurityConfig(

            CustomAccessDeniedHandler accessDeniedHandler,

            CustomAuthenticationFailureHandler authenticationFailureHandler,

            CustomOAuth2UserService customOAuth2UserService) {

        this.accessDeniedHandler =
                accessDeniedHandler;

        this.authenticationFailureHandler =
                authenticationFailureHandler;

        this.customOAuth2UserService =
                customOAuth2UserService;
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
                // PUBLIC PAGES
                // =============================================

                .requestMatchers(

                    "/",

                    "/login",

                    "/register",

                    "/verify-email",

                    "/access-denied",


                    // =========================================
                    // LIVE NEWS
                    // =========================================

                    "/live-news",

                    "/api/live-channels",


                    // =========================================
                    // STATIC CSS
                    // =========================================

                    "/css/**",

                    "/newsportal.css",


                    // =========================================
                    // STATIC JAVASCRIPT
                    // =========================================

                    "/js/**",

                    "/newsportal.js",


                    // =========================================
                    // IMAGES / UPLOADS
                    // =========================================

                    "/images/**",

                    "/uploads/**",


                    // =========================================
                    // WEATHER
                    // =========================================

                    "/weather",

                    "/api/weather/**",


                    // =========================================
                    // OAUTH2
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

                .loginPage("/login")

                .userInfoEndpoint(userInfo ->
                    userInfo.userService(
                        customOAuth2UserService
                    )
                )

                .defaultSuccessUrl(
                    "/",
                    true
                )

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