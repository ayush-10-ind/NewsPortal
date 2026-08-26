package com.newsportal.repository;

import com.newsportal.model.LiveChannelEntity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LiveChannelRepository
        extends JpaRepository<LiveChannelEntity, Long> {

    Optional<LiveChannelEntity> findByChannelId(
            String channelId
    );
}