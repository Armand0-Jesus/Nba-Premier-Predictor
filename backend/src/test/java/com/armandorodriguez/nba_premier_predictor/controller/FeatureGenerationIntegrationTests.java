package com.armandorodriguez.nba_premier_predictor.controller;

import static org.assertj.core.api.Assertions.assertThat;
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
                .containsEntry("last_3_points_avg", 15.0)
                .containsEntry("season_points_avg", 15.0)
                .containsEntry("days_rest", 2)
                .containsEntry("back_to_back", false)
                .containsEntry("is_home", true)
                .containsEntry("opponent_points_allowed_avg", 105.0);
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
}
