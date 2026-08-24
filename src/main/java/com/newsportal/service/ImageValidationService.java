package com.newsportal.service;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.URI;
import java.time.Duration;

@Service
public class ImageValidationService {

    private final WebClient webClient;

    public ImageValidationService() {

        this.webClient = WebClient.builder()
                .build();
    }


    // =====================================================
    // VALIDATE IMAGE URL
    // =====================================================

    public boolean isValidImage(String imageUrl) {

        if (imageUrl == null ||
                imageUrl.isBlank()) {

            return false;
        }


        if (!imageUrl.startsWith("http://") &&
                !imageUrl.startsWith("https://")) {

            return false;
        }


        try {

            URI uri = URI.create(imageUrl);

            System.out.println();
            System.out.println(
                    "Checking image: " + imageUrl
            );


            return webClient
                    .get()
                    .uri(uri)

                    // -----------------------------------------
                    // Browser-like headers
                    // -----------------------------------------

                    .header(
                            HttpHeaders.USER_AGENT,
                            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                            "AppleWebKit/537.36 " +
                            "Chrome/151.0.0.0 " +
                            "Safari/537.36"
                    )

                    .header(
                            HttpHeaders.ACCEPT,
                            "image/avif,image/webp,image/apng," +
                            "image/svg+xml,image/*,*/*;q=0.8"
                    )

                    // -----------------------------------------
                    // Don't download the entire image.
                    // We only inspect the response headers.
                    // -----------------------------------------

                    .exchangeToMono(response -> {

                        HttpStatusCode status =
                                response.statusCode();

                        String contentType =
                                response.headers()
                                        .contentType()
                                        .map(Object::toString)
                                        .orElse("UNKNOWN");


                        System.out.println(
                                "Image status: " + status
                        );

                        System.out.println(
                                "Image content-type: "
                                        + contentType
                        );


                        // -------------------------------------
                        // HTTP must be successful
                        // -------------------------------------

                        if (!status.is2xxSuccessful()) {

                            System.out.println(
                                    "IMAGE REJECTED: HTTP "
                                            + status.value()
                            );

                            return response
                                    .releaseBody()
                                    .thenReturn(false);
                        }


                        // -------------------------------------
                        // Must actually be an image
                        // -------------------------------------

                        if (!contentType
                                .toLowerCase()
                                .startsWith("image/")) {

                            System.out.println(
                                    "IMAGE REJECTED: Not an image"
                            );

                            return response
                                    .releaseBody()
                                    .thenReturn(false);
                        }


                        System.out.println(
                                "IMAGE ACCEPTED"
                        );


                        return response
                                .releaseBody()
                                .thenReturn(true);
                    })

                    // -----------------------------------------
                    // Prevent one slow server from hanging
                    // the entire news import.
                    // -----------------------------------------

                    .timeout(
                            Duration.ofSeconds(8)
                    )

                    .onErrorReturn(false)

                    .blockOptional()
                    .orElse(false);


        } catch (Exception e) {

            System.out.println(
                    "IMAGE VALIDATION FAILED: "
                            + e.getMessage()
            );

            return false;
        }
    }
}