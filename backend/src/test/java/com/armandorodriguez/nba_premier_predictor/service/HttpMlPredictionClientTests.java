package com.armandorodriguez.nba_premier_predictor.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import com.armandorodriguez.nba_premier_predictor.dto.PlayerPredictionRequest;
import com.armandorodriguez.nba_premier_predictor.dto.TeamScorePredictionRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;

class HttpMlPredictionClientTests {

    @Test
    void postsPredictionBodyAsHttp11Json() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        AtomicReference<List<String>> upgradeHeader = new AtomicReference<>();
        AtomicReference<String> protocol = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/predict/player", exchange -> {
            protocol.set(exchange.getProtocol());
            upgradeHeader.set(exchange.getRequestHeaders().get("Upgrade"));
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = """
                    {
                      "model_version": "player-baseline-v1",
                      "trained_rows": 1,
                      "game_id": 22300003,
                      "player_id": 201939,
                      "team_id": 1610612744,
                      "projected_points": 18.5,
                      "projected_rebounds": 6.2,
                      "projected_assists": 5.4,
                      "projected_minutes": 31.0,
                      "fantasy_points": 35.4,
                      "fantasy_floor": 30.1,
                      "fantasy_ceiling": 40.7,
                      "confidence_score": 0.76,
                      "risk_level": "medium",
                      "factors": []
                    }
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        try {
            HttpMlPredictionClient client = new HttpMlPredictionClient(
                    "http://localhost:" + server.getAddress().getPort(),
                    new ObjectMapper());

            var response = client.predictPlayer(new PlayerPredictionRequest(
                    22300003L,
                    201939L,
                    1610612744L,
                    LocalDateTime.parse("2024-01-05T21:59:59"),
                    Map.of("last_5_points_avg", 15.0)));

            assertThat(response.projectedPoints()).isEqualTo(18.5);
            assertThat(protocol.get()).isEqualTo("HTTP/1.1");
            assertThat(upgradeHeader.get()).isNull();
            assertThat(requestBody.get())
                    .contains("\"player_id\":201939")
                    .contains("\"data_cutoff_time\":\"2024-01-05T21:59:59\"")
                    .contains("\"features\":{\"last_5_points_avg\":15.0}");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void postsGameScorePredictionBodyAsJson() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/predict/game-score", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = """
                    {
                      "model_version": "game-score-baseline-v1",
                      "trained_rows": 1,
                      "game_id": 22300003,
                      "home_team_id": 1610612744,
                      "away_team_id": 1610612747,
                      "home_team_score": 116.5,
                      "away_team_score": 108.2,
                      "predicted_winner_team_id": 1610612744,
                      "point_differential": 8.3,
                      "confidence_score": 0.72,
                      "factors": []
                    }
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        try {
            HttpMlPredictionClient client = new HttpMlPredictionClient(
                    "http://localhost:" + server.getAddress().getPort(),
                    new ObjectMapper());

            var response = client.predictGameScore(new TeamScorePredictionRequest(
                    22300003L,
                    1610612744L,
                    1610612747L,
                    LocalDateTime.parse("2024-01-05T21:59:59"),
                    Map.of("season_point_differential_delta", 15.0)));

            assertThat(response.homeTeamScore()).isEqualTo(116.5);
            assertThat(response.predictedWinnerTeamId()).isEqualTo(1610612744L);
            assertThat(requestBody.get())
                    .contains("\"home_team_id\":1610612744")
                    .contains("\"away_team_id\":1610612747")
                    .contains("\"data_cutoff_time\":\"2024-01-05T21:59:59\"")
                    .contains("\"features\":{\"season_point_differential_delta\":15.0}");
        } finally {
            server.stop(0);
        }
    }
}
