package com.armandorodriguez.nba_premier_predictor.service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.armandorodriguez.nba_premier_predictor.aws.S3StorageService;
import com.armandorodriguez.nba_premier_predictor.dto.PlayerPredictionRequest;
import com.armandorodriguez.nba_premier_predictor.dto.PlayerPredictionResponse;
import com.armandorodriguez.nba_premier_predictor.dto.TeamScorePredictionRequest;
import com.armandorodriguez.nba_premier_predictor.dto.TeamScorePredictionResponse;

@Service
public class PredictionExportService {

    private static final Logger log = LoggerFactory.getLogger(PredictionExportService.class);

    private final S3StorageService s3StorageService;

    public PredictionExportService(S3StorageService s3StorageService) {
        this.s3StorageService = s3StorageService;
    }

    public void exportPlayerPrediction(String predictionType, PlayerPredictionRequest request, PlayerPredictionResponse response) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("predictionId", response.predictionId());
        report.put("predictionType", predictionType);
        report.put("gameId", request.gameId());
        report.put("playerId", request.playerId());
        report.put("teamId", request.teamId());
        report.put("modelVersion", response.modelVersion());
        report.put("requestedAt", Instant.now().toString());
        report.put("dataCutoffTime", request.dataCutoffTime() == null ? null : request.dataCutoffTime().toString());
        report.put("projection", playerProjection(response));
        report.put("confidenceScore", response.confidenceScore());
        report.put("factors", response.factors());
        export(predictionType, response.predictionId(), report);
    }

    public void exportGameScorePrediction(TeamScorePredictionRequest request, TeamScorePredictionResponse response) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("predictionId", response.predictionId());
        report.put("predictionType", "game_score");
        report.put("gameId", request.gameId());
        report.put("homeTeamId", response.homeTeamId());
        report.put("awayTeamId", response.awayTeamId());
        report.put("modelVersion", response.modelVersion());
        report.put("requestedAt", Instant.now().toString());
        report.put("dataCutoffTime", request.dataCutoffTime() == null ? null : request.dataCutoffTime().toString());
        report.put("projection", gameScoreProjection(response));
        report.put("confidenceScore", response.confidenceScore());
        report.put("factors", response.factors());
        export("game_score", response.predictionId(), report);
    }

    private void export(String predictionType, Long predictionId, Map<String, Object> report) {
        if (predictionId == null) {
            return;
        }
        try {
            s3StorageService.putJson(s3StorageService.predictionReportKey(predictionType, predictionId), report);
        } catch (RuntimeException ex) {
            log.warn("Prediction export failed for prediction {}", predictionId, ex);
        }
    }

    private static Map<String, Object> playerProjection(PlayerPredictionResponse response) {
        Map<String, Object> projection = new LinkedHashMap<>();
        projection.put("points", response.projectedPoints());
        projection.put("rebounds", response.projectedRebounds());
        projection.put("assists", response.projectedAssists());
        projection.put("minutes", response.projectedMinutes());
        projection.put("steals", response.projectedSteals());
        projection.put("blocks", response.projectedBlocks());
        projection.put("turnovers", response.projectedTurnovers());
        projection.put("fieldGoalsMade", response.projectedFieldGoalsMade());
        projection.put("fieldGoalsAttempted", response.projectedFieldGoalsAttempted());
        projection.put("fieldGoalPercentage", response.projectedFieldGoalPercentage());
        projection.put("fantasyPoints", response.fantasyPoints());
        projection.put("fantasyFloor", response.fantasyFloor());
        projection.put("fantasyCeiling", response.fantasyCeiling());
        projection.put("riskLevel", response.riskLevel());
        return projection;
    }

    private static Map<String, Object> gameScoreProjection(TeamScorePredictionResponse response) {
        Map<String, Object> projection = new LinkedHashMap<>();
        projection.put("homeTeamScore", response.homeTeamScore());
        projection.put("awayTeamScore", response.awayTeamScore());
        projection.put("predictedWinnerTeamId", response.predictedWinnerTeamId());
        projection.put("pointDifferential", response.pointDifferential());
        return projection;
    }
}
