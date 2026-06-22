package com.armandorodriguez.nba_premier_predictor.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
import com.armandorodriguez.nba_premier_predictor.feature.TeamFeatureCalculator;
import com.armandorodriguez.nba_premier_predictor.feature.TeamFeatureRow;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class TeamFeatureSnapshotService {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final TeamFeatureCalculator calculator;

    public TeamFeatureSnapshotService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper, TeamFeatureCalculator calculator) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.calculator = calculator;
    }

    @Transactional
    public FeatureGenerationResponse generateTeam(Integer seasonStartYear) {
        List<TeamFeatureRow> rows = loadTeamRows(seasonStartYear);
        Map<Long, List<TeamFeatureRow>> byTeam = groupByTeam(rows);
        List<Object[]> batch = new ArrayList<>();

        for (List<TeamFeatureRow> teamRows : byTeam.values()) {
            List<TeamFeatureRow> prior = new ArrayList<>();
            for (TeamFeatureRow row : teamRows) {
                LocalDateTime dataCutoff = row.gameDateTime().minusSeconds(1);
                batch.add(new Object[] {
                        Timestamp.valueOf(dataCutoff),
                        row.gameId(),
                        row.teamId(),
                        Timestamp.valueOf(dataCutoff),
                        toJson(calculator.calculate(row, prior, opponentRowsBefore(byTeam, row)))
                });
                prior.add(row);
            }
        }

        persistTeam(batch);
        return new FeatureGenerationResponse("team", seasonStartYear, batch.size());
    }

    @Transactional
    public FeatureGenerationResponse generateGame(Integer seasonStartYear) {
        List<TeamFeatureRow> rows = loadTeamRows(seasonStartYear);
        Map<Long, List<TeamFeatureRow>> byTeam = groupByTeam(rows);
        Map<Long, List<TeamFeatureRow>> byGame = rows.stream()
                .collect(Collectors.groupingBy(TeamFeatureRow::gameId, LinkedHashMap::new, Collectors.toList()));
        List<Object[]> batch = new ArrayList<>();

        for (Map.Entry<Long, List<TeamFeatureRow>> entry : byGame.entrySet()) {
            TeamFeatureRow home = findByHome(entry.getValue(), true);
            TeamFeatureRow away = findByHome(entry.getValue(), false);
            if (home == null || away == null) {
                continue;
            }

            Map<String, Object> homeFeatures = calculator.calculate(home, rowsBefore(byTeam, home), opponentRowsBefore(byTeam, home));
            Map<String, Object> awayFeatures = calculator.calculate(away, rowsBefore(byTeam, away), opponentRowsBefore(byTeam, away));
            Map<String, Object> features = new LinkedHashMap<>();
            features.put("home_team_id", home.teamId());
            features.put("away_team_id", away.teamId());
            putPrefixed(features, "home_", homeFeatures);
            putPrefixed(features, "away_", awayFeatures);
            features.put("games_played_prior_delta", difference(homeFeatures.get("games_played_prior"), awayFeatures.get("games_played_prior")));
            features.put("days_rest_delta", difference(homeFeatures.get("days_rest"), awayFeatures.get("days_rest")));
            features.put("season_point_differential_delta", difference(
                    homeFeatures.get("season_point_differential_avg"),
                    awayFeatures.get("season_point_differential_avg")));
            features.put("last_5_point_differential_delta", difference(
                    homeFeatures.get("last_5_point_differential_avg"),
                    awayFeatures.get("last_5_point_differential_avg")));

            LocalDateTime dataCutoff = home.gameDateTime().minusSeconds(1);
            batch.add(new Object[] {
                    Timestamp.valueOf(dataCutoff),
                    entry.getKey(),
                    Timestamp.valueOf(dataCutoff),
                    toJson(features)
            });
        }

        persistGame(batch);
        return new FeatureGenerationResponse("game", seasonStartYear, batch.size());
    }

    private List<TeamFeatureRow> loadTeamRows(Integer seasonStartYear) {
        return jdbcTemplate.query("""
                select t.game_id, t.team_id, t.opponent_team_id, g.season_start_year,
                       g.game_date_time_est, t.home, t.team_score, t.opponent_score,
                       t.assists, t.rebounds_total, t.turnovers
                from team_game_stats t
                join games g on g.game_id = t.game_id
                where (? is null or g.season_start_year = ?)
                  and g.game_date_time_est is not null
                order by g.game_date_time_est, t.game_id, t.team_id
                """, this::mapTeamRow, seasonStartYear, seasonStartYear);
    }

    private TeamFeatureRow mapTeamRow(ResultSet rs, int rowNum) throws SQLException {
        return new TeamFeatureRow(
                rs.getLong("game_id"),
                rs.getLong("team_id"),
                nullableLong(rs, "opponent_team_id"),
                nullableInt(rs, "season_start_year"),
                rs.getTimestamp("game_date_time_est").toLocalDateTime(),
                nullableBoolean(rs, "home"),
                nullableInt(rs, "team_score"),
                nullableInt(rs, "opponent_score"),
                nullableInt(rs, "assists"),
                nullableInt(rs, "rebounds_total"),
                nullableInt(rs, "turnovers"));
    }

    private Map<Long, List<TeamFeatureRow>> groupByTeam(List<TeamFeatureRow> rows) {
        Map<Long, List<TeamFeatureRow>> byTeam = rows.stream()
                .collect(Collectors.groupingBy(TeamFeatureRow::teamId, LinkedHashMap::new, Collectors.toList()));
        byTeam.values().forEach(teamRows -> teamRows.sort(Comparator.comparing(TeamFeatureRow::gameDateTime)
                .thenComparing(TeamFeatureRow::gameId)));
        return byTeam;
    }

    private List<TeamFeatureRow> opponentRowsBefore(Map<Long, List<TeamFeatureRow>> rowsByTeam, TeamFeatureRow target) {
        return rowsBefore(rowsByTeam, target.opponentTeamId(), target.gameDateTime());
    }

    private List<TeamFeatureRow> rowsBefore(Map<Long, List<TeamFeatureRow>> rowsByTeam, TeamFeatureRow target) {
        return rowsBefore(rowsByTeam, target.teamId(), target.gameDateTime());
    }

    private List<TeamFeatureRow> rowsBefore(Map<Long, List<TeamFeatureRow>> rowsByTeam, Long teamId, LocalDateTime gameDateTime) {
        return rowsByTeam.getOrDefault(teamId, List.of()).stream()
                .filter(row -> row.gameDateTime().isBefore(gameDateTime))
                .toList();
    }

    private TeamFeatureRow findByHome(List<TeamFeatureRow> rows, boolean home) {
        return rows.stream()
                .filter(row -> Boolean.valueOf(home).equals(row.home()))
                .findFirst()
                .orElse(null);
    }

    private void persistTeam(List<Object[]> batch) {
        if (batch.isEmpty()) {
            return;
        }

        String sql = isPostgres() ? """
                insert into team_feature_snapshots (snapshot_time, game_id, team_id, data_cutoff_time, features)
                values (?, ?, ?, ?, cast(? as jsonb))
                on conflict (snapshot_time, game_id, team_id) do update set
                    generated_at = now(),
                    data_cutoff_time = excluded.data_cutoff_time,
                    features = excluded.features
                """ : """
                insert into team_feature_snapshots (snapshot_time, game_id, team_id, data_cutoff_time, features)
                values (?, ?, ?, ?, ?)
                """;
        jdbcTemplate.batchUpdate(sql, batch);
    }

    private void persistGame(List<Object[]> batch) {
        if (batch.isEmpty()) {
            return;
        }

        String sql = isPostgres() ? """
                insert into game_feature_snapshots (snapshot_time, game_id, data_cutoff_time, features)
                values (?, ?, ?, cast(? as jsonb))
                on conflict (snapshot_time, game_id) do update set
                    generated_at = now(),
                    data_cutoff_time = excluded.data_cutoff_time,
                    features = excluded.features
                """ : """
                insert into game_feature_snapshots (snapshot_time, game_id, data_cutoff_time, features)
                values (?, ?, ?, ?)
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
            throw new IllegalStateException("Could not serialize feature snapshot", ex);
        }
    }

    private static void putPrefixed(Map<String, Object> target, String prefix, Map<String, Object> source) {
        source.forEach((key, value) -> target.put(prefix + key, value));
    }

    private static Double difference(Object left, Object right) {
        if (!(left instanceof Number leftNumber) || !(right instanceof Number rightNumber)) {
            return null;
        }
        return round(leftNumber.doubleValue() - rightNumber.doubleValue());
    }

    private static double round(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
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
