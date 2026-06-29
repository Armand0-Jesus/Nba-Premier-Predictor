package com.armandorodriguez.nba_premier_predictor.service;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.armandorodriguez.nba_premier_predictor.dto.ModelRetrainRequest;
import com.armandorodriguez.nba_premier_predictor.exception.ResourceNotFoundException;

@Service
public class ModelMetadataService {

    private static final String DEFAULT_PLAYER_MODEL_VERSION = "player-baseline-v2";
    private static final String DEFAULT_GAME_SCORE_MODEL_VERSION = "game-score-baseline-v2";

    private final MlPredictionClient mlPredictionClient;
    private final JdbcTemplate jdbcTemplate;

    public ModelMetadataService(MlPredictionClient mlPredictionClient, JdbcTemplate jdbcTemplate) {
        this.mlPredictionClient = mlPredictionClient;
        this.jdbcTemplate = jdbcTemplate;
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

    public List<Map<String, Object>> trainingRuns() {
        return jdbcTemplate.query("""
                select id, started_at, finished_at, status, training_data_range,
                       validation_data_range, triggered_by, notes
                from model_training_runs
                order by started_at desc, id desc
                limit 25
                """, (rs, rowNum) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", rs.getLong("id"));
            row.put("startedAt", rs.getTimestamp("started_at").toInstant().toString());
            row.put("finishedAt", rs.getTimestamp("finished_at") == null ? null : rs.getTimestamp("finished_at").toInstant().toString());
            row.put("status", rs.getString("status"));
            row.put("trainingDataRange", rs.getString("training_data_range"));
            row.put("validationDataRange", rs.getString("validation_data_range"));
            row.put("triggeredBy", rs.getString("triggered_by"));
            row.put("notes", rs.getString("notes"));
            return row;
        });
    }

    public List<Map<String, Object>> activeVersions() {
        return jdbcTemplate.query("""
                select id, version_name, model_type, target_variable, trained_at,
                       artifact_path, status, is_active, created_at
                from model_versions
                where is_active = true
                order by target_variable, id desc
                """, (rs, rowNum) -> modelVersionRow(rs));
    }

