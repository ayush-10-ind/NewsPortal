package com.newsportal.service;

import com.newsportal.entity.EmailVerificationToken;
import com.newsportal.entity.User;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@Service
public class EmailService {

    private static final Logger logger =
            LoggerFactory.getLogger(EmailService.class);

    private static final String MAILJET_API_URL =
            "https://api.mailjet.com/v3.1/send";

    private final WebClient webClient;

    @Value("${mailjet.api-key:}")
    private String apiKey;

    @Value("${mailjet.secret-key:}")
    private String secretKey;

    @Value("${mailjet.from-email:}")
    private String fromEmail;

    // Railway should set APP_BASE_URL to the public AgniPress URL.
    // Local development falls back to the application's default port.
    @Value("${APP_BASE_URL:http://localhost:8080}")
    private String baseUrl;

    public EmailService(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }

    public void sendVerificationEmail(
            User user,
            EmailVerificationToken token) {

        validateConfiguration();

        String verificationUrl =
                normalizeBaseUrl()
                        + "/verify-email?token="
                        + token.getToken();

        String html = """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <title>Verify your AgniPress account</title>
                </head>
                <body style="margin:0;padding:0;background:#f5f2eb;font-family:Arial,Helvetica,sans-serif;color:#111111;">
                    <div style="max-width:600px;margin:40px auto;background:#ffffff;border:1px solid #ddd8cf;">
                        <div style="padding:28px 32px;border-bottom:1px solid #ddd8cf;">
                            <div style="font-size:28px;font-weight:700;letter-spacing:-1px;">
                                Agni<span style="font-weight:400;">Press</span>
                            </div>
                            <div style="margin-top:6px;font-size:11px;letter-spacing:2px;color:#b83220;">
                                INDIA. IN THE MOMENT.
                            </div>
                        </div>
                        <div style="padding:40px 32px;">
                            <div style="font-size:11px;font-weight:bold;letter-spacing:2px;color:#b83220;margin-bottom:15px;">
                                WELCOME TO AGNIPRESS
                            </div>
                            <h1 style="margin:0 0 18px 0;font-family:Georgia,serif;font-size:38px;font-weight:400;line-height:1.15;">
                                Verify your email.
                            </h1>
                            <p style="font-size:16px;line-height:1.7;color:#555555;">
                                Hello %s,
                            </p>
                            <p style="font-size:16px;line-height:1.7;color:#555555;">
                                Thanks for creating your AgniPress account.
                                Please verify your email address to continue
                                and create your password.
                            </p>
                            <div style="margin:32px 0;">
                                <a href="%s" style="display:inline-block;background:#111111;color:#ffffff;text-decoration:none;padding:16px 28px;font-size:12px;font-weight:bold;letter-spacing:1.5px;">
                                    VERIFY EMAIL
                                </a>
                            </div>
                            <p style="font-size:13px;line-height:1.6;color:#777777;">
                                This verification link will expire after 24 hours.
                            </p>
                            <p style="font-size:13px;line-height:1.6;color:#777777;">
                                If you did not create an AgniPress account, you can safely ignore this email.
                            </p>
                        </div>
                        <div style="padding:20px 32px;border-top:1px solid #ddd8cf;font-size:11px;color:#888888;">
                            AgniPress &nbsp;·&nbsp; Independent News
                        </div>
                    </div>
                </body>
                </html>
                """
                .formatted(
                        escapeHtml(user.getName()),
                        verificationUrl
                );

        Map<String, Object> message = Map.of(
                "From", Map.of(
                        "Email", fromEmail,
                        "Name", "AgniPress"
                ),
                "To", List.of(Map.of(
                        "Email", user.getEmail(),
                        "Name", user.getName()
                )),
                "Subject", "Verify your AgniPress account",
                "HTMLPart", html
        );

        Map<String, Object> payload = Map.of(
                "Messages", List.of(message)
        );

        try {
            webClient
                    .post()
                    .uri(MAILJET_API_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(headers -> headers.setBasicAuth(apiKey, secretKey))
                    .bodyValue(payload)
                    .retrieve()
                    .toBodilessEntity()
                    .block(Duration.ofSeconds(10));

            logger.info(
                    "Verification email sent successfully via Mailjet: recipient={}",
                    user.getEmail()
            );

        } catch (WebClientResponseException e) {
            logger.error(
                    "Mailjet delivery failed: recipient={}, status={}, errorType={}",
                    user.getEmail(),
                    e.getStatusCode().value(),
                    e.getClass().getSimpleName()
            );

            throw new RuntimeException(
                    "Unable to send verification email through the email provider.",
                    e
            );

        } catch (Exception e) {
            logger.error(
                    "Mailjet email error: recipient={}, errorType={}, message={}",
                    user.getEmail(),
                    e.getClass().getSimpleName(),
                    e.getMessage()
            );

            throw new RuntimeException(
                    "Unable to send verification email.",
                    e
            );
        }
    }

    private String normalizeBaseUrl() {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException(
                    "APP_BASE_URL is not configured."
            );
        }

        return baseUrl.trim().replaceAll("/+$", "");
    }

    private void validateConfiguration() {
        if (apiKey == null || apiKey.isBlank()) {
            logger.error("Mailjet configuration missing: MAILJET_API_KEY");
            throw new IllegalStateException("Email provider is not configured.");
        }

        if (secretKey == null || secretKey.isBlank()) {
            logger.error("Mailjet configuration missing: MAILJET_SECRET_KEY");
            throw new IllegalStateException("Email provider secret is not configured.");
        }

        if (fromEmail == null || fromEmail.isBlank()) {
            logger.error("Mailjet configuration missing: MAILJET_FROM_EMAIL");
            throw new IllegalStateException("Email sender is not configured.");
        }
    }

    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }

        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
