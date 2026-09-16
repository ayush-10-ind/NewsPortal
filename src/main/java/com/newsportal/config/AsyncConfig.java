package com.newsportal.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
public class AsyncConfig {

    @Bean(name = "newsTaskExecutor")
    public Executor newsTaskExecutor() {

        ThreadPoolTaskExecutor executor =
                new ThreadPoolTaskExecutor();

        // Keep external Ashna calls bounded so an RSS cycle cannot create a
        // large burst of concurrent network requests and memory pressure.
        executor.setCorePoolSize(3);
        executor.setMaxPoolSize(3);
        executor.setQueueCapacity(120);
        executor.setThreadNamePrefix("news-ashna-");

        executor.initialize();

        return executor;
    }
}