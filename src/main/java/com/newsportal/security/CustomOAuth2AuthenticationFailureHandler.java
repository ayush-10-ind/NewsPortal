package com.newsportal.security;

import java.io.IOException;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

@Component
public class CustomOAuth2AuthenticationFailureHandler
        implements AuthenticationFailureHandler {

    private static final Logger logger =
            LoggerFactory.getLogger(CustomOAuth2AuthenticationFailureHandler.class);

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception)
            throws IOException, ServletException {

        String errorCode = "provider_error";

        if (exception instanceof OAuth2AuthenticationException oauthException
                && oauthException.getError() != null
                && oauthException.getError().getErrorCode() != null
                && !oauthException.getError().getErrorCode().isBlank()) {

            errorCode = oauthException.getError().getErrorCode();
        }

        logger.error(
                "OAuth authentication failed: provider={}, errorCode={}, errorType={}, message={}",
                request.getRequestURI(),
                errorCode,
                exception.getClass().getSimpleName(),
                exception.getMessage(),
                exception
        );

        response.sendRedirect(
                "/login?oauth2Error=true&reason=" + errorCode
        );
    }
}
