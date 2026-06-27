package com.armandorodriguez.nba_premier_predictor.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;

@Service
public class ModelMetadataService {

    private static final String DEFAULT_PLAYER_MODEL_VERSION = "player-baseline-v1";
    private static final String DEFAULT_GAME_SCORE_MODEL_VERSION = "game-score-baseline-v1";

    private final MlPredictionClient mlPredictionClient;

    public ModelMetadataService(MlPredictionClient mlPredictionClient) {
        this.mlPredictionClient = mlPredictionClient;
    }

    @Cacheable(cacheNames = "modelMetrics", key = "'latest'")
    public Map<String, Object> metrics() {
        return normalizeMap(mlPredictionClient.modelMetrics());
    }

    @Cacheable(cacheNames = "modelVersions", key = "'latest'")
    public Map<String, Object> versions() {
        return normalizeMap(mlPredictionClient.modelVersions());
    }

    @CacheEvict(cacheNames = "modelMetrics", key = "'latest'", beforeInvocation = true)
    public Map<String, Object> evaluate() {
        return normalizeMap(mlPredictionClient.evaluateModels());
    }

    public static String playerModelVersion(Map<String, Object> versions) {
        return versionFrom(versions, "activeModel", "modelVersion", DEFAULT_PLAYER_MODEL_VERSION);
    }

    public static String gameScoreModelVersion(Map<String, Object> versions) {
        return versionFrom(versions, "gameScoreModel", "gameScoreModelVersion", DEFAULT_GAME_SCORE_MODEL_VERSION);
    }

    private static String versionFrom(
            Map<String, Object> versions,
            String nestedModelKey,
            String flatVersionKey,
            String fallback) {
        if (versions == null) {
            return fallback;
        }
        Object nestedModel = versions.get(nestedModelKey);
        if (nestedModel instanceof Map<?, ?> model) {
            Object versionName = model.get("versionName");
            if (versionName instanceof String value && !value.isBlank()) {
                return value;
            }
        }
        Object flatVersion = versions.get(flatVersionKey);
        if (flatVersion instanceof String value && !value.isBlank()) {
            return value;
        }
        return fallback;
    }

    private static Map<String, Object> normalizeMap(Map<String, Object> source) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        if (source == null) {
            return normalized;
        }
        source.forEach((key, value) -> normalized.put(key, normalize(value)));
        return normalized;
    }

    private static Object normalize(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> normalized = new LinkedHashMap<>();
            map.forEach((key, mapValue) -> normalized.put(String.valueOf(key), normalize(mapValue)));
            return normalized;
        }
        if (value instanceof Iterable<?> iterable) {
            List<Object> normalized = new ArrayList<>();
            iterable.forEach(item -> normalized.add(normalize(item)));
            return normalized;
        }
        return value;
    }
}
