package com.armandorodriguez.nba_premier_predictor.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest
@Sql(scripts = {"/test-cleanup.sql", "/test-feature-data.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class TrainingDataIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void returnsPlayerTrainingRowsFromFeatureSnapshotsAndActuals() throws Exception {
        mockMvc.perform(post("/api/features/player-snapshots/generate").param("season", "2023"))
                .andExpect(status().isOk());

        String responseJson = mockMvc.perform(get("/api/training-data/player-stats")
                        .param("season", "2023")
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        List<Map<String, Object>> rows = objectMapper.readValue(responseJson, new TypeReference<>() {
        });

        assertThat(rows).hasSize(3);
        Map<String, Object> lastRow = rows.get(2);
        assertThat(lastRow)
                .containsEntry("gameId", 22300003)
                .containsEntry("playerId", 201939);

        Map<String, Object> features = nestedMap(lastRow, "features");
        Map<String, Object> targets = nestedMap(lastRow, "targets");
        assertThat(features)
                .containsEntry("games_played_prior", 2)
                .containsEntry("last_3_points_avg", 15.0);
        assertThat(targets)
                .containsEntry("points", 999)
                .containsEntry("rebounds", 99)
                .containsEntry("assists", 99)
                .containsEntry("fantasyPoints", 1266.3);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> nestedMap(Map<String, Object> row, String key) {
        return (Map<String, Object>) row.get(key);
    }
}
