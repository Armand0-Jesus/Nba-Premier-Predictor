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
}
