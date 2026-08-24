package com.newsportal.controller;

import java.net.URI;
import java.time.Duration;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;

import io.netty.channel.ChannelOption;
import reactor.netty.http.client.HttpClient;

@RestController
public class ImageProxyController {

    private final WebClient webClient;

    public ImageProxyController() {

        ExchangeStrategies strategies =
                ExchangeStrategies.builder()
                        .codecs(configurer ->
                                configurer
                                        .defaultCodecs()
                                        .maxInMemorySize(20 * 1024 * 1024)
                        )
                        .build();

        HttpClient httpClient =
                HttpClient.create()
                        .option(
                                ChannelOption.CONNECT_TIMEOUT_MILLIS,
                                10000
                        )
                        .responseTimeout(
                                Duration.ofSeconds(20)
                        );

        this.webClient =
                WebClient.builder()
                        .exchangeStrategies(strategies)
                        .clientConnector(
                                new ReactorClientHttpConnector(
                                        httpClient
                                )
                        )
                        .build();
    }


    // =====================================================
    // TEST
    // =====================================================

    @GetMapping("/images/proxy-test")
    public String proxyTest() {
        return "IMAGE PROXY CONTROLLER IS WORKING";
    }


    // =====================================================
    // IMAGE PROXY
    // =====================================================

    @GetMapping("/images/proxy")
    public ResponseEntity<byte[]> proxyImage(
            @RequestParam("url") String url) {

        System.out.println();
        System.out.println("========================================");
        System.out.println("IMAGE PROXY REQUEST");
        System.out.println("========================================");
        System.out.println("URL: " + url);


        if (url == null || url.isBlank()) {

            System.out.println("ERROR: Empty URL");

            return ResponseEntity.badRequest().build();
        }


        if (!url.startsWith("http://")
                && !url.startsWith("https://")) {

            System.out.println("ERROR: Invalid URL");

            return ResponseEntity.badRequest().build();
        }


        try {

            URI imageUri = URI.create(url);

            String host =
                    imageUri.getHost() == null
                            ? ""
                            : imageUri.getHost().toLowerCase();

            System.out.println("HOST: " + host);


            /*
             * Build request.
             */
            WebClient.RequestHeadersSpec<?> request =
                    webClient
                            .get()
                            .uri(imageUri)

                            .header(
                                    HttpHeaders.USER_AGENT,
                                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                                    + "AppleWebKit/537.36 "
                                    + "(KHTML, like Gecko) "
                                    + "Chrome/151.0.0.0 "
                                    + "Safari/537.36"
                            )

                            .header(
                                    HttpHeaders.ACCEPT,
                                    "image/avif,image/webp,image/apng,"
                                    + "image/svg+xml,image/*,*/*;q=0.8"
                            )

                            .header(
                                    HttpHeaders.ACCEPT_LANGUAGE,
                                    "en-US,en;q=0.9"
                            )

                            .header(
                                    HttpHeaders.CACHE_CONTROL,
                                    "no-cache"
                            );


            /*
             * Give the remote server a reasonable Referer.
             */
            if (!host.isBlank()) {

                request =
                        request.header(
                                HttpHeaders.REFERER,
                                "https://" + host + "/"
                        );
            }


            /*
             * IMPORTANT:
             *
             * exchangeToMono lets us inspect 403/404/429
             * instead of WebClient throwing immediately.
             */
            ResponseEntity<byte[]> response =
                    request
                            .exchangeToMono(clientResponse -> {

                                HttpStatusCode status =
                                        clientResponse.statusCode();

                                System.out.println(
                                        "REMOTE STATUS: " + status
                                );

                                MediaType contentType =
                                        clientResponse
                                                .headers()
                                                .contentType()
                                                .orElse(null);

                                System.out.println(
                                        "REMOTE CONTENT TYPE: "
                                                + contentType
                                );


                                return clientResponse
                                        .bodyToMono(byte[].class)
                                        .map(body ->
                                                ResponseEntity
                                                        .status(status)
                                                        .headers(
                                                                headers -> {

                                                                    if (contentType != null) {

                                                                        headers.setContentType(
                                                                                contentType
                                                                        );
                                                                    }
                                                                }
                                                        )
                                                        .body(body)
                                        );
                            })
                            .block();


            if (response == null) {

                System.out.println(
                        "ERROR: No response from remote server"
                );

                return ResponseEntity
                        .status(502)
                        .build();
            }


            /*
             * Remote server rejected request.
             */
            if (!response.getStatusCode()
                    .is2xxSuccessful()) {

                System.out.println(
                        "REMOTE SERVER REJECTED IMAGE"
                );

                System.out.println(
                        "STATUS: "
                                + response.getStatusCode()
                );

                return ResponseEntity
                        .status(502)
                        .build();
            }


            byte[] image =
                    response.getBody();


            if (image == null || image.length == 0) {

                System.out.println(
                        "ERROR: Empty image"
                );

                return ResponseEntity
                        .status(502)
                        .build();
            }


            /*
             * Determine content type.
             */
            MediaType contentType =
                    response
                            .getHeaders()
                            .getContentType();


            if (contentType == null) {

                contentType =
                        detectImageType(image);
            }


            /*
             * Sometimes servers incorrectly return
             * text/html for an actual image.
             */
            if (!isImage(contentType)) {

                contentType =
                        detectImageType(image);
            }


            System.out.println(
                    "IMAGE SIZE: "
                            + image.length
                            + " bytes"
            );

            System.out.println(
                    "FINAL CONTENT TYPE: "
                            + contentType
            );

            System.out.println(
                    "IMAGE PROXY SUCCESS"
            );

            System.out.println(
                    "========================================"
            );


            return ResponseEntity
                    .ok()
                    .header(
                            HttpHeaders.CACHE_CONTROL,
                            "public, max-age=86400"
                    )
                    .contentType(contentType)
                    .body(image);


        } catch (Exception e) {

            System.out.println();
            System.out.println(
                    "========================================"
            );

            System.out.println(
                    "IMAGE PROXY ERROR"
            );

            System.out.println(
                    "TYPE: "
                            + e.getClass().getName()
            );

            System.out.println(
                    "MESSAGE: "
                            + e.getMessage()
            );

            System.out.println(
                    "========================================"
            );

            e.printStackTrace();

            return ResponseEntity
                    .status(502)
                    .build();
        }
    }


