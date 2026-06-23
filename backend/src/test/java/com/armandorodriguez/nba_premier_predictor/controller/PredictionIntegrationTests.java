package com.armandorodriguez.nba_premier_predictor.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

import com.armandorodriguez.nba_premier_predictor.dto.PlayerPredictionRequest;
import com.armandorodriguez.nba_premier_predictor.dto.PlayerPredictionResponse;
import com.armandorodriguez.nba_premier_predictor.service.MlPredictionClient;

@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest
@Sql(scripts = {"/test-cleanup.sql", "/test-feature-data.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class PredictionIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void predictsPlayerStatsThroughMlServiceAndStoresHistory() throws Exception {
        mockMvc.perform(post("/api/predictions/player")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(playerRequestJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.predictionId").isNumber())
                .andExpect(jsonPath("$.modelVersion").value("player-baseline-v1"))
                .andExpect(jsonPath("$.projectedPoints").value(18.5))
                .andExpect(jsonPath("$.fantasyPoints").value(35.4));

        assertThat(countRows("predictions")).isEqualTo(1);
        assertThat(countRows("player_stat_predictions")).isEqualTo(1);
        assertThat(countRows("model_versions")).isEqualTo(1);

        mockMvc.perform(get("/api/predictions/history").param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].predictionType").value("player_stat"))
                .andExpect(jsonPath("$[0].projectedPoints").value(18.5))
                .andExpect(jsonPath("$[0].modelVersion").value("player-baseline-v1"));
    }

    @Test
    void predictsFantasyThroughMlServiceAndStoresFantasyHistory() throws Exception {
        mockMvc.perform(post("/api/predictions/fantasy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(playerRequestJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.predictionId").isNumber())
                .andExpect(jsonPath("$.fantasyPoints").value(35.4))
                .andExpect(jsonPath("$.riskLevel").value("medium"));

        assertThat(countRows("predictions")).isEqualTo(1);
        assertThat(countRows("fantasy_predictions")).isEqualTo(1);

        mockMvc.perform(get("/api/predictions/history").param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].predictionType").value("fantasy"))
                .andExpect(jsonPath("$[0].fantasyPoints").value(35.4))
                .andExpect(jsonPath("$[0].riskLevel").value("medium"));
    }

    @Test
    void rejectsPredictionRequestsWithoutFeatures() throws Exception {
        mockMvc.perform(post("/api/predictions/player")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "gameId": 22300003,
                                  "playerId": 201939,
                                  "teamId": 1610612744,
                                  "features": {}
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void exposesModelMetricsAndVersionsThroughBackend() throws Exception {
        mockMvc.perform(get("/api/model/metrics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modelVersion").value("player-baseline-v1"))
                .andExpect(jsonPath("$.trainedRows").value(30034));

        mockMvc.perform(get("/api/model/versions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeModel.versionName").value("player-baseline-v1"))
                .andExpect(jsonPath("$.activeModel.modelType").value("ridge-regression"));
    }

    private Integer countRows(String tableName) {
        return jdbcTemplate.queryForObject("select count(*) from " + tableName, Integer.class);
    }

    private static String playerRequestJson() {
        return """
                {
                  "gameId": 22300003,
                  "playerId": 201939,
                  "teamId": 1610612744,
                  "dataCutoffTime": "2024-01-05T21:59:59",
                  "features": {
                    "games_played_prior": 2,
                    "last_5_points_avg": 15.0,
                    "last_5_rebounds_avg": 6.0,
                    "last_5_assists_avg": 5.0,
                    "last_5_minutes_avg": 31.0
                  }
                }
                """;
    }

    @TestConfiguration
    static class StubMlClientConfig {

        @Bean
        @Primary
        MlPredictionClient mlPredictionClient() {
            return new StubMlPredictionClient();
        }
    }

    static class StubMlPredictionClient implements MlPredictionClient {

        @Override
        public PlayerPredictionResponse predictPlayer(PlayerPredictionRequest request) {
            return prediction(request);
        }

        @Override
        public PlayerPredictionResponse predictFantasy(PlayerPredictionRequest request) {
            return prediction(request);
        }

        @Override
        public Map<String, Object> modelMetrics() {
            return Map.of(
                    "modelVersion", "player-baseline-v1",
                    "trainedRows", 30034);
        }

        @Override
        public Map<String, Object> modelVersions() {
            return Map.of("activeModel", Map.of(
                    "versionName", "player-baseline-v1",
                    "modelType", "ridge-regression"));
        }

        private static PlayerPredictionResponse prediction(PlayerPredictionRequest request) {
            return new PlayerPredictionResponse(
                    null,
                    "player-baseline-v1",
                    30034,
                    request.gameId(),
                    request.playerId(),
                    request.teamId(),
                    18.5,
                    6.2,
                    5.4,
                    31.0,
                    35.4,
                    30.1,
                    40.7,
                    0.76,
                    "medium",
                    List.of(Map.of("name", "last_5_points_avg", "value", 15.0)));
        }
    }
}
