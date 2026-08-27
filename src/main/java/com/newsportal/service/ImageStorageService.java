package com.newsportal.service;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.UUID;

@Service
public class ImageStorageService {

    private final WebClient webClient;

    private final Path uploadDirectory;


    // =====================================================
    // CONSTRUCTOR
    // =====================================================

    public ImageStorageService() {

        this.webClient =
                WebClient.builder()
                        .codecs(configurer ->
                                configurer
                                        .defaultCodecs()
                                        .maxInMemorySize(
                                                15 * 1024 * 1024
                                        )
                        )
                        .build();


        // =================================================
        // UPLOAD DIRECTORY
        // =================================================
        //
        // Railway:
        //   UPLOAD_DIR=/app/uploads
        //
        // Local development:
        //   uploads/
        //
        // News images:
        //   <UPLOAD_DIR>/news/
        //
        // =================================================

        String uploadRoot =
                System.getenv("UPLOAD_DIR");

        if (uploadRoot == null ||
                uploadRoot.isBlank()) {

            uploadRoot = "uploads";
        }


        this.uploadDirectory =
                Paths.get(
                        uploadRoot,
                        "news"
                );


        System.out.println(
                "========================================"
        );

        System.out.println(
                "IMAGE STORAGE INITIALIZED"
        );

        System.out.println(
                "UPLOAD ROOT: "
                        + uploadRoot
        );

        System.out.println(
                "NEWS IMAGE DIRECTORY: "
                        + uploadDirectory
                                .toAbsolutePath()
        );

        System.out.println(
                "========================================"
        );
    }


    // =====================================================
    // DOWNLOAD + SAVE IMAGE
    // =====================================================

    public String downloadAndSaveImage(
            String imageUrl) {

        if (imageUrl == null ||
                imageUrl.isBlank()) {

            return null;
        }


        if (!imageUrl.startsWith("http://") &&
                !imageUrl.startsWith("https://")) {

            System.out.println(
                    "Invalid image URL."
            );

            return null;
        }


        try {

            // =============================================
            // CREATE UPLOAD DIRECTORY
            // =============================================

            Files.createDirectories(
                    uploadDirectory
            );


            URI imageUri =
                    URI.create(imageUrl);


            System.out.println();
            System.out.println(
                    "----------------------------------------"
            );

            System.out.println(
                    "DOWNLOADING IMAGE"
            );

            System.out.println(
                    "URL: "
                            + imageUrl
            );

            System.out.println(
                    "DESTINATION: "
                            + uploadDirectory
                                    .toAbsolutePath()
            );


            // =============================================
            // DOWNLOAD IMAGE
            // =============================================

            ImageDownloadResult result =

                    webClient
                            .get()
                            .uri(imageUri)

                            .header(
                                    HttpHeaders.USER_AGENT,
                                    "Mozilla/5.0 " +
                                    "(Windows NT 10.0; Win64; x64) " +
                                    "AppleWebKit/537.36 " +
                                    "(KHTML, like Gecko) " +
                                    "Chrome/151.0.0.0 " +
                                    "Safari/537.36"
                            )

                            .header(
                                    HttpHeaders.ACCEPT,
                                    "image/avif," +
                                    "image/webp," +
                                    "image/apng," +
                                    "image/svg+xml," +
                                    "image/jpeg," +
                                    "image/png," +
                                    "image/gif," +
                                    "image/*,*/*;q=0.8"
                            )

                            .exchangeToMono(
                                    response -> {

                                        System.out.println(
                                                "Remote status: "
                                                        + response
                                                        .statusCode()
                                        );


                                        if (!response
                                                .statusCode()
                                                .is2xxSuccessful()) {

                                            System.out.println(
                                                    "IMAGE REJECTED: HTTP "
                                                            + response
                                                            .statusCode()
                                            );

                                            return response
                                                    .releaseBody()
                                                    .thenReturn(null);
                                        }


                                        MediaType contentType =
                                                response
                                                        .headers()
                                                        .contentType()
                                                        .orElse(null);


                                        System.out.println(
                                                "Remote content-type: "
                                                        + contentType
                                        );


                                        if (contentType == null ||
                                                !contentType
                                                        .getType()
                                                        .equalsIgnoreCase(
                                                                "image"
                                                        )) {

                                            System.out.println(
                                                    "IMAGE REJECTED: "
                                                            + "response is not "
                                                            + "an image."
                                            );

                                            return response
                                                    .releaseBody()
                                                    .thenReturn(null);
                                        }


                                        return response
                                                .bodyToMono(byte[].class)

                                                .map(bytes ->
                                                        new ImageDownloadResult(
                                                                bytes,
                                                                contentType
                                                        )
                                                );
                                    }
                            )

                            .timeout(
                                    Duration.ofSeconds(10)
                            )

                            .block();


            // =============================================
            // DOWNLOAD FAILED
            // =============================================

            if (result == null ||
                    result.bytes() == null ||
                    result.bytes().length == 0) {

                System.out.println(
                        "IMAGE DOWNLOAD FAILED"
                );

                System.out.println(
                        "----------------------------------------"
                );

                return null;
            }


            // =============================================
            // CHECK IMAGE SIZE
            // =============================================

            if (result.bytes().length >
                    15 * 1024 * 1024) {

                System.out.println(
                        "IMAGE REJECTED: "
                                + "image exceeds 15 MB."
                );

                return null;
            }


            // =============================================
            // FILE EXTENSION
            // =============================================

            String extension =
                    getExtension(
                            result.contentType()
                    );


            // =============================================
            // UNIQUE FILE NAME
            // =============================================

            String filename =
                    UUID.randomUUID()
                            .toString()
                            + extension;


            Path destination =
                    uploadDirectory.resolve(
                            filename
                    );


            // =============================================
            // SAVE FILE
            // =============================================

            Files.write(
                    destination,
                    result.bytes()
            );


            // =============================================
            // VERIFY FILE REALLY EXISTS
            // =============================================

            if (!Files.exists(destination)) {

                System.out.println(
                        "ERROR: Image was not found "
                                + "after saving."
                );

                return null;
            }


            // =============================================
            // SUCCESS
            // =============================================

            System.out.println(
                    "IMAGE SAVED SUCCESSFULLY"
            );

            System.out.println(
                    "File: "
                            + destination
                                    .toAbsolutePath()
            );

            System.out.println(
                    "Exists: "
                            + Files.exists(destination)
            );

            System.out.println(
                    "Size: "
                            + result.bytes().length
                            + " bytes"
            );

            System.out.println(
                    "----------------------------------------"
            );


            // =============================================
            // DATABASE VALUE
            // =============================================

            return "/uploads/news/" + filename;


        } catch (Exception e) {

            System.out.println();
            System.out.println(
                    "========================================"
            );

            System.out.println(
                    "IMAGE STORAGE FAILED"
            );

            System.out.println(
                    "URL: "
                            + imageUrl
            );

            System.out.println(
                    "UPLOAD DIRECTORY: "
                            + uploadDirectory
                                    .toAbsolutePath()
            );

            System.out.println(
                    "Error: "
                            + e.getMessage()
            );

            System.out.println(
                    "========================================"
            );

            e.printStackTrace();

            return null;
        }
    }


