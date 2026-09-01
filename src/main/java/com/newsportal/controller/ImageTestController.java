package com.newsportal.controller;

import com.newsportal.service.ArticleImageService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ImageTestController {

    private final ArticleImageService articleImageService;

    public ImageTestController(
            ArticleImageService articleImageService) {

        this.articleImageService =
                articleImageService;
    }


    @GetMapping("/api-integration/image-test")
    public ResponseEntity<String> testImage(
            @RequestParam("articleUrl")
            String articleUrl) {

        /*
         * We deliberately pass null as the NewsAPI image.
         *
         * This forces Level 1 to fail so that we can
         * properly test Level 2.
         */
        String imageUrl =
                articleImageService.resolveImage(
                        null,
                        articleUrl,
                        "Technology"
                );


        /*
         * IMPORTANT:
         *
         * Do NOT use .formatted() here.
         *
         * The HTML/CSS contains { } characters which
         * Java's formatted() interprets as placeholders.
         *
         * Instead, we use simple placeholder replacement.
         */
        String html = """
                <!DOCTYPE html>

                <html>

                <head>

                    <meta charset="UTF-8">

                    <title>
                        AgniPress Image Test
                    </title>

                    <style>

                        * {
                            box-sizing: border-box;
                        }

                        body {
                            margin: 0;
                            padding: 40px;
                            background: #11110f;
                            color: #f5f2eb;
                            font-family: Arial, sans-serif;
                        }

                        .container {
                            max-width: 1100px;
                            margin: 0 auto;
                        }

                        .label {
                            color: #b4482f;
                            font-weight: bold;
                            letter-spacing: 3px;
                            font-size: 13px;
                            margin-top: 25px;
                            margin-bottom: 10px;
                        }

                        h1 {
                            font-family: Georgia, serif;
                            font-size: 52px;
                            font-weight: 400;
                            margin: 0 0 30px;
                        }

                        .result {
                            padding: 25px;
                            border: 1px solid #333;
                            background: #191917;
                            margin-top: 25px;
                        }

                        .url {
                            word-break: break-all;
                            color: #aaa69e;
                            line-height: 1.6;
                        }

                        code {
                            color: #d86a4d;
                            word-break: break-all;
                        }

                        .image-wrapper {
                            margin-top: 30px;
                            border: 1px solid #333;
                            background: #191917;
                            overflow: hidden;
                        }

                        img {
                            display: block;
                            width: 100%;
                            max-width: 1100px;
                            height: auto;
                        }

                        .note {
                            margin-top: 20px;
                            color: #77736b;
                            font-size: 14px;
                        }

                    </style>

                </head>


                <body>

                    <div class="container">

                        <div class="label">
                            AGNIPRESS IMAGE SYSTEM
                        </div>

                        <h1>
                            Image Resolution Test
                        </h1>


                        <div class="result">

                            <div class="label">
                                ARTICLE URL
                            </div>

                            <div class="url">
                                __ARTICLE_URL__
                            </div>


                            <div class="label">
                                RESOLVED IMAGE
                            </div>

                            <div class="url">
                                <code>
                                    __IMAGE_URL__
                                </code>
                            </div>

                        </div>


                        <div class="image-wrapper">

                            <img
                                src="__IMAGE_URL_ATTR__"
                                alt="Resolved article image"
                            />

                        </div>


                        <div class="note">
                            Level 1 is intentionally disabled for this test.
                            The image above comes from Level 2 or Level 3.
                        </div>

                    </div>

                </body>

                </html>
                """;


        /*
         * Replace placeholders manually.
         *
         * This avoids String.formatted() interpreting
         * CSS braces as formatting expressions.
         */
        html = html.replace(
                "__ARTICLE_URL__",
                escapeHtml(articleUrl)
        );

        html = html.replace(
                "__IMAGE_URL__",
                escapeHtml(imageUrl)
        );

        html = html.replace(
                "__IMAGE_URL_ATTR__",
                escapeHtml(imageUrl)
        );


        return ResponseEntity
                .ok()
                .header(
                        "Content-Type",
                        "text/html;charset=UTF-8"
                )
                .body(html);
    }


    private String escapeHtml(
            String value) {

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