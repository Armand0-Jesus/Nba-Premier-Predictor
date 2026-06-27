package com.armandorodriguez.nba_premier_predictor.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest
@Sql(scripts = {"/test-cleanup.sql", "/test-feature-data.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class FeatureGenerationIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void generatesLeakageSafePlayerFeatureSnapshots() throws Exception {
        mockMvc.perform(post("/api/features/player-snapshots/generate").param("season", "2023"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.featureType").value("player"))
                .andExpect(jsonPath("$.seasonStartYear").value(2023))
                .andExpect(jsonPath("$.snapshotsGenerated").value(3));

        String featuresJson = jdbcTemplate.queryForObject("""
                select features
                from player_feature_snapshots
                where game_id = ? and player_id = ?
                """, String.class, 22300003L, 201939L);
        Map<String, Object> features = objectMapper.readValue(featuresJson, new TypeReference<>() {
        });

        assertThat(features)
                .containsEntry("games_played_prior", 2)
                .containsEntry("age_at_game", 35)
                .containsEntry("age_entering_season", 35)
                .containsEntry("years_experience_at_game", 14)
                .containsEntry("career_stage", "late_career")
                .containsEntry("career_games_played_before_game", 2)
                .containsEntry("career_minutes_played_before_game", 62.0)
                .containsEntry("projected_starter", true)
                .containsEntry("player_changed_team_before_game", false)
                .containsEntry("same_position_competition", 0)
                .containsEntry("team_missing_starters_count", 1)
                .containsEntry("team_roster_turnover_score", 0.13)
                .containsEntry("team_minutes_vacated_by_departures", 26.0)
                .containsEntry("team_usage_vacated_by_departures", 0.24)
                .containsEntry("teammate_injury_usage_boost", 0.08)
                .containsEntry("teammate_injury_minutes_boost", 2.5)
                .containsEntry("last_3_points_avg", 15.0)
                .containsEntry("season_points_avg", 15.0)
                .containsEntry("recent_minutes_trend", null)
                .containsEntry("injury_history_count_before_game", 1)
                .containsEntry("fantasy_volatility_score", 7.2)
                .containsEntry("rest_management_risk", 0.23)
                .containsEntry("days_rest", 2)
                .containsEntry("back_to_back", false)
                .containsEntry("is_home", true)
                .containsEntry("opponent_points_allowed_avg", 105.0);
    }

    @Test
    void returnsLatestPlayerFeatureSnapshotWithoutTargets() throws Exception {
        mockMvc.perform(post("/api/features/player-snapshots/generate").param("season", "2023"))
                .andExpect(status().isOk());

        String responseJson = mockMvc.perform(get("/api/features/player-snapshots/latest")
                        .param("gameId", "22300003")
                        .param("playerId", "201939"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.snapshotType").value("player"))
                .andExpect(jsonPath("$.gameId").value(22300003))
                .andExpect(jsonPath("$.playerId").value(201939))
                .andExpect(jsonPath("$.teamId").value(1610612744))
                .andExpect(jsonPath("$.homeTeamId").value(1610612744))
                .andExpect(jsonPath("$.awayTeamId").value(1610612747))
                .andExpect(jsonPath("$.dataCutoffTime").exists())
                .andExpect(jsonPath("$.features.games_played_prior").value(2))
                .andReturn()
                .getResponse()
                .getContentAsString();

        Map<String, Object> response = objectMapper.readValue(responseJson, new TypeReference<>() {
        });
        Map<String, Object> features = nestedMap(response, "features");
        assertThat(features)
                .containsEntry("age_at_game", 35)
                .containsEntry("career_stage", "late_career")
                .doesNotContainKeys("points", "rebounds", "assists", "fantasyPoints", "targets");
    }

    @Test
    void returnsLatestGameFeatureSnapshotWithoutTargets() throws Exception {
        mockMvc.perform(post("/api/features/game-snapshots/generate").param("season", "2023"))
                .andExpect(status().isOk());

        String responseJson = mockMvc.perform(get("/api/features/game-snapshots/latest")
                        .param("gameId", "22300003"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.snapshotType").value("game"))
                .andExpect(jsonPath("$.gameId").value(22300003))
                .andExpect(jsonPath("$.homeTeamId").value(1610612744))
                .andExpect(jsonPath("$.awayTeamId").value(1610612747))
                .andExpect(jsonPath("$.dataCutoffTime").exists())
                .andExpect(jsonPath("$.features.home_team_id").value(1610612744))
                .andReturn()
                .getResponse()
                .getContentAsString();

        Map<String, Object> response = objectMapper.readValue(responseJson, new TypeReference<>() {
        });
        Map<String, Object> features = nestedMap(response, "features");
        assertThat(features)
                .containsEntry("season_point_differential_delta", 15.0)
                .doesNotContainKeys("homeScore", "awayScore", "winnerTeamId", "pointDifferential", "targets");
    }

    @Test
    void latestFeatureSnapshotEndpointsReturnNotFoundForMissingSnapshots() throws Exception {
        mockMvc.perform(get("/api/features/player-snapshots/latest")
                        .param("gameId", "22300003")
                        .param("playerId", "201939"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Player feature snapshot not found for game 22300003 and player 201939"));

        mockMvc.perform(get("/api/features/game-snapshots/latest")
                        .param("gameId", "22300003"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Game feature snapshot not found for game 22300003"));
    }

    @Test
    void ensurePlayerFeatureSnapshotGeneratesMissingSeasonSnapshot() throws Exception {
        String responseJson = mockMvc.perform(post("/api/features/player-snapshots/ensure")
                        .param("gameId", "22300003")
                        .param("playerId", "201939"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.snapshotType").value("player"))
                .andExpect(jsonPath("$.gameId").value(22300003))
                .andExpect(jsonPath("$.playerId").value(201939))
                .andExpect(jsonPath("$.features.games_played_prior").value(2))
                .andReturn()
                .getResponse()
                .getContentAsString();

        Map<String, Object> response = objectMapper.readValue(responseJson, new TypeReference<>() {
        });
        assertThat(nestedMap(response, "features"))
                .containsEntry("age_at_game", 35)
                .doesNotContainKeys("points", "rebounds", "assists", "targets");
    }

    @Test
    void ensureGameFeatureSnapshotGeneratesMissingSeasonSnapshot() throws Exception {
        String responseJson = mockMvc.perform(post("/api/features/game-snapshots/ensure")
                        .param("gameId", "22300003"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.snapshotType").value("game"))
                .andExpect(jsonPath("$.gameId").value(22300003))
                .andExpect(jsonPath("$.features.home_team_id").value(1610612744))
                .andReturn()
                .getResponse()
                .getContentAsString();

        Map<String, Object> response = objectMapper.readValue(responseJson, new TypeReference<>() {
        });
        assertThat(nestedMap(response, "features"))
                .containsEntry("season_point_differential_delta", 15.0)
                .doesNotContainKeys("homeScore", "awayScore", "targets");
    }

    @Test
    void generatesLeakageSafeTeamFeatureSnapshots() throws Exception {
        mockMvc.perform(post("/api/features/team-snapshots/generate").param("season", "2023"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.featureType").value("team"))
                .andExpect(jsonPath("$.seasonStartYear").value(2023))
                .andExpect(jsonPath("$.snapshotsGenerated").value(6));

        String featuresJson = jdbcTemplate.queryForObject("""
                select features
                from team_feature_snapshots
                where game_id = ? and team_id = ?
                """, String.class, 22300003L, 1610612744L);
        Map<String, Object> features = objectMapper.readValue(featuresJson, new TypeReference<>() {
        });

        assertThat(features)
                .containsEntry("games_played_prior", 2)
                .containsEntry("average_team_age", 34.0)
                .containsEntry("starter_average_age", 34.0)
                .containsEntry("rotation_average_age", 34.0)
                .containsEntry("young_team_flag", false)
                .containsEntry("veteran_team_flag", true)
                .containsEntry("team_roster_turnover_score", 0.13)
                .containsEntry("team_minutes_vacated_by_departures", 26.0)
                .containsEntry("team_usage_vacated_by_departures", 0.24)
                .containsEntry("team_missing_starters_count", 1)
                .containsEntry("projected_starters_available_count", 1)
                .containsEntry("last_3_team_score_avg", 105.0)
                .containsEntry("season_point_differential_avg", 7.5)
                .containsEntry("days_rest", 2)
                .containsEntry("back_to_back", false)
                .containsEntry("is_home", true)
                .containsEntry("opponent_points_allowed_avg", 105.0);
    }

    @Test
    void generatesLeakageSafeGameFeatureSnapshots() throws Exception {
        mockMvc.perform(post("/api/features/game-snapshots/generate").param("season", "2023"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.featureType").value("game"))
                .andExpect(jsonPath("$.seasonStartYear").value(2023))
                .andExpect(jsonPath("$.snapshotsGenerated").value(3));

        String featuresJson = jdbcTemplate.queryForObject("""
                select features
                from game_feature_snapshots
                where game_id = ?
                """, String.class, 22300003L);
        Map<String, Object> features = objectMapper.readValue(featuresJson, new TypeReference<>() {
        });

        assertThat(((Number) features.get("home_team_id")).longValue()).isEqualTo(1610612744L);
        assertThat(((Number) features.get("away_team_id")).longValue()).isEqualTo(1610612747L);
        assertThat(features)
                .containsEntry("home_last_3_team_score_avg", 105.0)
                .containsEntry("home_average_team_age", 34.0)
                .containsEntry("home_team_missing_starters_count", 1)
                .containsEntry("home_team_roster_turnover_score", 0.13)
                .containsEntry("home_team_minutes_vacated_by_departures", 26.0)
                .containsEntry("home_team_usage_vacated_by_departures", 0.24)
                .containsEntry("away_average_team_age", 39.0)
                .containsEntry("away_last_3_team_score_avg", 97.5)
                .containsEntry("season_point_differential_delta", 15.0)
                .containsEntry("last_5_point_differential_delta", 15.0);
    }

    @Test
    void populatesAllFeatureSnapshotTablesForASeason() throws Exception {
        mockMvc.perform(post("/api/features/player-snapshots/generate").param("season", "2023"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.snapshotsGenerated").value(3));
        mockMvc.perform(post("/api/features/team-snapshots/generate").param("season", "2023"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.snapshotsGenerated").value(6));
        mockMvc.perform(post("/api/features/game-snapshots/generate").param("season", "2023"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.snapshotsGenerated").value(3));

        assertThat(countRows("player_feature_snapshots")).isEqualTo(3);
        assertThat(countRows("team_feature_snapshots")).isEqualTo(6);
        assertThat(countRows("game_feature_snapshots")).isEqualTo(3);

        Integer cutoffViolations = jdbcTemplate.queryForObject("""
                select count(*)
                from player_feature_snapshots f
                join games g on g.game_id = f.game_id
                where f.data_cutoff_time >= g.game_date_time_est
                """, Integer.class);
        assertThat(cutoffViolations).isZero();
    }

    private Integer countRows(String tableName) {
        return jdbcTemplate.queryForObject("select count(*) from " + tableName, Integer.class);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> nestedMap(Map<String, Object> row, String key) {
        return (Map<String, Object>) row.get(key);
    }
}
