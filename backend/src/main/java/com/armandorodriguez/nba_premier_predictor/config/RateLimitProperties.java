package com.armandorodriguez.nba_premier_predictor.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.rate-limit")
public record RateLimitProperties(
        boolean enabled,
        int predictionsPerMinute,
        Duration window,
        boolean trustForwardedHeaders) {

    public RateLimitProperties {
        if (predictionsPerMinute <= 0) {
            predictionsPerMinute = 30;
        }
        if (window == null || window.isZero() || window.isNegative()) {
            window = Duration.ofMinutes(1);
        }
    }
}
