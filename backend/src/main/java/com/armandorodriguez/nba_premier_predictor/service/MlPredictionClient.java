package com.armandorodriguez.nba_premier_predictor.service;

import java.util.Map;

import com.armandorodriguez.nba_premier_predictor.dto.PlayerPredictionRequest;
import com.armandorodriguez.nba_premier_predictor.dto.PlayerPredictionResponse;
import com.armandorodriguez.nba_premier_predictor.dto.TeamScorePredictionRequest;
import com.armandorodriguez.nba_premier_predictor.dto.TeamScorePredictionResponse;

public interface MlPredictionClient {

    PlayerPredictionResponse predictPlayer(PlayerPredictionRequest request);

    PlayerPredictionResponse predictFantasy(PlayerPredictionRequest request);

    TeamScorePredictionResponse predictGameScore(TeamScorePredictionRequest request);

    Map<String, Object> modelMetrics();

    Map<String, Object> modelVersions();
}
