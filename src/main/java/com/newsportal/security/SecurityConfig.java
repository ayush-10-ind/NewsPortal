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

    public SecurityConfig(
            CustomAccessDeniedHandler accessDeniedHandler,
            CustomAuthenticationFailureHandler authenticationFailureHandler,
            CustomOAuth2UserService customOAuth2UserService) {
        this.accessDeniedHandler = accessDeniedHandler;
        this.authenticationFailureHandler = authenticationFailureHandler;
        this.customOAuth2UserService = customOAuth2UserService;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/",
                    "/login",
                    "/register",
                    "/verify-email",
                    "/access-denied",
                    "/live-news",
                    "/api/live-channels",
                    "/css/**",
                    "/newsportal.css",
                    "/register-guide.css",
                    "/js/**",
                    "/newsportal.js",
                    "/audio/**",
                    "/images/**",
                    "/uploads/**",
                    "/weather",
                    "/api/weather/**",
                    "/oauth2/**",
                    "/login/oauth2/**",
                    "/api-integration/**",
                    "/api/news/**",
                    "/news",
                    "/newsList",
                    "/viewNews/**",
                    "/news/**"
                ).permitAll()
                .requestMatchers(
                    "/bookmarks",
                    "/bookmarks/**"
                ).authenticated()
                .requestMatchers(
                    "/admin/**"
                ).hasRole("ADMIN")
                .requestMatchers(
                    "/addNews",
                    "/saveNews",
                    "/editNews/**",
                    "/updateNews",
                    "/deleteNews/**"
                ).hasAnyRole("EDITOR", "ADMIN")
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .defaultSuccessUrl("/", true)
                .failureHandler(authenticationFailureHandler)
                .permitAll()
            )
            .oauth2Login(oauth -> oauth
                .loginPage("/login")
                .userInfoEndpoint(userInfo -> userInfo.userService(customOAuth2UserService))
                .defaultSuccessUrl("/", true)
                .failureUrl("/login?oauth2Error=true")
            )
            .logout(logout -> logout
                .logoutSuccessUrl("/")
                .permitAll()
            )
            .exceptionHandling(exception -> exception
                .accessDeniedHandler(accessDeniedHandler)
            );

        return http.build();
    }
}
