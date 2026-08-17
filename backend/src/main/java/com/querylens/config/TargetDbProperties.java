package com.querylens.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "querylens.target-db")
public record TargetDbProperties(
        String url,
        String username,
        String password
) {
}
