package com.armandorodriguez.nba_premier_predictor.feature;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class PlayerFeatureCalculatorTests {

    private final PlayerFeatureCalculator calculator = new PlayerFeatureCalculator();

    @Test
    void calculatesPreGameFeaturesFromPriorGamesOnly() {
        List<PlayerFeatureRow> prior = List.of(
                playerRow(1, "2024-01-01T19:00:00", 10, 4, 3, 20),
                playerRow(2, "2024-01-02T19:00:00", 12, 5, 4, 22),
                playerRow(3, "2024-01-03T19:00:00", 14, 6, 5, 24),
                playerRow(4, "2024-01-05T19:00:00", 20, 7, 6, 30),
                playerRow(5, "2024-01-06T19:00:00", 22, 8, 7, 32),
                playerRow(6, "2024-01-08T19:00:00", 24, 9, 8, 34));
        PlayerFeatureRow target = playerRow(7, "2024-01-10T19:00:00", 999, 99, 99, 99);

        Map<String, Object> features = calculator.calculate(target, prior, List.of(
                new TeamFeatureRow(1610612747L, LocalDateTime.parse("2024-01-02T19:00:00"), 98, 100),
                new TeamFeatureRow(1610612747L, LocalDateTime.parse("2024-01-04T19:00:00"), 115, 110)));

        assertThat(features)
                .containsEntry("last_3_points_avg", 22.0)
                .containsEntry("last_5_points_avg", 18.4)
                .containsEntry("season_points_avg", 17.0)
                .containsEntry("minutes_trend", 10.0)
                .containsEntry("days_rest", 2L)
                .containsEntry("back_to_back", false)
                .containsEntry("opponent_points_allowed_avg", 105.0)
                .containsEntry("opponent_point_differential_avg", 1.5);
    }

    private static PlayerFeatureRow playerRow(long gameId, String gameTime, int points, int rebounds, int assists, int minutes) {
        return new PlayerFeatureRow(
                gameId,
                201939L,
                1610612744L,
                1610612747L,
                2023,
                LocalDateTime.parse(gameTime),
                true,
                BigDecimal.valueOf(minutes),
                points,
                rebounds,
                assists,
                2);
    }
}
