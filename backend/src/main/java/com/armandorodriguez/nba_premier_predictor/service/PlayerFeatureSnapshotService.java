package com.armandorodriguez.nba_premier_predictor.service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.armandorodriguez.nba_premier_predictor.dto.FeatureGenerationResponse;
import com.armandorodriguez.nba_premier_predictor.feature.PlayerFeatureCalculator;
import com.armandorodriguez.nba_premier_predictor.feature.PlayerFeatureRow;
import com.armandorodriguez.nba_premier_predictor.feature.TeamFeatureRow;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class PlayerFeatureSnapshotService {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final PlayerFeatureCalculator calculator;

    public PlayerFeatureSnapshotService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper, PlayerFeatureCalculator calculator) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.calculator = calculator;
    }

    @Transactional
    public FeatureGenerationResponse generate(Integer seasonStartYear) {
        List<PlayerFeatureRow> playerRows = loadPlayerRows(seasonStartYear);
        Map<Long, List<TeamFeatureRow>> teamRows = loadTeamRows(seasonStartYear).stream()
                .collect(Collectors.groupingBy(TeamFeatureRow::teamId, LinkedHashMap::new, Collectors.toList()));

        List<Object[]> batch = new ArrayList<>();
        Map<Long, List<PlayerFeatureRow>> byPlayer = playerRows.stream()
                .collect(Collectors.groupingBy(PlayerFeatureRow::playerId, LinkedHashMap::new, Collectors.toList()));

        for (List<PlayerFeatureRow> rows : byPlayer.values()) {
            rows.sort(Comparator.comparing(PlayerFeatureRow::gameDateTime));
            List<PlayerFeatureRow> prior = new ArrayList<>();
            for (PlayerFeatureRow row : rows) {
                LocalDateTime dataCutoff = row.gameDateTime().minusSeconds(1);
                batch.add(new Object[] {
                        Timestamp.valueOf(dataCutoff),
                        row.gameId(),
                        row.playerId(),
                        row.teamId(),
                        Timestamp.valueOf(dataCutoff),
                        toJson(calculator.calculate(row, prior, opponentRowsBefore(teamRows, row)))
                });
                prior.add(row);
            }
        }

        persist(batch);
        return new FeatureGenerationResponse("player", seasonStartYear, batch.size());
    }

    private List<PlayerFeatureRow> loadPlayerRows(Integer seasonStartYear) {
        return jdbcTemplate.query("""
                select s.game_id, s.player_id, s.team_id, s.opponent_team_id, g.season_start_year,
                       g.game_date_time_est, s.home, s.num_minutes, s.points, s.rebounds_total,
                       s.assists, s.turnovers
                from player_game_stats s
                join games g on g.game_id = s.game_id
                where (? is null or g.season_start_year = ?)
                  and g.game_date_time_est is not null
                order by s.player_id, g.game_date_time_est
                """, this::mapPlayerRow, seasonStartYear, seasonStartYear);
    }

    private List<TeamFeatureRow> loadTeamRows(Integer seasonStartYear) {
        return jdbcTemplate.query("""
                select t.team_id, g.game_date_time_est, t.team_score, t.opponent_score
                from team_game_stats t
                join games g on g.game_id = t.game_id
                where (? is null or g.season_start_year = ?)
                  and g.game_date_time_est is not null
                order by t.team_id, g.game_date_time_est
                """, this::mapTeamRow, seasonStartYear, seasonStartYear);
    }

    private PlayerFeatureRow mapPlayerRow(ResultSet rs, int rowNum) throws SQLException {
        return new PlayerFeatureRow(
                rs.getLong("game_id"),
                rs.getLong("player_id"),
                nullableLong(rs, "team_id"),
                nullableLong(rs, "opponent_team_id"),
                nullableInt(rs, "season_start_year"),
                rs.getTimestamp("game_date_time_est").toLocalDateTime(),
                nullableBoolean(rs, "home"),
                rs.getBigDecimal("num_minutes"),
                nullableInt(rs, "points"),
                nullableInt(rs, "rebounds_total"),
                nullableInt(rs, "assists"),
                nullableInt(rs, "turnovers"));
    }

    private TeamFeatureRow mapTeamRow(ResultSet rs, int rowNum) throws SQLException {
        return new TeamFeatureRow(
                rs.getLong("team_id"),
                rs.getTimestamp("game_date_time_est").toLocalDateTime(),
                nullableInt(rs, "team_score"),
                nullableInt(rs, "opponent_score"));
    }

    private List<TeamFeatureRow> opponentRowsBefore(Map<Long, List<TeamFeatureRow>> rowsByTeam, PlayerFeatureRow target) {
        List<TeamFeatureRow> rows = rowsByTeam.getOrDefault(target.opponentTeamId(), List.of());
        return rows.stream()
                .filter(row -> row.gameDateTime().isBefore(target.gameDateTime()))
                .toList();
    }

    private void persist(List<Object[]> batch) {
        if (batch.isEmpty()) {
            return;
        }

        String sql = isPostgres() ? """
                insert into player_feature_snapshots (snapshot_time, game_id, player_id, team_id, data_cutoff_time, features)
                values (?, ?, ?, ?, ?, cast(? as jsonb))
                on conflict (snapshot_time, game_id, player_id) do update set
                    team_id = excluded.team_id,
                    generated_at = now(),
                    data_cutoff_time = excluded.data_cutoff_time,
                    features = excluded.features
                """ : """
                insert into player_feature_snapshots (snapshot_time, game_id, player_id, team_id, data_cutoff_time, features)
                values (?, ?, ?, ?, ?, ?)
                """;
        jdbcTemplate.batchUpdate(sql, batch);
    }

    private boolean isPostgres() {
        return Boolean.TRUE.equals(jdbcTemplate.execute((ConnectionCallback<Boolean>) connection ->
                connection.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT).contains("postgres")));
    }

    private String toJson(Map<String, Object> features) {
        try {
            return objectMapper.writeValueAsString(features);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not serialize player feature snapshot", ex);
        }
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static Integer nullableInt(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private static Boolean nullableBoolean(ResultSet rs, String column) throws SQLException {
        boolean value = rs.getBoolean(column);
        return rs.wasNull() ? null : value;
    }
}
