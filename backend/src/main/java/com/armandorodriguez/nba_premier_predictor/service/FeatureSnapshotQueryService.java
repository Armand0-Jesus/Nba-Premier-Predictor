package com.armandorodriguez.nba_premier_predictor.service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.armandorodriguez.nba_premier_predictor.dto.FeatureSnapshotResponse;
import com.armandorodriguez.nba_premier_predictor.exception.ResourceNotFoundException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class FeatureSnapshotQueryService {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public FeatureSnapshotQueryService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public FeatureSnapshotResponse latestPlayerSnapshot(Long gameId, Long playerId) {
        return jdbcTemplate.query("""
                select f.id, f.game_id, f.player_id, f.team_id, g.home_team_id, g.away_team_id,
                       f.snapshot_time, f.data_cutoff_time, f.features
                from player_feature_snapshots f
                join games g on g.game_id = f.game_id
                where f.game_id = ? and f.player_id = ?
                order by f.data_cutoff_time desc, f.snapshot_time desc, f.id desc
                limit 1
                """, this::mapPlayerSnapshot, gameId, playerId).stream()
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Player feature snapshot not found for game " + gameId + " and player " + playerId));
    }

    public FeatureSnapshotResponse latestGameSnapshot(Long gameId) {
        return jdbcTemplate.query("""
                select f.id, f.game_id, g.home_team_id, g.away_team_id,
                       f.snapshot_time, f.data_cutoff_time, f.features
                from game_feature_snapshots f
                join games g on g.game_id = f.game_id
                where f.game_id = ?
                order by f.data_cutoff_time desc, f.snapshot_time desc, f.id desc
                limit 1
                """, this::mapGameSnapshot, gameId).stream()
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Game feature snapshot not found for game " + gameId));
    }

    private FeatureSnapshotResponse mapPlayerSnapshot(ResultSet rs, int rowNum) throws SQLException {
        return new FeatureSnapshotResponse(
                rs.getLong("id"),
                "player",
                rs.getLong("game_id"),
                rs.getLong("player_id"),
                nullableLong(rs, "team_id"),
                nullableLong(rs, "home_team_id"),
                nullableLong(rs, "away_team_id"),
                rs.getTimestamp("snapshot_time").toLocalDateTime(),
                rs.getTimestamp("data_cutoff_time").toLocalDateTime(),
                readFeatures(rs.getString("features")));
    }

    private FeatureSnapshotResponse mapGameSnapshot(ResultSet rs, int rowNum) throws SQLException {
        return new FeatureSnapshotResponse(
                rs.getLong("id"),
                "game",
                rs.getLong("game_id"),
                null,
                null,
                nullableLong(rs, "home_team_id"),
                nullableLong(rs, "away_team_id"),
                rs.getTimestamp("snapshot_time").toLocalDateTime(),
                rs.getTimestamp("data_cutoff_time").toLocalDateTime(),
                readFeatures(rs.getString("features")));
    }

    private Map<String, Object> readFeatures(String featuresJson) {
        try {
            return objectMapper.readValue(featuresJson, new TypeReference<>() {
            });
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not read feature snapshot JSON", ex);
        }
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }
}