    // =====================================================
    // DELETE LOCAL IMAGE
    // =====================================================

    public void deleteLocalImage(
            String imageUrl) {

        if (imageUrl == null ||
                imageUrl.isBlank()) {

            return;
        }


        if (!imageUrl.startsWith(
                "/uploads/news/"
        )) {

            System.out.println(
                    "Skipping image deletion - "
                            + "not a local news image: "
                            + imageUrl
            );

            return;
        }


        try {

            String filename =
                    imageUrl.substring(
                            "/uploads/news/".length()
                    );


            // =============================================
            // PATH TRAVERSAL PROTECTION
            // =============================================

            if (filename.contains("..") ||
                    filename.contains("/") ||
                    filename.contains("\\")) {

                System.out.println(
                        "Unsafe image path rejected: "
                                + imageUrl
                );

                return;
            }


            Path imagePath =
                    uploadDirectory.resolve(
                            filename
                    );


            boolean deleted =
                    Files.deleteIfExists(
                            imagePath
                    );


            if (deleted) {

                System.out.println(
                        "Deleted old image: "
                                + imagePath
                                        .toAbsolutePath()
                );

            } else {

                System.out.println(
                        "Image already missing: "
                                + imagePath
                                        .toAbsolutePath()
                );
            }


        } catch (Exception e) {

            System.out.println(
                    "Failed to delete image: "
                            + imageUrl
            );

            System.out.println(
                    "Error: "
                            + e.getMessage()
            );
        }
    }


    // =====================================================
    // CONTENT TYPE → EXTENSION
    // =====================================================

    private String getExtension(
            MediaType contentType) {

        if (contentType == null) {

            return ".img";
        }


        String type =
                contentType
                        .toString()
                        .toLowerCase();


        if (type.contains("image/jpeg")) {

            return ".jpg";
        }


        if (type.contains("image/png")) {

            return ".png";
        }


        if (type.contains("image/webp")) {

            return ".webp";
        }


        if (type.contains("image/gif")) {

            return ".gif";
        }


        if (type.contains("image/avif")) {

            return ".avif";
        }


        if (type.contains("image/svg+xml")) {

            return ".svg";
        }


        return ".img";
    }


    // =====================================================
    // IMAGE DOWNLOAD RESULT
    // =====================================================

    private record ImageDownloadResult(
            byte[] bytes,
            MediaType contentType) {
    }
}