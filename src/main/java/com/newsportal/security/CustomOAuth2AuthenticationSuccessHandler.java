package com.newsportal.security;

import java.io.IOException;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

@Component
public class CustomOAuth2AuthenticationSuccessHandler
        implements AuthenticationSuccessHandler {

    private static final Logger logger =
            LoggerFactory.getLogger(CustomOAuth2AuthenticationSuccessHandler.class);

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication)
            throws IOException, ServletException {

        logger.info(
                "OAuth authentication succeeded: principal={}",
                authentication.getName()
        );

        response.sendRedirect("/");
    }
}
