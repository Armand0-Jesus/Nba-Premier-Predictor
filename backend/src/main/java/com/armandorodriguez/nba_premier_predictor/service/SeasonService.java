package com.armandorodriguez.nba_premier_predictor.service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.armandorodriguez.nba_premier_predictor.dto.SeasonResponse;

@Service
@Transactional(readOnly = true)
public class SeasonService {

    private final JdbcTemplate jdbcTemplate;

    public SeasonService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<SeasonResponse> allSeasons() {
        return jdbcTemplate.query("""
                select g.season_start_year, count(*) as game_count, max(g.game_date) as most_recent_game_date
                from games g
                where g.season_start_year is not null
                group by g.season_start_year
                order by g.season_start_year desc
                """, this::mapSeason);
    }

    public List<SeasonResponse> playerSeasons(Long playerId) {
        return jdbcTemplate.query("""
                select g.season_start_year, count(*) as game_count, max(g.game_date) as most_recent_game_date
                from player_game_stats s
                join games g on g.game_id = s.game_id
                where s.player_id = ? and g.season_start_year is not null
                group by g.season_start_year
                order by g.season_start_year desc
                """, this::mapSeason, playerId);
    }

    public List<SeasonResponse> teamSeasons(Long teamId) {
        return jdbcTemplate.query("""
                select g.season_start_year, count(*) as game_count, max(g.game_date) as most_recent_game_date
                from team_game_stats s
                join games g on g.game_id = s.game_id
                where s.team_id = ? and g.season_start_year is not null
                group by g.season_start_year
                order by g.season_start_year desc
                """, this::mapSeason, teamId);
    }

    private SeasonResponse mapSeason(ResultSet rs, int rowNum) throws SQLException {
        return SeasonResponse.of(
                rs.getInt("season_start_year"),
                rs.getLong("game_count"),
                nullableDate(rs, "most_recent_game_date"));
    }

    private static LocalDate nullableDate(ResultSet rs, String column) throws SQLException {
        var date = rs.getDate(column);
        return date == null ? null : date.toLocalDate();
    }
}
