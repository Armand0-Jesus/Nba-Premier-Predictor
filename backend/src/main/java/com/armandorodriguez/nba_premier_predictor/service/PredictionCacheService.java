package com.armandorodriguez.nba_premier_predictor.service;

import java.lang.reflect.Array;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.armandorodriguez.nba_premier_predictor.dto.PlayerPredictionRequest;
import com.armandorodriguez.nba_premier_predictor.dto.TeamScorePredictionRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class PredictionCacheService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PredictionCacheService.class);
    private static final String KEY_PREFIX = "prediction:fingerprint:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final boolean enabled;
    private final Duration ttl;

    public PredictionCacheService(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            @Value("${app.prediction-cache.enabled:true}") boolean enabled,
            @Value("${app.prediction-cache.ttl:10m}") Duration ttl) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.enabled = enabled;
        this.ttl = ttl == null || ttl.isZero() || ttl.isNegative() ? Duration.ofMinutes(10) : ttl;
    }

    public boolean enabled() {
        return enabled;
    }

    public <T> Optional<T> get(String fingerprint, Class<T> responseType) {
        if (!enabled) {
            return Optional.empty();
        }
        try {
            String json = redisTemplate.opsForValue().get(cacheKey(fingerprint));
            if (json == null) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(json, responseType));
        } catch (JsonProcessingException ex) {
            redisTemplate.delete(cacheKey(fingerprint));
            return Optional.empty();
        } catch (DataAccessException ex) {
            LOGGER.debug("Prediction cache read failed", ex);
            return Optional.empty();
        }
    }

    public void put(String fingerprint, Object response) {
        if (!enabled) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(cacheKey(fingerprint), objectMapper.writeValueAsString(response), ttl);
        } catch (JsonProcessingException | DataAccessException ex) {
            LOGGER.debug("Prediction cache write failed", ex);
        }
    }

    public String playerFingerprint(String predictionType, PlayerPredictionRequest request, String modelVersion) {
        Map<String, Object> payload = new TreeMap<>();
        payload.put("predictionType", predictionType);
        payload.put("gameId", request.gameId());
        payload.put("playerId", request.playerId());
        payload.put("teamId", request.teamId());
        payload.put("dataCutoffTime", request.dataCutoffTime() == null ? null : request.dataCutoffTime().toString());
        payload.put("modelVersion", modelVersion);
        payload.put("features", canonicalize(request.features()));
        return sha256(payload);
    }

    public String gameScoreFingerprint(TeamScorePredictionRequest request, String modelVersion) {
        Map<String, Object> payload = new TreeMap<>();
        payload.put("predictionType", "game_score");
        payload.put("gameId", request.gameId());
        payload.put("homeTeamId", request.homeTeamId());
        payload.put("awayTeamId", request.awayTeamId());
        payload.put("dataCutoffTime", request.dataCutoffTime() == null ? null : request.dataCutoffTime().toString());
        payload.put("modelVersion", modelVersion);
        payload.put("features", canonicalize(request.features()));
        return sha256(payload);
    }

    private static String cacheKey(String fingerprint) {
        return KEY_PREFIX + fingerprint;
    }

    private Object canonicalize(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> sorted = new TreeMap<>();
            map.forEach((key, mapValue) -> sorted.put(String.valueOf(key), canonicalize(mapValue)));
            return sorted;
        }
        if (value instanceof Iterable<?> iterable) {
            List<Object> sortedItems = new ArrayList<>();
            iterable.forEach(item -> sortedItems.add(canonicalize(item)));
            return sortedItems;
        }
        if (value != null && value.getClass().isArray()) {
            List<Object> items = new ArrayList<>();
            for (int index = 0; index < Array.getLength(value); index++) {
                items.add(canonicalize(Array.get(value, index)));
            }
            return items;
        }
        return value;
    }

    private String sha256(Map<String, Object> payload) {
        try {
            byte[] json = objectMapper.writeValueAsString(payload).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json));
        } catch (JsonProcessingException | NoSuchAlgorithmException ex) {
            throw new IllegalStateException("Could not fingerprint prediction request", ex);
        }
    }
}
