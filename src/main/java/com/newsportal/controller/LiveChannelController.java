package com.newsportal.controller;

import com.newsportal.model.LiveChannel;
import com.newsportal.service.LiveChannelService;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/live-channels")
public class LiveChannelController {


    private final LiveChannelService liveChannelService;


    public LiveChannelController(
            LiveChannelService liveChannelService
    ) {

        this.liveChannelService =
                liveChannelService;
    }


    // =====================================================
    // GET LIVE CHANNELS
    // =====================================================

    @GetMapping
    public List<LiveChannel> getLiveChannels() {

        return liveChannelService
                .getLiveChannels();
    }
}