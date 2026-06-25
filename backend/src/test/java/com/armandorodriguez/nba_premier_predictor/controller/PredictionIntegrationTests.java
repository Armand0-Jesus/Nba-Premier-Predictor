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
import com.armandorodriguez.nba_premier_predictor.dto.TeamScorePredictionRequest;
import com.armandorodriguez.nba_premier_predictor.dto.TeamScorePredictionResponse;
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
    void predictsGameScoreThroughMlServiceAndStoresTeamScoreHistory() throws Exception {
        mockMvc.perform(post("/api/predictions/game-score")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(gameScoreRequestJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.predictionId").isNumber())
                .andExpect(jsonPath("$.modelVersion").value("game-score-baseline-v1"))
                .andExpect(jsonPath("$.homeTeamScore").value(116.5))
                .andExpect(jsonPath("$.awayTeamScore").value(108.2))
                .andExpect(jsonPath("$.predictedWinnerTeamId").value(1610612744))
                .andExpect(jsonPath("$.pointDifferential").value(8.3));

        assertThat(countRows("predictions")).isEqualTo(1);
        assertThat(countRows("team_score_predictions")).isEqualTo(1);
        assertThat(countRows("model_versions")).isEqualTo(1);

        mockMvc.perform(get("/api/predictions/history").param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].predictionType").value("game_score"))
                .andExpect(jsonPath("$[0].homeTeamScore").value(116.5))
                .andExpect(jsonPath("$[0].awayTeamScore").value(108.2))
                .andExpect(jsonPath("$[0].predictedWinnerTeamId").value(1610612744))
                .andExpect(jsonPath("$[0].pointDifferential").value(8.3))
                .andExpect(jsonPath("$[0].modelVersion").value("game-score-baseline-v1"));
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
    void rejectsGameScorePredictionRequestsWithoutTeams() throws Exception {
        mockMvc.perform(post("/api/predictions/game-score")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "gameId": 22300003,
                                  "homeTeamId": 1610612744,
                                  "features": {
                                    "home_last_5_team_score_avg": 105.0
                                  }
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

    private static String gameScoreRequestJson() {
        return """
                {
                  "gameId": 22300003,
                  "homeTeamId": 1610612744,
                  "awayTeamId": 1610612747,
                  "dataCutoffTime": "2024-01-05T21:59:59",
                  "features": {
                    "home_games_played_prior": 2,
                    "away_games_played_prior": 2,
                    "home_last_5_team_score_avg": 105.0,
                    "away_last_5_team_score_avg": 97.5,
                    "season_point_differential_delta": 15.0
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
        public TeamScorePredictionResponse predictGameScore(TeamScorePredictionRequest request) {
            return new TeamScorePredictionResponse(
                    null,
                    "game-score-baseline-v1",
                    1200,
                    request.gameId(),
                    request.homeTeamId(),
                    request.awayTeamId(),
                    116.5,
                    108.2,
                    request.homeTeamId(),
                    8.3,
                    0.72,
                    List.of(Map.of("name", "season_point_differential_delta", "value", 15.0)));
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