    // =====================================================
    // IMAGE TYPE DETECTION
    // =====================================================

    private boolean isImage(
            MediaType mediaType) {

        return mediaType != null
                && "image".equalsIgnoreCase(
                        mediaType.getType()
                );
    }


    private MediaType detectImageType(
            byte[] data) {

        /*
         * JPEG
         */
        if (data.length >= 3
                && (data[0] & 0xFF) == 0xFF
                && (data[1] & 0xFF) == 0xD8
                && (data[2] & 0xFF) == 0xFF) {

            return MediaType.IMAGE_JPEG;
        }


        /*
         * PNG
         */
        if (data.length >= 8
                && (data[0] & 0xFF) == 0x89
                && data[1] == 0x50
                && data[2] == 0x4E
                && data[3] == 0x47
                && data[4] == 0x0D
                && data[5] == 0x0A
                && data[6] == 0x1A
                && data[7] == 0x0A) {

            return MediaType.IMAGE_PNG;
        }


        /*
         * GIF
         */
        if (data.length >= 6
                && data[0] == 'G'
                && data[1] == 'I'
                && data[2] == 'F') {

            return MediaType.IMAGE_GIF;
        }


        /*
         * WEBP
         */
        if (data.length >= 12
                && data[0] == 'R'
                && data[1] == 'I'
                && data[2] == 'F'
                && data[3] == 'F'
                && data[8] == 'W'
                && data[9] == 'E'
                && data[10] == 'B'
                && data[11] == 'P') {

            return MediaType.parseMediaType(
                    "image/webp"
            );
        }


        /*
         * AVIF
         */
        if (data.length >= 12) {

            String header =
                    new String(
                            data,
                            4,
                            Math.min(8, data.length - 4)
                    );

            if (header.contains("ftyp")) {

                return MediaType.parseMediaType(
                        "image/avif"
                );
            }
        }


        /*
         * Last fallback.
         */
        return MediaType.IMAGE_JPEG;
    }
}