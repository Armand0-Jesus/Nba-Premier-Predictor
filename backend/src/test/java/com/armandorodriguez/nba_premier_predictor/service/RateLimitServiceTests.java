package com.armandorodriguez.nba_premier_predictor.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import com.armandorodriguez.nba_premier_predictor.config.RateLimitProperties;
import com.armandorodriguez.nba_premier_predictor.exception.RateLimitExceededException;

class RateLimitServiceTests {

    @Test
    @SuppressWarnings("unchecked")
    void incrementsRedisKeyAndExpiresNewWindow() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment("rate_limit:predictions:client_1")).thenReturn(1L, 2L);

        RateLimitService rateLimitService = new RateLimitService(
                redisTemplate,
                new RateLimitProperties(true, 2, Duration.ofMinutes(1)));

        rateLimitService.checkPredictionLimit("client 1");
        rateLimitService.checkPredictionLimit("client 1");

        verify(redisTemplate).expire(eq("rate_limit:predictions:client_1"), eq(Duration.ofMinutes(1)));
    }

    @Test
    @SuppressWarnings("unchecked")
    void rejectsRequestsAboveLimit() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment("rate_limit:predictions:client")).thenReturn(3L);

        RateLimitService rateLimitService = new RateLimitService(
                redisTemplate,
                new RateLimitProperties(true, 2, Duration.ofMinutes(1)));

        assertThrows(RateLimitExceededException.class, () -> rateLimitService.checkPredictionLimit("client"));
    }

    @Test
    void disabledLimiterDoesNotCallRedis() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        RateLimitService rateLimitService = new RateLimitService(
                redisTemplate,
                new RateLimitProperties(false, 2, Duration.ofMinutes(1)));

        rateLimitService.checkPredictionLimit("client");

        verify(redisTemplate, never()).opsForValue();
    }
}
