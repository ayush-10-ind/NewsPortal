package com.newsportal.service;

import com.newsportal.entity.EmailVerificationToken;
import com.newsportal.entity.User;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Value("${app.base-url:http://localhost:8082}")
    private String baseUrl;


    // =====================================================
    // CONSTRUCTOR
    // =====================================================

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }


    // =====================================================
    // SEND VERIFICATION EMAIL
    // =====================================================

    public void sendVerificationEmail(
            User user,
            EmailVerificationToken token) {

        String verificationUrl =
                baseUrl
                        + "/verify-email?token="
                        + token.getToken();


        String subject =
                "Verify your AgniPress account";


        String html = """
                <!DOCTYPE html>

                <html>

                <head>
                    <meta charset="UTF-8">
                    <title>Verify your AgniPress account</title>
                </head>

                <body style="
                    margin:0;
                    padding:0;
                    background:#f5f2eb;
                    font-family:Arial,Helvetica,sans-serif;
                    color:#111111;
                ">

                    <div style="
                        max-width:600px;
                        margin:40px auto;
                        background:#ffffff;
                        border:1px solid #ddd8cf;
                    ">

                        <div style="
                            padding:28px 32px;
                            border-bottom:1px solid #ddd8cf;
                        ">

                            <div style="
                                font-size:28px;
                                font-weight:700;
                                letter-spacing:-1px;
                            ">
                                Agni<span style="font-weight:400;">Press</span>
                            </div>

                            <div style="
                                margin-top:6px;
                                font-size:11px;
                                letter-spacing:2px;
                                color:#b83220;
                            ">
                                INDIA. IN THE MOMENT.
                            </div>

                        </div>


                        <div style="padding:40px 32px;">

                            <div style="
                                font-size:11px;
                                font-weight:bold;
                                letter-spacing:2px;
                                color:#b83220;
                                margin-bottom:15px;
                            ">
                                WELCOME TO AGNIPRESS
                            </div>


                            <h1 style="
                                margin:0 0 18px 0;
                                font-family:Georgia,serif;
                                font-size:38px;
                                font-weight:400;
                                line-height:1.15;
                            ">
                                Verify your email.
                            </h1>


                            <p style="
                                font-size:16px;
                                line-height:1.7;
                                color:#555555;
                            ">
                                Hello %s,
                            </p>


                            <p style="
                                font-size:16px;
                                line-height:1.7;
                                color:#555555;
                            ">
                                Thanks for creating your AgniPress account.
                                Please verify your email address to continue
                                and create your password.
                            </p>


                            <div style="margin:32px 0;">

                                <a href="%s"
                                   style="
                                   display:inline-block;
                                   background:#111111;
                                   color:#ffffff;
                                   text-decoration:none;
                                   padding:16px 28px;
                                   font-size:12px;
                                   font-weight:bold;
                                   letter-spacing:1.5px;
                                   ">
                                    VERIFY EMAIL
                                </a>

                            </div>


                            <p style="
                                font-size:13px;
                                line-height:1.6;
                                color:#777777;
                            ">
                                This verification link will expire after
                                24 hours.
                            </p>


                            <p style="
                                font-size:13px;
                                line-height:1.6;
                                color:#777777;
                            ">
                                If you did not create an AgniPress account,
                                you can safely ignore this email.
                            </p>

                        </div>


                        <div style="
                            padding:20px 32px;
                            border-top:1px solid #ddd8cf;
                            font-size:11px;
                            color:#888888;
                        ">
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


        // =====================================================
        // CREATE AND SEND MESSAGE
        // =====================================================

        try {

            MimeMessage message =
                    mailSender.createMimeMessage();


            MimeMessageHelper helper =
                    new MimeMessageHelper(
                            message,
                            true,
                            "UTF-8"
                    );


            helper.setFrom(fromEmail);

            helper.setTo(user.getEmail());

            helper.setSubject(subject);

            helper.setText(
                    html,
                    true
            );


            System.out.println(
                    "================================================="
            );

            System.out.println(
                    "AGNIPRESS VERIFICATION EMAIL"
            );

            System.out.println(
                    "FROM: " + fromEmail
            );

            System.out.println(
                    "TO: " + user.getEmail()
            );

            System.out.println(
                    "VERIFICATION URL: " + verificationUrl
            );

            System.out.println(
                    "================================================="
            );


            mailSender.send(message);


            System.out.println(
                    "VERIFICATION EMAIL SENT SUCCESSFULLY"
            );


        } catch (MessagingException e) {

            System.err.println(
                    "EMAIL MESSAGE CREATION FAILED"
            );

            e.printStackTrace();

            throw new RuntimeException(
                    "Unable to create verification email.",
                    e
            );


        } catch (MailException e) {

            System.err.println(
                    "SMTP EMAIL DELIVERY FAILED"
            );

            e.printStackTrace();

            throw new RuntimeException(
                    "Unable to send verification email. "
                    + "Please check the email configuration.",
                    e
            );


        } catch (Exception e) {

            System.err.println(
                    "UNKNOWN EMAIL ERROR"
            );

            e.printStackTrace();

            throw new RuntimeException(
                    "Unable to send verification email.",
                    e
            );
        }
    }


    // =====================================================
    // HTML ESCAPING
    // =====================================================

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