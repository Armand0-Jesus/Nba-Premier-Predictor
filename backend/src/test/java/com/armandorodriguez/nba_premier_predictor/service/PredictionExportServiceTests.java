package com.armandorodriguez.nba_premier_predictor.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.armandorodriguez.nba_premier_predictor.aws.S3StorageService;
import com.armandorodriguez.nba_premier_predictor.dto.PlayerPredictionRequest;
import com.armandorodriguez.nba_premier_predictor.dto.PlayerPredictionResponse;

class PredictionExportServiceTests {

    @Test
    void playerPredictionReportUsesStringCutoffTime() {
        S3StorageService storageService = mock(S3StorageService.class);
        when(storageService.predictionReportKey("player_stat", 7L)).thenReturn("predictions/player_stat/7.json");
        PredictionExportService exportService = new PredictionExportService(storageService);

        exportService.exportPlayerPrediction(
                "player_stat",
                new PlayerPredictionRequest(
                        22300003L,
                        201939L,
                        1610612744L,
                        LocalDateTime.of(2024, 1, 5, 21, 59, 59),
                        Map.of("last_5_points_avg", 15.0)),
                new PlayerPredictionResponse(
                        7L,
                        "player-baseline-v2",
                        30034,
                        22300003L,
                        201939L,
                        1610612744L,
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
                        List.of()));

        ArgumentCaptor<Object> report = ArgumentCaptor.forClass(Object.class);
        verify(storageService).putJson(org.mockito.ArgumentMatchers.eq("predictions/player_stat/7.json"), report.capture());
        assertThat(((Map<?, ?>) report.getValue()).get("dataCutoffTime")).isEqualTo("2024-01-05T21:59:59");
    }
}
