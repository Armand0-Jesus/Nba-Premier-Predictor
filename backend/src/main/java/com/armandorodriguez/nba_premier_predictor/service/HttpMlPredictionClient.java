package com.armandorodriguez.nba_premier_predictor.service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.armandorodriguez.nba_premier_predictor.dto.PlayerPredictionRequest;
import com.armandorodriguez.nba_premier_predictor.dto.PlayerPredictionResponse;
import com.armandorodriguez.nba_premier_predictor.dto.TeamScorePredictionRequest;
import com.armandorodriguez.nba_premier_predictor.dto.TeamScorePredictionResponse;
import com.armandorodriguez.nba_premier_predictor.exception.MlServiceException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
class HttpMlPredictionClient implements MlPredictionClient {

    private final String baseUrl;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    HttpMlPredictionClient(
            @Value("${app.ml-service.url:http://localhost:8000}") String baseUrl,
            ObjectMapper objectMapper) {
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    @Override
    public PlayerPredictionResponse predictPlayer(PlayerPredictionRequest request) {
        return postPrediction("/predict/player", request);
    }

    @Override
    public PlayerPredictionResponse predictFantasy(PlayerPredictionRequest request) {
        return postPrediction("/predict/fantasy", request);
    }

    @Override
    public TeamScorePredictionResponse predictGameScore(TeamScorePredictionRequest request) {
        try {
            HttpResponse<String> response = send("POST", "/predict/game-score", gameScorePredictionBody(request));
            return objectMapper.readValue(response.body(), TeamScorePredictionResponse.class);
        } catch (JsonProcessingException ex) {
            throw new MlServiceException("Could not parse ML game-score response", ex);
        }
    }

    @Override
    public Map<String, Object> modelMetrics() {
        return getMap("/model/metrics");
    }

    @Override
    public Map<String, Object> modelVersions() {
        return getMap("/model/versions");
    }

    private PlayerPredictionResponse postPrediction(String path, PlayerPredictionRequest request) {
        try {
            HttpResponse<String> response = send("POST", path, predictionBody(request));
            return objectMapper.readValue(response.body(), PlayerPredictionResponse.class);
        } catch (JsonProcessingException ex) {
            throw new MlServiceException("Could not parse ML prediction response", ex);
        }
    }

    private Map<String, Object> getMap(String path) {
        try {
            HttpResponse<String> response = send("GET", path, null);
            return objectMapper.readValue(response.body(), new TypeReference<>() {
            });
        } catch (JsonProcessingException ex) {
            throw new MlServiceException("Could not parse ML service response", ex);
        }
    }

    private HttpResponse<String> send(String method, String path, String body) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(30));
        if ("POST".equals(method)) {
            builder.header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body));
        } else {
            builder.GET();
        }

        try {
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new MlServiceException("ML service returned HTTP " + response.statusCode() + ": " + response.body());
            }
            return response;
        } catch (IOException ex) {
            throw new MlServiceException("Could not reach ML service", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new MlServiceException("ML service request was interrupted", ex);
        }
    }

    private String predictionBody(PlayerPredictionRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("game_id", request.gameId());
        body.put("player_id", request.playerId());
        body.put("team_id", request.teamId());
        body.put("data_cutoff_time", request.dataCutoffTime() == null ? null : request.dataCutoffTime().toString());
        body.put("features", request.features());
        try {
            return objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException ex) {
            throw new MlServiceException("Could not serialize ML prediction request", ex);
        }
    }

    private String gameScorePredictionBody(TeamScorePredictionRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("game_id", request.gameId());
        body.put("home_team_id", request.homeTeamId());
        body.put("away_team_id", request.awayTeamId());
        body.put("data_cutoff_time", request.dataCutoffTime() == null ? null : request.dataCutoffTime().toString());
        body.put("features", request.features());
        try {
            return objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException ex) {
            throw new MlServiceException("Could not serialize ML game-score request", ex);
        }
    }
}
