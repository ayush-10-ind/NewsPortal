package com.newsportal.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class LiveNewsController {

    @GetMapping("/live-news")
    public String liveNews() {
        return "live-news";
    }
}