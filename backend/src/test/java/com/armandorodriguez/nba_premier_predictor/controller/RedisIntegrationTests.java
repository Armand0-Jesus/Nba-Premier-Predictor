package com.armandorodriguez.nba_premier_predictor.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import com.armandorodriguez.nba_premier_predictor.dto.PlayerPredictionRequest;
import com.armandorodriguez.nba_premier_predictor.dto.PlayerPredictionResponse;
import com.armandorodriguez.nba_premier_predictor.dto.TeamScorePredictionRequest;
import com.armandorodriguez.nba_premier_predictor.dto.TeamScorePredictionResponse;
import com.armandorodriguez.nba_premier_predictor.service.MlPredictionClient;

@ActiveProfiles("test")
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "REDIS_INTEGRATION_TESTS", matches = "true")
@SpringBootTest(properties = {
        "spring.cache.type=redis",
        "spring.data.redis.host=localhost",
        "spring.data.redis.port=6379",
        "app.rate-limit.enabled=true",
        "app.rate-limit.trust-forwarded-headers=true",
        "app.rate-limit.predictions-per-minute=2",
        "app.rate-limit.window=1m",
        "app.prediction-cache.enabled=true",
        "app.search-cache.enabled=true"
})
@Sql(scripts = {"/test-cleanup.sql", "/test-data.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class RedisIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StubMlPredictionClient mlPredictionClient;

    @BeforeEach
    void clearRedis() {
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();
        mlPredictionClient.reset();
    }

    @Test
    void playerDetailCacheWritesToRedis() throws Exception {
        mockMvc.perform(get("/api/players/201939"))
                .andExpect(status().isOk());

        assertThat(redisTemplate.hasKey("playerDetails::201939")).isTrue();
    }

    @Test
    void playerSearchCacheWritesToRedisAndCanBeReadBack() throws Exception {
        mockMvc.perform(get("/api/players")
                        .param("query", "Stephen")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/players")
                        .param("query", "Stephen")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk());

        assertThat(redisTemplate.keys("playerSearch:*")).isNotEmpty();
    }

    @Test
    void repeatedIdenticalPredictionUsesFingerprintCacheWithoutDuplicateHistory() throws Exception {
        mockMvc.perform(post("/api/predictions/player")
                        .header("X-Forwarded-For", "10.0.0.9")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(playerRequestJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.predictionId").isNumber());

        mockMvc.perform(post("/api/predictions/player")
                        .header("X-Forwarded-For", "10.0.0.9")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(playerRequestJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.predictionId").isNumber());

        assertThat(mlPredictionClient.playerPredictionCalls()).isEqualTo(1);
        assertThat(countRows("predictions")).isEqualTo(1);
        assertThat(countRows("player_stat_predictions")).isEqualTo(1);
        assertThat(redisTemplate.keys("prediction:fingerprint:*")).hasSize(1);
    }

    @Test
    void predictionRateLimitUsesRedisWindow() throws Exception {
        mockMvc.perform(post("/api/predictions/player")
                        .header("X-Forwarded-For", "10.0.0.8")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(playerRequestJson()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/predictions/player")
                        .header("X-Forwarded-For", "10.0.0.8")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(playerRequestJson()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/predictions/player")
                        .header("X-Forwarded-For", "10.0.0.8")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(playerRequestJson()))
                .andExpect(status().isTooManyRequests());

        assertThat(redisTemplate.getExpire("rate_limit:predictions:10.0.0.8")).isPositive();
    }

    private Integer countRows(String tableName) {
        return jdbcTemplate.queryForObject("select count(*) from " + tableName, Integer.class);
    }

    private static String playerRequestJson() {
        return """
                {
                  "gameId": 12300001,
                  "playerId": 201939,
                  "teamId": 1610612744,
                  "dataCutoffTime": "2024-01-15T21:59:59",
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
        StubMlPredictionClient mlPredictionClient() {
            return new StubMlPredictionClient();
        }
    }

    static class StubMlPredictionClient implements MlPredictionClient {

        private final AtomicInteger playerPredictionCalls = new AtomicInteger();

        @Override
        public PlayerPredictionResponse predictPlayer(PlayerPredictionRequest request) {
            playerPredictionCalls.incrementAndGet();
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
            return Map.of("modelVersion", "player-baseline-v1", "trainedRows", 30034);
        }

        @Override
        public Map<String, Object> modelVersions() {
            return Map.of(
                    "activeModel", Map.of(
                            "versionName", "player-baseline-v1",
                            "modelType", "ridge-regression"),
                    "gameScoreModel", Map.of(
                            "versionName", "game-score-baseline-v1",
                            "modelType", "ridge-regression"));
        }

        void reset() {
            playerPredictionCalls.set(0);
        }

        int playerPredictionCalls() {
            return playerPredictionCalls.get();
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
