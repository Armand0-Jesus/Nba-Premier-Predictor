package com.armandorodriguez.nba_premier_predictor.service;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.armandorodriguez.nba_premier_predictor.dto.PlayerPredictionRequest;
import com.armandorodriguez.nba_premier_predictor.dto.PlayerPredictionResponse;
import com.armandorodriguez.nba_premier_predictor.dto.PredictionHistoryResponse;
import com.armandorodriguez.nba_premier_predictor.dto.TeamScorePredictionRequest;
import com.armandorodriguez.nba_premier_predictor.dto.TeamScorePredictionResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class PredictionService {

    private final MlPredictionClient mlPredictionClient;
    private final ModelMetadataService modelMetadataService;
    private final PredictionCacheService predictionCacheService;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public PredictionService(
            MlPredictionClient mlPredictionClient,
            ModelMetadataService modelMetadataService,
            PredictionCacheService predictionCacheService,
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper) {
        this.mlPredictionClient = mlPredictionClient;
        this.modelMetadataService = modelMetadataService;
        this.predictionCacheService = predictionCacheService;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public PlayerPredictionResponse predictPlayer(PlayerPredictionRequest request) {
        String fingerprint = playerFingerprint("player_stat", request);
        Optional<PlayerPredictionResponse> cached = cachedPlayerPrediction(fingerprint);
        if (cached.isPresent()) {
            return cached.get();
        }
        PlayerPredictionResponse response = mlPredictionClient.predictPlayer(request);
        Long predictionId = savePrediction("player_stat", request, response);
        savePlayerStatPrediction(predictionId, response);
        PlayerPredictionResponse saved = response.withPredictionId(predictionId);
        cacheAfterCommit(fingerprint, saved);
        return saved;
    }

    @Transactional
    public PlayerPredictionResponse predictFantasy(PlayerPredictionRequest request) {
        String fingerprint = playerFingerprint("fantasy", request);
        Optional<PlayerPredictionResponse> cached = cachedPlayerPrediction(fingerprint);
        if (cached.isPresent()) {
            return cached.get();
        }
        PlayerPredictionResponse response = mlPredictionClient.predictFantasy(request);
        Long predictionId = savePrediction("fantasy", request, response);
        saveFantasyPrediction(predictionId, response);
        PlayerPredictionResponse saved = response.withPredictionId(predictionId);
        cacheAfterCommit(fingerprint, saved);
        return saved;
    }

    @Transactional
    public TeamScorePredictionResponse predictGameScore(TeamScorePredictionRequest request) {
        String fingerprint = gameScoreFingerprint(request);
        Optional<TeamScorePredictionResponse> cached = cachedGameScorePrediction(fingerprint);
        if (cached.isPresent()) {
            return cached.get();
        }
        TeamScorePredictionResponse response = mlPredictionClient.predictGameScore(request);
        Long predictionId = saveGameScorePredictionParent(request, response);
        saveTeamScorePrediction(predictionId, response);
        TeamScorePredictionResponse saved = response.withPredictionId(predictionId);
        cacheAfterCommit(fingerprint, saved);
        return saved;
    }

    public List<PredictionHistoryResponse> history(int limit) {
        return jdbcTemplate.query("""
                select p.id, p.prediction_type, p.game_id, p.player_id, p.team_id,
                       mv.version_name, p.requested_at, p.confidence_score,
                       ps.projected_points, ps.projected_rebounds, ps.projected_assists,
                       ps.projected_minutes, fp.fantasy_points, fp.floor_projection,
                       fp.ceiling_projection, fp.risk_level,
                       tsp.home_team_id, tsp.away_team_id,
                       tsp.home_team_score, tsp.away_team_score,
                       tsp.predicted_winner_team_id, tsp.point_differential
                from predictions p
                left join model_versions mv on mv.id = p.model_version_id
                left join player_stat_predictions ps on ps.prediction_id = p.id
                left join fantasy_predictions fp on fp.prediction_id = p.id
                left join team_score_predictions tsp on tsp.prediction_id = p.id
                order by p.requested_at desc, p.id desc
                limit ?
                """, (rs, rowNum) -> new PredictionHistoryResponse(
                rs.getLong("id"),
                rs.getString("prediction_type"),
                nullableLong(rs, "game_id"),
                nullableLong(rs, "player_id"),
                nullableLong(rs, "team_id"),
                rs.getString("version_name"),
                rs.getTimestamp("requested_at").toInstant(),
                nullableDouble(rs, "confidence_score"),
                nullableDouble(rs, "projected_points"),
                nullableDouble(rs, "projected_rebounds"),
                nullableDouble(rs, "projected_assists"),
                nullableDouble(rs, "projected_minutes"),
                nullableDouble(rs, "fantasy_points"),
                nullableDouble(rs, "floor_projection"),
                nullableDouble(rs, "ceiling_projection"),
                rs.getString("risk_level"),
                nullableLong(rs, "home_team_id"),
                nullableLong(rs, "away_team_id"),
                nullableDouble(rs, "home_team_score"),
                nullableDouble(rs, "away_team_score"),
                nullableLong(rs, "predicted_winner_team_id"),
                nullableDouble(rs, "point_differential")), limit);
    }

    private Long savePrediction(String predictionType, PlayerPredictionRequest request, PlayerPredictionResponse response) {
        Long modelVersionId = ensureModelVersion(response.modelVersion(), "player_stat_fantasy");
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    insert into predictions (
                        prediction_type, game_id, player_id, team_id, model_version_id,
                        data_cutoff_time, confidence_score, explanation
                    ) values (?, ?, ?, ?, ?, ?, ?, cast(? as jsonb))
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, predictionType);
            setLong(ps, 2, request.gameId());
            ps.setLong(3, request.playerId());
            setLong(ps, 4, request.teamId());
            ps.setLong(5, modelVersionId);
            if (request.dataCutoffTime() == null) {
                ps.setTimestamp(6, null);
            } else {
                ps.setTimestamp(6, Timestamp.valueOf(request.dataCutoffTime()));
            }
            setDouble(ps, 7, response.confidenceScore());
            ps.setString(8, writeJson(response.factors()));
            return ps;
        }, keyHolder);
        Object key = keyHolder.getKeys().get("id");
        return ((Number) key).longValue();
    }

    private String playerFingerprint(String predictionType, PlayerPredictionRequest request) {
        if (!predictionCacheService.enabled()) {
            return null;
        }
        String modelVersion = ModelMetadataService.playerModelVersion(modelMetadataService.versions());
        return predictionCacheService.playerFingerprint(predictionType, request, modelVersion);
    }

    private String gameScoreFingerprint(TeamScorePredictionRequest request) {
        if (!predictionCacheService.enabled()) {
            return null;
        }
        String modelVersion = ModelMetadataService.gameScoreModelVersion(modelMetadataService.versions());
        return predictionCacheService.gameScoreFingerprint(request, modelVersion);
    }

    private Optional<PlayerPredictionResponse> cachedPlayerPrediction(String fingerprint) {
        return fingerprint == null
                ? Optional.empty()
                : predictionCacheService.get(fingerprint, PlayerPredictionResponse.class);
    }

    private Optional<TeamScorePredictionResponse> cachedGameScorePrediction(String fingerprint) {
        return fingerprint == null
                ? Optional.empty()
                : predictionCacheService.get(fingerprint, TeamScorePredictionResponse.class);
    }

    private void cacheAfterCommit(String fingerprint, Object response) {
        if (fingerprint == null) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            predictionCacheService.put(fingerprint, response);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                predictionCacheService.put(fingerprint, response);
            }
        });
    }

    private Long saveGameScorePredictionParent(TeamScorePredictionRequest request, TeamScorePredictionResponse response) {
        Long modelVersionId = ensureModelVersion(response.modelVersion(), "game_score");
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    insert into predictions (
                        prediction_type, game_id, player_id, team_id, model_version_id,
                        data_cutoff_time, confidence_score, explanation
                    ) values (?, ?, ?, ?, ?, ?, ?, cast(? as jsonb))
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, "game_score");
            ps.setLong(2, request.gameId());
            ps.setObject(3, null);
            ps.setObject(4, null);
            ps.setLong(5, modelVersionId);
            if (request.dataCutoffTime() == null) {
                ps.setTimestamp(6, null);
            } else {
                ps.setTimestamp(6, Timestamp.valueOf(request.dataCutoffTime()));
            }
            setDouble(ps, 7, response.confidenceScore());
            ps.setString(8, writeJson(response.factors()));
            return ps;
        }, keyHolder);
        Object key = keyHolder.getKeys().get("id");
        return ((Number) key).longValue();
    }

    private void savePlayerStatPrediction(Long predictionId, PlayerPredictionResponse response) {
        jdbcTemplate.update("""
                insert into player_stat_predictions (
                    prediction_id, projected_points, projected_rebounds, projected_assists,
                    projected_minutes
                ) values (?, ?, ?, ?, ?)
                """,
                predictionId,
                response.projectedPoints(),
                response.projectedRebounds(),
                response.projectedAssists(),
                response.projectedMinutes());
    }

    private void saveFantasyPrediction(Long predictionId, PlayerPredictionResponse response) {
        jdbcTemplate.update("""
                insert into fantasy_predictions (
                    prediction_id, fantasy_points, floor_projection, ceiling_projection,
                    risk_level, scoring_formula
                ) values (?, ?, ?, ?, ?, cast(? as jsonb))
                """,
                predictionId,
                response.fantasyPoints(),
                response.fantasyFloor(),
                response.fantasyCeiling(),
                response.riskLevel(),
                "{}");
    }

    private void saveTeamScorePrediction(Long predictionId, TeamScorePredictionResponse response) {
        jdbcTemplate.update("""
                insert into team_score_predictions (
                    prediction_id, home_team_id, away_team_id,
                    home_team_score, away_team_score,
                    predicted_winner_team_id, point_differential
                ) values (?, ?, ?, ?, ?, ?, ?)
                """,
                predictionId,
                response.homeTeamId(),
                response.awayTeamId(),
                response.homeTeamScore(),
                response.awayTeamScore(),
                response.predictedWinnerTeamId(),
                response.pointDifferential());
    }

    private Long ensureModelVersion(String versionName, String targetVariable) {
        List<Long> existing = jdbcTemplate.queryForList(
                "select id from model_versions where version_name = ?",
                Long.class,
                versionName);
        if (!existing.isEmpty()) {
            return existing.get(0);
        }
        jdbcTemplate.update("""
                insert into model_versions (
                    version_name, model_type, target_variable, status, is_active
                ) values (?, ?, ?, ?, ?)
                """, versionName, "ridge-regression", targetVariable, "active", true);
        return jdbcTemplate.queryForObject(
                "select id from model_versions where version_name = ?",
                Long.class,
                versionName);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not serialize prediction explanation", ex);
        }
    }

    private static void setLong(PreparedStatement ps, int parameterIndex, Long value) throws java.sql.SQLException {
        if (value == null) {
            ps.setObject(parameterIndex, null);
        } else {
            ps.setLong(parameterIndex, value);
        }
    }

    private static void setDouble(PreparedStatement ps, int parameterIndex, Double value) throws java.sql.SQLException {
        if (value == null) {
            ps.setObject(parameterIndex, null);
        } else {
            ps.setDouble(parameterIndex, value);
        }
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
