package com.newsportal.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class FallbackImageController {

    @GetMapping(
            value = "/images/fallback",
            produces = "image/svg+xml"
    )
    public ResponseEntity<String> fallbackImage(
            @RequestParam(
                    value = "category",
                    defaultValue = "NEWS"
            )
            String category) {

        String safeCategory = escapeXml(category).toUpperCase();

        String svg = """
                <svg xmlns="http://www.w3.org/2000/svg"
                     width="1600"
                     height="900"
                     viewBox="0 0 1600 900">

                    <defs>
                        <linearGradient
                            id="bg"
                            x1="0"
                            y1="0"
                            x2="1"
                            y2="1">

                            <stop
                                offset="0%"
                                stop-color="#11110f"/>

                            <stop
                                offset="100%"
                                stop-color="#292824"/>
                        </linearGradient>

                        <pattern
                            id="grid"
                            width="80"
                            height="80"
                            patternUnits="userSpaceOnUse">

                            <path
                                d="M 80 0 L 0 0 0 80"
                                fill="none"
                                stroke="#ffffff"
                                stroke-opacity=".055"
                                stroke-width="1"/>
                        </pattern>
                    </defs>

                    <rect
                        width="1600"
                        height="900"
                        fill="url(#bg)"/>

                    <rect
                        width="1600"
                        height="900"
                        fill="url(#grid)"/>

                    <line
                        x1="120"
                        y1="135"
                        x2="1480"
                        y2="135"
                        stroke="#b4482f"
                        stroke-width="3"/>

                    <text
                        x="120"
                        y="235"
                        fill="#b4482f"
                        font-family="Arial, Helvetica, sans-serif"
                        font-size="30"
                        font-weight="700"
                        letter-spacing="8">
                        AGNIPRESS
                    </text>

                    <text
                        x="120"
                        y="470"
                        fill="#f5f2eb"
                        font-family="Georgia, Times New Roman, serif"
                        font-size="112"
                        font-weight="400">
                        __CATEGORY__
                    </text>

                    <text
                        x="120"
                        y="535"
                        fill="#aaa69e"
                        font-family="Arial, Helvetica, sans-serif"
                        font-size="22"
                        letter-spacing="5">
                        NEWSROOM
                    </text>

                    <line
                        x1="120"
                        y1="710"
                        x2="1480"
                        y2="710"
                        stroke="#ffffff"
                        stroke-opacity=".15"
                        stroke-width="1"/>

                    <text
                        x="120"
                        y="770"
                        fill="#77736b"
                        font-family="Arial, Helvetica, sans-serif"
                        font-size="18"
                        letter-spacing="3">
                        IMAGE UNAVAILABLE
                    </text>

                </svg>
                """;

        /*
         * IMPORTANT:
         * Do not use String.formatted() here.
         * SVG contains { } characters internally.
         */
        svg = svg.replace(
                "__CATEGORY__",
                safeCategory
        );

        return ResponseEntity
                .ok()
                .contentType(
                        MediaType.parseMediaType(
                                "image/svg+xml"
                        )
                )
                .body(svg);
    }

    private String escapeXml(String value) {

        if (value == null || value.isBlank()) {
            return "NEWS";
        }

        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}