    public List<Map<String, Object>> promotionHistory() {
        return jdbcTemplate.query("""
                select id, previous_model_version_id, candidate_model_version_id,
                       promoted, reason, previous_mae, candidate_mae, promoted_at
                from model_promotion_history
                order by promoted_at desc, id desc
                limit 25
                """, (rs, rowNum) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", rs.getLong("id"));
            row.put("previousModelVersionId", nullableLong(rs, "previous_model_version_id"));
            row.put("candidateModelVersionId", nullableLong(rs, "candidate_model_version_id"));
            row.put("promoted", rs.getBoolean("promoted"));
            row.put("reason", rs.getString("reason"));
            row.put("previousMae", nullableDouble(rs, "previous_mae"));
            row.put("candidateMae", nullableDouble(rs, "candidate_mae"));
            row.put("promotedAt", rs.getTimestamp("promoted_at").toInstant().toString());
            return row;
        });
    }

    @CacheEvict(cacheNames = {"modelMetrics", "modelVersions"}, allEntries = true, beforeInvocation = true)
    public Map<String, Object> retrain(ModelRetrainRequest rawRequest) {
        ModelRetrainRequest request = rawRequest == null
                ? new ModelRetrainRequest(null, null, null, null, null, null)
                : rawRequest;
        validateRetrainRequest(request);

        Long runId = createTrainingRun(request);
        try {
            String suffix = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
                    .withZone(ZoneOffset.UTC)
                    .format(Instant.now());
            Map<String, Object> playerTraining = normalizeMap(mlPredictionClient.trainPlayerModel(
                    request,
                    DEFAULT_PLAYER_MODEL_VERSION + "-" + suffix,
                    false));
            Map<String, Object> gameScoreTraining = normalizeMap(mlPredictionClient.trainGameScoreModel(
                    request,
                    DEFAULT_GAME_SCORE_MODEL_VERSION + "-" + suffix,
                    false));
            Map<String, Object> evaluation = normalizeMap(mlPredictionClient.evaluateModels(request));

            Map<String, Object> playerCandidate = registerCandidate(
                    "player",
                    "player_stat_fantasy",
                    playerTraining,
                    nestedMetrics(evaluation, "playerBaseline"));
            Map<String, Object> gameScoreCandidate = registerCandidate(
                    "gameScore",
                    "game_score",
                    gameScoreTraining,
                    nestedMetrics(evaluation, "gameScoreBaseline"));

            finishTrainingRun(runId, "completed", "Candidate models trained and evaluated");

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("trainingRunId", runId);
            response.put("status", "completed");
            response.put("playerCandidate", playerCandidate);
            response.put("gameScoreCandidate", gameScoreCandidate);
            response.put("activeVersions", activeVersions());
            return response;
        } catch (RuntimeException ex) {
            finishTrainingRun(runId, "failed", ex.getMessage());
            throw ex;
        }
    }

    @Transactional
    @CacheEvict(cacheNames = {"modelMetrics", "modelVersions"}, allEntries = true, beforeInvocation = true)
    public Map<String, Object> promote(Long modelVersionId) {
        return activateModel(modelVersionId, "manual promotion");
    }

    @Transactional
    @CacheEvict(cacheNames = {"modelMetrics", "modelVersions"}, allEntries = true, beforeInvocation = true)
    public Map<String, Object> rollback(Long modelVersionId) {
        return activateModel(modelVersionId, "rollback");
    }

    public static String playerModelVersion(Map<String, Object> versions) {
        return versionFrom(versions, "activeModel", "modelVersion", DEFAULT_PLAYER_MODEL_VERSION);
    }

    public static String gameScoreModelVersion(Map<String, Object> versions) {
        return versionFrom(versions, "gameScoreModel", "gameScoreModelVersion", DEFAULT_GAME_SCORE_MODEL_VERSION);
    }

    private Map<String, Object> registerCandidate(
            String mlModelType,
            String targetVariable,
            Map<String, Object> training,
            Map<String, Object> metrics) {
        String versionName = stringValue(training, "model_version", "modelVersion");
        if (versionName == null || versionName.isBlank()) {
            versionName = targetVariable + "-" + Instant.now().toEpochMilli();
        }
        String artifactPath = stringValue(training, "artifact_path", "artifactPath");
        Long modelVersionId = upsertModelVersion(versionName, "ridge-regression", targetVariable, artifactPath, "candidate", false);
        saveMetrics(modelVersionId, metrics);
        upsertRegistry(modelVersionId, "candidate");

        Long previousActiveId = activeModelVersionId(targetVariable);
        Double previousMae = previousActiveId == null ? null : averageStoredMae(previousActiveId);
        Double candidateMae = averageMae(metrics);
        boolean promoted = previousMae == null || (candidateMae != null && candidateMae < previousMae);
        String reason = promoted
                ? "Candidate improved average MAE"
                : "Candidate did not improve average MAE";
        if (promoted) {
            activateModel(modelVersionId, mlModelType, artifactPath, reason, previousActiveId, previousMae, candidateMae);
        } else {
            insertPromotionHistory(previousActiveId, modelVersionId, false, reason, previousMae, candidateMae);
        }

        Map<String, Object> candidate = new LinkedHashMap<>();
        candidate.put("modelVersionId", modelVersionId);
        candidate.put("versionName", versionName);
        candidate.put("targetVariable", targetVariable);
        candidate.put("artifactPath", artifactPath);
        candidate.put("trainedRows", intValue(training, "trained_rows", "trainedRows"));
        candidate.put("averageMae", candidateMae);
        candidate.put("promoted", promoted);
        candidate.put("reason", reason);
        return candidate;
    }

    private Map<String, Object> activateModel(Long modelVersionId, String reason) {
        Map<String, Object> model = findModelVersion(modelVersionId);
        String artifactPath = (String) model.get("artifactPath");
        String mlModelType = "game_score".equals(model.get("targetVariable")) ? "gameScore" : "player";
        Long previousActiveId = activeModelVersionId((String) model.get("targetVariable"));
        Double previousMae = previousActiveId == null ? null : averageStoredMae(previousActiveId);
        Double candidateMae = averageStoredMae(modelVersionId);
        activateModel(modelVersionId, mlModelType, artifactPath, reason, previousActiveId, previousMae, candidateMae);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("promoted", true);
        response.put("modelVersion", findModelVersion(modelVersionId));
        response.put("activeVersions", activeVersions());
        return response;
    }

    private void activateModel(
            Long modelVersionId,
            String mlModelType,
            String artifactPath,
            String reason,
            Long previousActiveId,
            Double previousMae,
            Double candidateMae) {
        if (artifactPath != null && !artifactPath.isBlank()) {
            mlPredictionClient.promoteModel(mlModelType, artifactPath);
        }
        String targetVariable = jdbcTemplate.queryForObject(
                "select target_variable from model_versions where id = ?",
                String.class,
                modelVersionId);
        jdbcTemplate.update(
                "update model_versions set is_active = false, status = 'archived' where target_variable = ? and id <> ?",
                targetVariable,
                modelVersionId);
        jdbcTemplate.update(
                "update model_versions set is_active = true, status = 'active' where id = ?",
                modelVersionId);
        upsertRegistry(modelVersionId, "active");
        insertPromotionHistory(previousActiveId, modelVersionId, true, reason, previousMae, candidateMae);
    }

    private Long upsertModelVersion(
            String versionName,
            String modelType,
            String targetVariable,
            String artifactPath,
            String status,
            boolean active) {
        List<Long> existing = jdbcTemplate.queryForList(
                "select id from model_versions where version_name = ?",
                Long.class,
                versionName);
        if (!existing.isEmpty()) {
            Long id = existing.get(0);
            jdbcTemplate.update("""
                    update model_versions
                    set model_type = ?, target_variable = ?, trained_at = ?,
                        artifact_path = ?, status = ?, is_active = ?
                    where id = ?
                    """,
                    modelType,
                    targetVariable,
                    Timestamp.from(Instant.now()),
                    artifactPath,
                    status,
                    active,
                    id);
            return id;
        }

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    insert into model_versions (
                        version_name, model_type, target_variable, trained_at,
                        artifact_path, status, is_active
                    ) values (?, ?, ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, versionName);
            ps.setString(2, modelType);
            ps.setString(3, targetVariable);
            ps.setTimestamp(4, Timestamp.from(Instant.now()));
            ps.setString(5, artifactPath);
            ps.setString(6, status);
            ps.setBoolean(7, active);
            return ps;
        }, keyHolder);
        return generatedId(keyHolder);
    }

    private void saveMetrics(Long modelVersionId, Map<String, Object> metrics) {
        jdbcTemplate.update("delete from model_metrics where model_version_id = ?", modelVersionId);
        metrics.forEach((target, values) -> {
            if (!(values instanceof Map<?, ?> rawValues)) {
                return;
            }
            Map<String, Object> targetMetrics = normalizeMap(rawValues);
            jdbcTemplate.update("""
                    insert into model_metrics (
                        model_version_id, target_variable, mae, rmse, r2_score, validation_sample_size
                    ) values (?, ?, ?, ?, ?, ?)
                    """,
                    modelVersionId,
                    target,
                    doubleValue(targetMetrics, "mae"),
                    doubleValue(targetMetrics, "rmse"),
                    doubleValue(targetMetrics, "r2_score", "r2Score"),
                    intValue(targetMetrics, "sample_size", "sampleSize"));
        });
    }

    private Long createTrainingRun(ModelRetrainRequest request) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    insert into model_training_runs (
                        status, training_data_range, validation_data_range, triggered_by, notes
                    ) values (?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, "running");
            ps.setString(2, trainingRange(request));
            ps.setString(3, "time-based validation split");
            ps.setString(4, request.normalizedTriggeredBy());
            ps.setString(5, "Training candidate models");
            return ps;
        }, keyHolder);
        return generatedId(keyHolder);
    }

    private void finishTrainingRun(Long runId, String status, String notes) {
        jdbcTemplate.update("""
                update model_training_runs
                set finished_at = ?, status = ?, notes = ?
                where id = ?
                """, Timestamp.from(Instant.now()), status, notes, runId);
    }

    private Map<String, Object> findModelVersion(Long modelVersionId) {
        List<Map<String, Object>> rows = jdbcTemplate.query("""
                select id, version_name, model_type, target_variable, trained_at,
                       artifact_path, status, is_active, created_at
                from model_versions
                where id = ?
                """, (rs, rowNum) -> modelVersionRow(rs), modelVersionId);
        if (rows.isEmpty()) {
            throw new ResourceNotFoundException("Model version not found: " + modelVersionId);
        }
        return rows.get(0);
    }

    private Long activeModelVersionId(String targetVariable) {
        List<Long> rows = jdbcTemplate.queryForList("""
                select id
                from model_versions
                where target_variable = ? and is_active = true
                order by id desc
                limit 1
                """, Long.class, targetVariable);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private Double averageStoredMae(Long modelVersionId) {
        return jdbcTemplate.queryForObject("""
                select avg(mae)
                from model_metrics
                where model_version_id = ? and mae is not null
                """, Double.class, modelVersionId);
    }

    private void upsertRegistry(Long modelVersionId, String status) {
        List<Long> existing = jdbcTemplate.queryForList(
                "select id from model_registry where model_version_id = ?",
                Long.class,
                modelVersionId);
        if (existing.isEmpty()) {
            jdbcTemplate.update(
                    "insert into model_registry (model_version_id, registry_status) values (?, ?)",
                    modelVersionId,
                    status);
        } else {
            jdbcTemplate.update(
                    "update model_registry set registry_status = ?, archived_at = null where model_version_id = ?",
                    status,
                    modelVersionId);
        }
    }

    private void insertPromotionHistory(
            Long previousModelVersionId,
            Long candidateModelVersionId,
            boolean promoted,
            String reason,
            Double previousMae,
            Double candidateMae) {
        jdbcTemplate.update("""
                insert into model_promotion_history (
                    previous_model_version_id, candidate_model_version_id, promoted,
                    reason, previous_mae, candidate_mae
                ) values (?, ?, ?, ?, ?, ?)
                """,
                previousModelVersionId,
                candidateModelVersionId,
                promoted,
                reason,
                previousMae,
                candidateMae);
    }

    private Map<String, Object> nestedMetrics(Map<String, Object> metricsResponse, String key) {
        Object section = metricsResponse.get(key);
        if (!(section instanceof Map<?, ?> rawSection)) {
            return Map.of();
        }
        Map<String, Object> normalized = normalizeMap(rawSection);
        Object metrics = normalized.get("metrics");
        return metrics instanceof Map<?, ?> rawMetrics ? normalizeMap(rawMetrics) : Map.of();
    }

    private static Map<String, Object> modelVersionRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", rs.getLong("id"));
        row.put("versionName", rs.getString("version_name"));
        row.put("modelType", rs.getString("model_type"));
        row.put("targetVariable", rs.getString("target_variable"));
        row.put("trainedAt", rs.getTimestamp("trained_at") == null ? null : rs.getTimestamp("trained_at").toInstant().toString());
        row.put("artifactPath", rs.getString("artifact_path"));
        row.put("status", rs.getString("status"));
        row.put("active", rs.getBoolean("is_active"));
        row.put("createdAt", rs.getTimestamp("created_at").toInstant().toString());
        return row;
    }

    private static void validateRetrainRequest(ModelRetrainRequest request) {
        if (request.startSeason() != null && request.endSeason() != null && request.startSeason() > request.endSeason()) {
            throw new IllegalArgumentException("startSeason must be before or equal to endSeason");
        }
        if (request.limit() != null && request.limit() < 2) {
            throw new IllegalArgumentException("limit must be at least 2");
        }
        if (request.trainRatio() != null && (request.trainRatio() <= 0 || request.trainRatio() >= 1)) {
            throw new IllegalArgumentException("trainRatio must be greater than 0 and less than 1");
        }
    }

    private static String trainingRange(ModelRetrainRequest request) {
        if (request.startSeason() == null && request.endSeason() == null) {
            return "all imported seasons";
        }
        String start = request.startSeason() == null ? "earliest" : String.valueOf(request.startSeason());
        String end = request.endSeason() == null ? "latest" : String.valueOf(request.endSeason());
        return start + "-" + end;
    }

    private static Long generatedId(KeyHolder keyHolder) {
        Map<String, Object> keys = keyHolder.getKeys();
        if (keys != null && keys.get("id") instanceof Number id) {
            return id.longValue();
        }
        if (keys != null && keys.get("ID") instanceof Number id) {
            return id.longValue();
        }
        if (keys != null && !keys.isEmpty()) {
            Object value = keys.values().iterator().next();
            return ((Number) value).longValue();
        }
        return keyHolder.getKey().longValue();
    }

    private static Double averageMae(Map<String, Object> metrics) {
        List<Double> maes = metrics.values().stream()
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .map(metric -> doubleValue(normalizeMap(metric), "mae"))
                .filter(value -> value != null)
                .toList();
        if (maes.isEmpty()) {
            return null;
        }
        return maes.stream().mapToDouble(Double::doubleValue).average().orElseThrow();
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

    private static Map<String, Object> normalizeMap(Map<?, ?> source) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        if (source == null) {
            return normalized;
        }
        source.forEach((key, value) -> normalized.put(String.valueOf(key), normalize(value)));
        return normalized;
    }

    private static Object normalize(Object value) {
        if (value instanceof Map<?, ?> map) {
            return normalizeMap(map);
        }
        if (value instanceof Iterable<?> iterable) {
            List<Object> normalized = new ArrayList<>();
            iterable.forEach(item -> normalized.add(normalize(item)));
            return normalized;
        }
        return value;
    }

    private static String stringValue(Map<String, Object> source, String... keys) {
        for (String key : keys) {
            Object value = source.get(key);
            if (value instanceof String stringValue && !stringValue.isBlank()) {
                return stringValue;
            }
        }
        return null;
    }

    private static Integer intValue(Map<String, Object> source, String... keys) {
        for (String key : keys) {
            Object value = source.get(key);
            if (value instanceof Number number) {
                return number.intValue();
            }
        }
        return null;
    }

    private static Double doubleValue(Map<String, Object> source, String... keys) {
        for (String key : keys) {
            Object value = source.get(key);
            if (value instanceof Number number) {
                return number.doubleValue();
            }
        }
        return null;
    }

    private static Long nullableLong(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static Double nullableDouble(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        double value = rs.getDouble(column);
        return rs.wasNull() ? null : value;
    }
}
