package com.armandorodriguez.nba_premier_predictor.service;

import java.util.Map;

import com.armandorodriguez.nba_premier_predictor.dto.PlayerPredictionRequest;
import com.armandorodriguez.nba_premier_predictor.dto.PlayerPredictionResponse;

public interface MlPredictionClient {

    PlayerPredictionResponse predictPlayer(PlayerPredictionRequest request);

    PlayerPredictionResponse predictFantasy(PlayerPredictionRequest request);

    Map<String, Object> modelMetrics();

    Map<String, Object> modelVersions();
}
