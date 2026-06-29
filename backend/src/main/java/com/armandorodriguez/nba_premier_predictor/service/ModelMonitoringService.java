package com.armandorodriguez.nba_premier_predictor.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ModelMonitoringService {

    private final JdbcTemplate jdbcTemplate;

    public ModelMonitoringService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<String, Object> monitoring(int limit) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("totalErrors", countErrors());
        response.put("targetSummaries", targetSummaries());
        response.put("recentErrors", predictionErrors(limit));
        return response;
    }

    public List<Map<String, Object>> predictionErrors(int limit) {
        int rowLimit = rowLimit(limit);
        return jdbcTemplate.query("""
                select e.id, e.prediction_id, p.prediction_type, p.game_id, p.player_id,
                       p.team_id, mv.version_name, e.target_variable,
                       e.predicted_value, e.actual_value, e.absolute_error, e.recorded_at
                from prediction_errors e
                join predictions p on p.id = e.prediction_id
                left join model_versions mv on mv.id = p.model_version_id
                order by e.recorded_at desc, e.id desc
                limit ?
                """, (rs, rowNum) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", rs.getLong("id"));
            row.put("predictionId", rs.getLong("prediction_id"));
            row.put("predictionType", rs.getString("prediction_type"));
            row.put("gameId", nullableLong(rs, "game_id"));
            row.put("playerId", nullableLong(rs, "player_id"));
            row.put("teamId", nullableLong(rs, "team_id"));
            row.put("modelVersion", rs.getString("version_name"));
            row.put("targetVariable", rs.getString("target_variable"));
            row.put("predictedValue", nullableDouble(rs, "predicted_value"));
            row.put("actualValue", nullableDouble(rs, "actual_value"));
            row.put("absoluteError", nullableDouble(rs, "absolute_error"));
            row.put("recordedAt", rs.getTimestamp("recorded_at").toInstant().toString());
            return row;
        }, rowLimit);
    }

    @Transactional
    public Map<String, Object> refreshPredictionErrors() {
        int inserted = 0;
        inserted += insertPlayerError("points", "ps.projected_points", "s.points");
        inserted += insertPlayerError("rebounds", "ps.projected_rebounds", "s.rebounds_total");
        inserted += insertPlayerError("assists", "ps.projected_assists", "s.assists");
        inserted += insertPlayerError("steals", "ps.projected_steals", "s.steals");
        inserted += insertPlayerError("blocks", "ps.projected_blocks", "s.blocks");
        inserted += insertPlayerError("turnovers", "ps.projected_turnovers", "s.turnovers");
        inserted += insertPlayerError("minutes", "ps.projected_minutes", "s.num_minutes");
        inserted += insertFantasyError();
        inserted += insertGameScoreError("home_team_score", "tsp.home_team_score", "g.home_score");
        inserted += insertGameScoreError("away_team_score", "tsp.away_team_score", "g.away_score");
        inserted += insertGameScoreError("point_differential", "tsp.point_differential", "(g.home_score - g.away_score)");

        Map<String, Object> response = monitoring(10);
        response.put("insertedErrors", inserted);
        return response;
    }

    private int insertPlayerError(String targetVariable, String predictedExpression, String actualExpression) {
        return jdbcTemplate.update("""
                insert into prediction_errors (
                    prediction_id, target_variable, predicted_value, actual_value,
                    absolute_error, squared_error
                )
                select p.id, ?, %1$s, %2$s,
                       abs(%1$s - %2$s),
                       (%1$s - %2$s) * (%1$s - %2$s)
                from predictions p
                join player_stat_predictions ps on ps.prediction_id = p.id
                join player_game_stats s on s.game_id = p.game_id
                    and s.player_id = p.player_id
                    and (p.team_id is null or s.team_id = p.team_id)
                where p.prediction_type = 'player_stat'
                  and %1$s is not null
                  and %2$s is not null
                  and not exists (
                      select 1
                      from prediction_errors e
                      where e.prediction_id = p.id and e.target_variable = ?
                  )
                """.formatted(predictedExpression, actualExpression), targetVariable, targetVariable);
    }

    private int insertFantasyError() {
        String actualFantasy = """
                (coalesce(s.points, 0)
                 + (1.2 * coalesce(s.rebounds_total, 0))
                 + (1.5 * coalesce(s.assists, 0))
                 + (3.0 * coalesce(s.steals, 0))
                 + (3.0 * coalesce(s.blocks, 0))
                 - coalesce(s.turnovers, 0))
                """;
        return jdbcTemplate.update("""
                insert into prediction_errors (
                    prediction_id, target_variable, predicted_value, actual_value,
                    absolute_error, squared_error
                )
                select p.id, 'fantasy_points', fp.fantasy_points, %1$s,
                       abs(fp.fantasy_points - %1$s),
                       (fp.fantasy_points - %1$s) * (fp.fantasy_points - %1$s)
                from predictions p
                join fantasy_predictions fp on fp.prediction_id = p.id
                join player_game_stats s on s.game_id = p.game_id
                    and s.player_id = p.player_id
                    and (p.team_id is null or s.team_id = p.team_id)
                where p.prediction_type = 'fantasy'
                  and fp.fantasy_points is not null
                  and s.points is not null
                  and not exists (
                      select 1
                      from prediction_errors e
                      where e.prediction_id = p.id and e.target_variable = 'fantasy_points'
                  )
                """.formatted(actualFantasy));
    }

    private int insertGameScoreError(String targetVariable, String predictedExpression, String actualExpression) {
        return jdbcTemplate.update("""
                insert into prediction_errors (
                    prediction_id, target_variable, predicted_value, actual_value,
                    absolute_error, squared_error
                )
                select p.id, ?, %1$s, %2$s,
                       abs(%1$s - %2$s),
                       (%1$s - %2$s) * (%1$s - %2$s)
                from predictions p
                join team_score_predictions tsp on tsp.prediction_id = p.id
                join games g on g.game_id = p.game_id
                where p.prediction_type = 'game_score'
                  and %1$s is not null
                  and %2$s is not null
                  and not exists (
                      select 1
                      from prediction_errors e
                      where e.prediction_id = p.id and e.target_variable = ?
                  )
                """.formatted(predictedExpression, actualExpression), targetVariable, targetVariable);
    }

    private List<Map<String, Object>> targetSummaries() {
        return jdbcTemplate.query("""
                select target_variable, count(*) as prediction_count,
                       avg(absolute_error) as average_miss,
                       sqrt(avg(squared_error)) as rmse
                from prediction_errors
                group by target_variable
                order by target_variable
                """, (rs, rowNum) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("targetVariable", rs.getString("target_variable"));
            row.put("predictionCount", rs.getInt("prediction_count"));
            row.put("averageMiss", nullableDouble(rs, "average_miss"));
            row.put("rmse", nullableDouble(rs, "rmse"));
            return row;
        });
    }

    private Integer countErrors() {
        return jdbcTemplate.queryForObject("select count(*) from prediction_errors", Integer.class);
    }

    private static int rowLimit(int limit) {
        return Math.max(1, Math.min(limit, 100));
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
