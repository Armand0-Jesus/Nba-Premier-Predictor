package com.armandorodriguez.nba_premier_predictor.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
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

import com.armandorodriguez.nba_premier_predictor.dto.ModelRetrainRequest;
import com.armandorodriguez.nba_premier_predictor.dto.PlayerPredictionRequest;
import com.armandorodriguez.nba_premier_predictor.dto.PlayerPredictionResponse;
import com.armandorodriguez.nba_premier_predictor.dto.TeamScorePredictionRequest;
import com.armandorodriguez.nba_premier_predictor.dto.TeamScorePredictionResponse;
import com.armandorodriguez.nba_premier_predictor.exception.MlServiceException;
import com.armandorodriguez.nba_premier_predictor.service.MlPredictionClient;
import com.armandorodriguez.nba_premier_predictor.service.PredictionExportService;

@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest
@Sql(scripts = {"/test-cleanup.sql", "/test-feature-data.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class PredictionIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetStub() {
        StubMlPredictionClient.reset();
        StubPredictionExportService.failExports = false;
    }

    @Test
    void predictsPlayerStatsThroughMlServiceAndStoresHistory() throws Exception {
        mockMvc.perform(post("/api/predictions/player")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(playerRequestJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.predictionId").isNumber())
                .andExpect(jsonPath("$.modelVersion").value("player-baseline-v2"))
                .andExpect(jsonPath("$.projectedPoints").value(18.5))
                .andExpect(jsonPath("$.fantasyPoints").value(35.4));

        assertThat(countRows("predictions")).isEqualTo(1);
        assertThat(countRows("player_stat_predictions")).isEqualTo(1);
        assertThat(countRows("model_versions")).isEqualTo(1);

        mockMvc.perform(get("/api/predictions/history").param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].predictionType").value("player_stat"))
                .andExpect(jsonPath("$[0].projectedPoints").value(18.5))
                .andExpect(jsonPath("$[0].modelVersion").value("player-baseline-v2"));
    }

    @Test
    void predictionFlowStillSucceedsWhenCloudExportFails() throws Exception {
        StubPredictionExportService.failExports = true;

        mockMvc.perform(post("/api/predictions/player")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(playerRequestJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.predictionId").isNumber())
                .andExpect(jsonPath("$.projectedPoints").value(18.5));

        assertThat(countRows("predictions")).isEqualTo(1);
        assertThat(countRows("player_stat_predictions")).isEqualTo(1);
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
                .andExpect(jsonPath("$[0].homeTeamId").value(1610612744))
                .andExpect(jsonPath("$[0].awayTeamId").value(1610612747))
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
                .andExpect(jsonPath("$.modelVersion").value("player-baseline-v2"))
                .andExpect(jsonPath("$.trainedRows").value(30034));

        mockMvc.perform(get("/api/model/versions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeModel.versionName").value("player-baseline-v2"))
                .andExpect(jsonPath("$.activeModel.modelType").value("ridge-regression"));
    }

    @Test
    void modelEvaluationEndpointReturnsHitRateMetrics() throws Exception {
        mockMvc.perform(post("/api/model/evaluate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.playerBaseline.metrics.projected_points.hitRate").value(0.72))
                .andExpect(jsonPath("$.playerBaseline.metrics.projected_points.hitThreshold").value(5))
                .andExpect(jsonPath("$.gameScoreBaseline.metrics.home_team_score.hitRate").value(0.64))
                .andExpect(jsonPath("$.gameScoreBaseline.metrics.home_team_score.hitThreshold").value(10));
    }

    @Test
    void retrainRegistersCandidateModelsAndPromotesWhenNoActiveMetricsExist() throws Exception {
        mockMvc.perform(post("/api/model/retrain")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "startSeason": 2023,
                                  "endSeason": 2024,
                                  "limit": 100,
                                  "triggeredBy": "test"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("completed"))
                .andExpect(jsonPath("$.playerCandidate.promoted").value(true))
                .andExpect(jsonPath("$.playerCandidate.validationSampleSize").value(80))
                .andExpect(jsonPath("$.gameScoreCandidate.promoted").value(true))
                .andExpect(jsonPath("$.gameScoreCandidate.validationSampleSize").value(80));

        assertThat(countRows("model_training_runs")).isEqualTo(1);
        assertThat(countRows("model_versions")).isEqualTo(2);
        assertThat(countRows("model_metrics")).isEqualTo(2);
        assertThat(countRows("model_registry")).isEqualTo(2);
        assertThat(countRows("model_promotion_history")).isEqualTo(2);
        assertThat(activeModelCount("player_stat_fantasy")).isEqualTo(1);
        assertThat(activeModelCount("game_score")).isEqualTo(1);
        assertThat(registryStatusCount("active")).isEqualTo(2);

        mockMvc.perform(get("/api/model/versions/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].active").value(true));

        mockMvc.perform(get("/api/model/training-runs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("completed"))
                .andExpect(jsonPath("$[0].triggeredBy").value("test"));

        mockMvc.perform(get("/api/model/promotion-history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].promoted").value(true));
    }

    @Test
    void promotionArchivesPreviousRegistryRowsAndRollbackRestoresOneActiveModel() throws Exception {
        mockMvc.perform(post("/api/model/retrain")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "startSeason": 2023,
                                  "endSeason": 2024,
                                  "limit": 100,
                                  "triggeredBy": "test"
                                }
                                """))
                .andExpect(status().isOk());

        Long originalActiveId = activeModelVersionId("player_stat_fantasy");
        Long candidateId = insertModelVersion(
                "player-baseline-v2-better-test",
                "player_stat_fantasy",
                "candidate",
                false);
        insertMetric(candidateId, "projected_points", 1.2, 1.8, 100);
        insertRegistry(candidateId, "candidate");

        mockMvc.perform(post("/api/model/promote/{modelVersionId}", candidateId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.promoted").value(true))
                .andExpect(jsonPath("$.modelVersion.id").value(candidateId));

        assertThat(activeModelVersionId("player_stat_fantasy")).isEqualTo(candidateId);
        assertThat(activeModelCount("player_stat_fantasy")).isEqualTo(1);
        assertThat(modelStatus(originalActiveId)).isEqualTo("archived");
        assertThat(registryStatus(originalActiveId)).isEqualTo("archived");
        assertThat(registryArchivedAt(originalActiveId)).isNotNull();

        mockMvc.perform(post("/api/model/rollback/{modelVersionId}", originalActiveId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.promoted").value(true))
                .andExpect(jsonPath("$.modelVersion.id").value(originalActiveId));

        assertThat(activeModelVersionId("player_stat_fantasy")).isEqualTo(originalActiveId);
        assertThat(activeModelCount("player_stat_fantasy")).isEqualTo(1);
        assertThat(modelStatus(candidateId)).isEqualTo("archived");
        assertThat(registryStatus(candidateId)).isEqualTo("archived");
    }

    @Test
    void manualPromotionRequiresCandidateMetricsAndEnoughValidationRows() throws Exception {
        Long missingMetricsCandidateId = insertModelVersion(
                "player-baseline-v2-missing-metrics-test",
                "player_stat_fantasy",
                "candidate",
                false);

        mockMvc.perform(post("/api/model/promote/{modelVersionId}", missingMetricsCandidateId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Candidate evaluation metrics are required before promotion"));

        Long tinySampleCandidateId = insertModelVersion(
                "player-baseline-v2-tiny-sample-test",
                "player_stat_fantasy",
                "candidate",
                false);
        insertMetric(tinySampleCandidateId, "projected_points", 2.4, 3.1, 5);

        mockMvc.perform(post("/api/model/promote/{modelVersionId}", tinySampleCandidateId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Candidate validation sample size is too small for promotion"));

        Long missingSampleCandidateId = insertModelVersion(
                "player-baseline-v2-missing-sample-test",
                "player_stat_fantasy",
                "candidate",
                false);
        insertMetricWithoutSample(missingSampleCandidateId, "projected_points", 2.2, 3.0);

        mockMvc.perform(post("/api/model/promote/{modelVersionId}", missingSampleCandidateId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Candidate validation sample size is required for promotion"));

        assertThat(activeModelCount("player_stat_fantasy")).isZero();
    }

    @Test
    void retrainRejectsCandidatesWhenEvaluationMetricsAreMissing() throws Exception {
        StubMlPredictionClient.missingMetrics = true;

        mockMvc.perform(post("/api/model/retrain")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "startSeason": 2023,
                                  "endSeason": 2024,
                                  "limit": 100,
                                  "triggeredBy": "test"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.playerCandidate.promoted").value(false))
                .andExpect(jsonPath("$.playerCandidate.reason").value("Candidate evaluation metrics were missing"))
                .andExpect(jsonPath("$.gameScoreCandidate.promoted").value(false))
                .andExpect(jsonPath("$.gameScoreCandidate.reason").value("Candidate evaluation metrics were missing"));

        assertThat(activeModelCount("player_stat_fantasy")).isZero();
        assertThat(activeModelCount("game_score")).isZero();
        assertThat(registryStatusCount("rejected")).isEqualTo(2);
        assertThat(registryStatusCount("active")).isZero();
        assertThat(countRows("model_promotion_history")).isEqualTo(2);
    }

    @Test
    void retrainRejectsCandidatesWhenValidationSampleIsTooSmall() throws Exception {
        StubMlPredictionClient.tinySample = true;

        mockMvc.perform(post("/api/model/retrain")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "startSeason": 2023,
                                  "endSeason": 2024,
                                  "limit": 100,
                                  "triggeredBy": "test"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.playerCandidate.promoted").value(false))
                .andExpect(jsonPath("$.playerCandidate.validationSampleSize").value(5))
                .andExpect(jsonPath("$.playerCandidate.reason").value("Candidate validation sample size was too small"))
                .andExpect(jsonPath("$.gameScoreCandidate.promoted").value(false))
                .andExpect(jsonPath("$.gameScoreCandidate.validationSampleSize").value(5));

        assertThat(activeModelCount("player_stat_fantasy")).isZero();
        assertThat(activeModelCount("game_score")).isZero();
        assertThat(registryStatusCount("rejected")).isEqualTo(2);
        assertThat(countRows("model_promotion_history")).isEqualTo(2);
    }

    @Test
    void retrainRejectsCandidatesWhenValidationSampleIsMissing() throws Exception {
        StubMlPredictionClient.missingSampleSize = true;

        mockMvc.perform(post("/api/model/retrain")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "startSeason": 2023,
                                  "endSeason": 2024,
                                  "limit": 100,
                                  "triggeredBy": "test"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.playerCandidate.promoted").value(false))
                .andExpect(jsonPath("$.playerCandidate.reason").value("Candidate validation sample size was missing"))
                .andExpect(jsonPath("$.gameScoreCandidate.promoted").value(false))
                .andExpect(jsonPath("$.gameScoreCandidate.reason").value("Candidate validation sample size was missing"));

        assertThat(activeModelCount("player_stat_fantasy")).isZero();
        assertThat(activeModelCount("game_score")).isZero();
        assertThat(registryStatusCount("rejected")).isEqualTo(2);
    }

    @Test
    void retrainReturnsClearErrorWhenTrainingDataIsTooSmall() throws Exception {
        StubMlPredictionClient.failTrainingForSmallData = true;

        mockMvc.perform(post("/api/model/retrain")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "startSeason": 2023,
                                  "endSeason": 2024,
                                  "limit": 100,
                                  "triggeredBy": "test"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Retraining cannot run because there is not enough training data for the selected range"));

        assertThat(countRows("model_training_runs")).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("select status from model_training_runs", String.class)).isEqualTo("failed");
        assertThat(jdbcTemplate.queryForObject("select notes from model_training_runs", String.class))
                .isEqualTo("Retraining cannot run because there is not enough training data for the selected range");
    }

    @Test
    void refreshesPredictionErrorsFromCompletedGamesWithoutDuplicates() throws Exception {
        mockMvc.perform(post("/api/predictions/player")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(playerRequestJson()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/predictions/fantasy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(playerRequestJson()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/predictions/game-score")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(gameScoreRequestJson()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/model/prediction-errors/refresh"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.insertedErrors").value(11))
                .andExpect(jsonPath("$.totalErrors").value(11));

        assertThat(countRows("prediction_errors")).isEqualTo(11);

        mockMvc.perform(post("/api/model/prediction-errors/refresh"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.insertedErrors").value(0))
                .andExpect(jsonPath("$.totalErrors").value(11));

        mockMvc.perform(get("/api/model/monitoring").param("limit", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalErrors").value(11))
                .andExpect(jsonPath("$.targetSummaries[0].targetVariable").isString())
                .andExpect(jsonPath("$.driftIndicators[0].status").value("needs_attention"))
                .andExpect(jsonPath("$.driftIndicators[0].watchThreshold").isNumber())
                .andExpect(jsonPath("$.recentErrors[0].absoluteError").isNumber());

        mockMvc.perform(get("/api/model/prediction-errors").param("limit", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].predictionId").isNumber())
                .andExpect(jsonPath("$[0].targetVariable").isString());
    }

    private Integer countRows(String tableName) {
        return jdbcTemplate.queryForObject("select count(*) from " + tableName, Integer.class);
    }

    private Integer activeModelCount(String targetVariable) {
        return jdbcTemplate.queryForObject(
                "select count(*) from model_versions where target_variable = ? and is_active = true",
                Integer.class,
                targetVariable);
    }

    private Long activeModelVersionId(String targetVariable) {
        return jdbcTemplate.queryForObject(
                "select id from model_versions where target_variable = ? and is_active = true",
                Long.class,
                targetVariable);
    }

    private Integer registryStatusCount(String status) {
        return jdbcTemplate.queryForObject(
                "select count(*) from model_registry where registry_status = ?",
                Integer.class,
                status);
    }

    private String modelStatus(Long modelVersionId) {
        return jdbcTemplate.queryForObject(
                "select status from model_versions where id = ?",
                String.class,
                modelVersionId);
    }

    private String registryStatus(Long modelVersionId) {
        return jdbcTemplate.queryForObject(
                "select registry_status from model_registry where model_version_id = ?",
                String.class,
                modelVersionId);
    }

    private Object registryArchivedAt(Long modelVersionId) {
        return jdbcTemplate.queryForObject(
                "select archived_at from model_registry where model_version_id = ?",
                Object.class,
                modelVersionId);
    }

    private Long insertModelVersion(String versionName, String targetVariable, String status, boolean active) {
        jdbcTemplate.update("""
                insert into model_versions (
                    version_name, model_type, target_variable, trained_at, artifact_path, status, is_active
                ) values (?, 'ridge-regression', ?, current_timestamp, ?, ?, ?)
                """,
                versionName,
                targetVariable,
                "artifacts/candidates/" + versionName + ".joblib",
                status,
                active);
        return jdbcTemplate.queryForObject(
                "select id from model_versions where version_name = ?",
                Long.class,
                versionName);
    }

    private void insertMetric(Long modelVersionId, String targetVariable, double mae, double rmse, int sampleSize) {
        jdbcTemplate.update("""
                insert into model_metrics (
                    model_version_id, target_variable, mae, rmse, validation_sample_size
                ) values (?, ?, ?, ?, ?)
                """, modelVersionId, targetVariable, mae, rmse, sampleSize);
    }

    private void insertMetricWithoutSample(Long modelVersionId, String targetVariable, double mae, double rmse) {
        jdbcTemplate.update("""
                insert into model_metrics (
                    model_version_id, target_variable, mae, rmse
                ) values (?, ?, ?, ?)
                """, modelVersionId, targetVariable, mae, rmse);
    }

    private void insertRegistry(Long modelVersionId, String status) {
        jdbcTemplate.update(
                "insert into model_registry (model_version_id, registry_status) values (?, ?)",
                modelVersionId,
                status);
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

        @Bean
        @Primary
        PredictionExportService predictionExportService() {
            return new StubPredictionExportService();
        }
    }

    static class StubPredictionExportService extends PredictionExportService {

        static boolean failExports;

        StubPredictionExportService() {
            super(null);
        }

        @Override
        public void exportPlayerPrediction(
                String predictionType,
                PlayerPredictionRequest request,
                PlayerPredictionResponse response) {
            if (failExports) {
                throw new IllegalStateException("S3 export failed");
            }
        }

        @Override
        public void exportGameScorePrediction(
                TeamScorePredictionRequest request,
                TeamScorePredictionResponse response) {
            if (failExports) {
                throw new IllegalStateException("S3 export failed");
            }
        }
    }

    static class StubMlPredictionClient implements MlPredictionClient {

        static boolean missingMetrics;
        static boolean tinySample;
        static boolean missingSampleSize;
        static boolean failTrainingForSmallData;

        static void reset() {
            missingMetrics = false;
            tinySample = false;
            missingSampleSize = false;
            failTrainingForSmallData = false;
        }

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
                    "modelVersion", "player-baseline-v2",
                    "trainedRows", 30034);
        }

        @Override
        public Map<String, Object> modelVersions() {
            return Map.of("activeModel", Map.of(
                    "versionName", "player-baseline-v2",
                    "modelType", "ridge-regression"));
        }

        @Override
        public Map<String, Object> evaluateModels() {
            if (missingMetrics) {
                return Map.of(
                        "modelVersion", "player-baseline-v2",
                        "trainedRows", 30034,
                        "playerBaseline", Map.of("testRows", 80, "metrics", Map.of()),
                        "gameScoreBaseline", Map.of("testRows", 80, "metrics", Map.of()));
            }
            int testRows = tinySample ? 5 : 80;
            return Map.of(
                    "modelVersion", "player-baseline-v2",
                    "trainedRows", 30034,
                    "playerBaseline", baselineSection(testRows, Map.of(
                            "projected_points", Map.of("mae", 4.5, "rmse", 6.1, "hitRate", 0.72, "hitThreshold", 5))),
                    "gameScoreBaseline", baselineSection(testRows, Map.of(
                            "home_team_score", Map.of("mae", 9.5, "rmse", 12.4, "hitRate", 0.64, "hitThreshold", 10))));
        }

        @Override
        public Map<String, Object> evaluateModels(ModelRetrainRequest request) {
            return evaluateModels();
        }

        @Override
        public Map<String, Object> trainPlayerModel(ModelRetrainRequest request, String versionName, boolean activate) {
            if (failTrainingForSmallData) {
                throw new MlServiceException("No complete player training rows were available");
            }
            return Map.of(
                    "model_version", versionName,
                    "trained_rows", 100,
                    "artifact_path", "artifacts/candidates/" + versionName + ".joblib");
        }

        @Override
        public Map<String, Object> trainGameScoreModel(ModelRetrainRequest request, String versionName, boolean activate) {
            return Map.of(
                    "model_version", versionName,
                    "trained_rows", 50,
                    "artifact_path", "artifacts/candidates/" + versionName + ".joblib");
        }

        @Override
        public Map<String, Object> promoteModel(String modelType, String artifactPath) {
            return Map.of(
                    "model_type", modelType,
                    "artifact_path", artifactPath);
        }

        private static PlayerPredictionResponse prediction(PlayerPredictionRequest request) {
            return new PlayerPredictionResponse(
                    null,
                    "player-baseline-v2",
                    30034,
                    request.gameId(),
                    request.playerId(),
                    request.teamId(),
                    18.5,
                    6.2,
                    5.4,
                    31.0,
                    1.3,
                    0.4,
                    2.1,
                    7.2,
                    15.4,
                    0.47,
                    35.4,
                    30.1,
                    40.7,
                    0.76,
                    "medium",
                    List.of(Map.of("name", "last_5_points_avg", "value", 15.0)));
        }

        private static Map<String, Object> baselineSection(int testRows, Map<String, Object> metrics) {
            return missingSampleSize
                    ? Map.of("metrics", metrics)
                    : Map.of("testRows", testRows, "metrics", metrics);
        }
    }
}
