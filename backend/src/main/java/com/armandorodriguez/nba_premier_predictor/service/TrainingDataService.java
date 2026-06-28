package com.armandorodriguez.nba_premier_predictor.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.armandorodriguez.nba_premier_predictor.dto.PlayerTrainingDataRow;
import com.armandorodriguez.nba_premier_predictor.dto.PlayerTrainingTargets;
import com.armandorodriguez.nba_premier_predictor.dto.TeamScoreTrainingDataRow;
import com.armandorodriguez.nba_premier_predictor.dto.TeamScoreTrainingTargets;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class TrainingDataService {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public TrainingDataService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public List<PlayerTrainingDataRow> playerStatRows(
            Integer seasonStartYear,
            Integer startSeason,
            Integer endSeason,
            int limit,
            int offset) {
        List<Object> params = new ArrayList<>();
        String seasonFilter = seasonFilter(seasonStartYear, startSeason, endSeason, params);
        String sql = """
                select f.game_id, f.player_id, f.team_id, g.season_start_year, g.game_date_time_est,
                       f.data_cutoff_time, f.features, s.points, s.rebounds_total, s.assists,
                       s.steals, s.blocks, s.turnovers, s.num_minutes
                from player_feature_snapshots f
                join player_game_stats s
                  on s.game_id = f.game_id
                 and s.player_id = f.player_id
                 and (s.team_id = f.team_id or s.team_id is null or f.team_id is null)
                join games g on g.game_id = f.game_id
                where s.points is not null
                  and s.rebounds_total is not null
                  and s.assists is not null
                %sorder by g.game_date_time_est, f.game_id, f.player_id
                limit ?
                offset ?
                """.formatted(seasonFilter);
        params.add(limit);
        params.add(offset);
        return jdbcTemplate.query(sql, this::mapRow, params.toArray());
    }

    public List<TeamScoreTrainingDataRow> gameScoreRows(
            Integer seasonStartYear,
            Integer startSeason,
            Integer endSeason,
            int limit,
            int offset) {
        List<Object> params = new ArrayList<>();
        String seasonFilter = seasonFilter(seasonStartYear, startSeason, endSeason, params);
        String sql = """
                select f.game_id, g.home_team_id, g.away_team_id, g.season_start_year,
                       g.game_date_time_est, f.data_cutoff_time, f.features,
                       g.home_score, g.away_score, g.winner_team_id
                from game_feature_snapshots f
                join games g on g.game_id = f.game_id
                where g.home_score is not null
                  and g.away_score is not null
                %sorder by g.game_date_time_est, f.game_id
                limit ?
                offset ?
                """.formatted(seasonFilter);
        params.add(limit);
        params.add(offset);
        return jdbcTemplate.query(sql, this::mapGameScoreRow, params.toArray());
    }

    private static String seasonFilter(
            Integer seasonStartYear,
            Integer startSeason,
            Integer endSeason,
            List<Object> params) {
        StringBuilder filter = new StringBuilder();
        if (seasonStartYear != null) {
            filter.append("                  and g.season_start_year = ?\n");
            params.add(seasonStartYear);
        }
        if (startSeason != null) {
            filter.append("                  and g.season_start_year >= ?\n");
            params.add(startSeason);
        }
        if (endSeason != null) {
            filter.append("                  and g.season_start_year <= ?\n");
            params.add(endSeason);
        }
        return filter.toString();
    }

    private PlayerTrainingDataRow mapRow(ResultSet rs, int rowNum) throws SQLException {
        Integer points = nullableInt(rs, "points");
        Integer rebounds = nullableInt(rs, "rebounds_total");
        Integer assists = nullableInt(rs, "assists");
        Integer steals = nullableInt(rs, "steals");
        Integer blocks = nullableInt(rs, "blocks");
        Integer turnovers = nullableInt(rs, "turnovers");

        return new PlayerTrainingDataRow(
                rs.getLong("game_id"),
                rs.getLong("player_id"),
                nullableLong(rs, "team_id"),
                nullableInt(rs, "season_start_year"),
                rs.getTimestamp("game_date_time_est").toLocalDateTime(),
                rs.getTimestamp("data_cutoff_time").toLocalDateTime(),
                readFeatures(rs.getString("features")),
                new PlayerTrainingTargets(
                        points,
                        rebounds,
                        assists,
                        steals,
                        blocks,
                        turnovers,
                        rs.getBigDecimal("num_minutes"),
                        fantasyPoints(points, rebounds, assists, steals, blocks, turnovers)));
    }

    private TeamScoreTrainingDataRow mapGameScoreRow(ResultSet rs, int rowNum) throws SQLException {
        Integer homeScore = nullableInt(rs, "home_score");
        Integer awayScore = nullableInt(rs, "away_score");
        return new TeamScoreTrainingDataRow(
                rs.getLong("game_id"),
                nullableLong(rs, "home_team_id"),
                nullableLong(rs, "away_team_id"),
                nullableInt(rs, "season_start_year"),
                rs.getTimestamp("game_date_time_est").toLocalDateTime(),
                rs.getTimestamp("data_cutoff_time").toLocalDateTime(),
                readFeatures(rs.getString("features")),
                new TeamScoreTrainingTargets(
                        homeScore,
                        awayScore,
                        nullableLong(rs, "winner_team_id"),
                        homeScore == null || awayScore == null ? null : homeScore - awayScore));
    }

    private Map<String, Object> readFeatures(String featuresJson) {
        try {
            return objectMapper.readValue(featuresJson, new TypeReference<>() {
            });
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not read feature snapshot JSON", ex);
        }
    }

    private static Double fantasyPoints(
            Integer points,
            Integer rebounds,
            Integer assists,
            Integer steals,
            Integer blocks,
            Integer turnovers) {
        if (points == null || rebounds == null || assists == null) {
            return null;
        }
        double value = points
                + (1.2 * rebounds)
                + (1.5 * assists)
                + (3.0 * valueOrZero(steals))
                + (3.0 * valueOrZero(blocks))
                - valueOrZero(turnovers);
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private static int valueOrZero(Integer value) {
        return value == null ? 0 : value;
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static Integer nullableInt(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }
}
