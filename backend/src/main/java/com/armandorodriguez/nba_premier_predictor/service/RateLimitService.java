package com.armandorodriguez.nba_premier_predictor.service;

import java.time.Duration;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.armandorodriguez.nba_premier_predictor.config.RateLimitProperties;
import com.armandorodriguez.nba_premier_predictor.exception.RateLimitExceededException;

@Service
public class RateLimitService {

    private final StringRedisTemplate redisTemplate;
    private final RateLimitProperties properties;

    public RateLimitService(StringRedisTemplate redisTemplate, RateLimitProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    public void checkPredictionLimit(String clientId) {
        check("predictions", clientId, properties.predictionsPerMinute(), properties.window());
    }

    void check(String bucket, String clientId, int limit, Duration window) {
        if (!properties.enabled()) {
            return;
        }

        String key = "rate_limit:" + bucket + ":" + clean(clientId);
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redisTemplate.expire(key, window);
        }
        if (count != null && count > limit) {
            throw new RateLimitExceededException("Prediction rate limit exceeded");
        }
    }

    private static String clean(String clientId) {
        if (clientId == null || clientId.isBlank()) {
            return "unknown";
        }
        return clientId.replaceAll("[^A-Za-z0-9_.:-]", "_");
    }
}
