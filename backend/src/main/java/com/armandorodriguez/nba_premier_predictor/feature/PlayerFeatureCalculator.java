package com.armandorodriguez.nba_premier_predictor.feature;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import org.springframework.stereotype.Component;

@Component
public class PlayerFeatureCalculator {

    public Map<String, Object> calculate(
            PlayerFeatureRow target,
            List<PlayerFeatureRow> priorPlayerGames,
            List<TeamFeatureRow> priorOpponentGames) {

        List<PlayerFeatureRow> sameSeason = priorPlayerGames.stream()
                .filter(row -> Objects.equals(row.seasonStartYear(), target.seasonStartYear()))
                .toList();

        Map<String, Object> features = new LinkedHashMap<>();
        features.put("games_played_prior", priorPlayerGames.size());
        features.put("last_3_points_avg", averageLast(priorPlayerGames, 3, PlayerFeatureRow::points));
        features.put("last_5_points_avg", averageLast(priorPlayerGames, 5, PlayerFeatureRow::points));
        features.put("last_10_points_avg", averageLast(priorPlayerGames, 10, PlayerFeatureRow::points));
        features.put("last_3_rebounds_avg", averageLast(priorPlayerGames, 3, PlayerFeatureRow::rebounds));
        features.put("last_5_rebounds_avg", averageLast(priorPlayerGames, 5, PlayerFeatureRow::rebounds));
        features.put("last_10_rebounds_avg", averageLast(priorPlayerGames, 10, PlayerFeatureRow::rebounds));
        features.put("last_3_assists_avg", averageLast(priorPlayerGames, 3, PlayerFeatureRow::assists));
        features.put("last_5_assists_avg", averageLast(priorPlayerGames, 5, PlayerFeatureRow::assists));
        features.put("last_10_assists_avg", averageLast(priorPlayerGames, 10, PlayerFeatureRow::assists));
        features.put("last_5_minutes_avg", averageLastMinutes(priorPlayerGames, 5));
        features.put("last_10_minutes_avg", averageLastMinutes(priorPlayerGames, 10));
        features.put("season_points_avg", averageAll(sameSeason, PlayerFeatureRow::points));
        features.put("season_rebounds_avg", averageAll(sameSeason, PlayerFeatureRow::rebounds));
        features.put("season_assists_avg", averageAll(sameSeason, PlayerFeatureRow::assists));
        features.put("season_minutes_avg", averageMinutes(sameSeason));
        features.put("minutes_trend", minutesTrend(priorPlayerGames));
        features.put("home_points_avg", averageAll(priorPlayerGames.stream().filter(PlayerFeatureCalculator::home).toList(), PlayerFeatureRow::points));
        features.put("away_points_avg", averageAll(priorPlayerGames.stream().filter(row -> !home(row)).toList(), PlayerFeatureRow::points));
        features.put("days_rest", daysRest(target, priorPlayerGames));
        features.put("back_to_back", Boolean.TRUE.equals(isBackToBack(target, priorPlayerGames)));
        features.put("is_home", Boolean.TRUE.equals(target.home()));
        features.put("opponent_games_prior", priorOpponentGames.size());
        features.put("opponent_points_allowed_avg", averageAll(priorOpponentGames, TeamFeatureRow::opponentScore));
        features.put("opponent_point_differential_avg", averageDouble(priorOpponentGames, row -> {
            if (row.teamScore() == null || row.opponentScore() == null) {
                return null;
            }
            return (double) row.teamScore() - row.opponentScore();
        }));
        return features;
    }

    private static boolean home(PlayerFeatureRow row) {
        return Boolean.TRUE.equals(row.home());
    }

    private static Double averageLast(List<PlayerFeatureRow> rows, int count, Function<PlayerFeatureRow, Integer> getter) {
        return averageAll(last(rows, count), getter);
    }

    private static Double averageLastMinutes(List<PlayerFeatureRow> rows, int count) {
        return averageMinutes(last(rows, count));
    }

    private static Double averageMinutes(List<PlayerFeatureRow> rows) {
        return average(rows, row -> row.minutes() == null ? null : row.minutes().doubleValue());
    }

    private static <T> Double averageAll(List<T> rows, Function<T, Integer> getter) {
        return average(rows, row -> {
            Integer value = getter.apply(row);
            return value == null ? null : value.doubleValue();
        });
    }

    private static <T> Double average(List<T> rows, Function<T, Double> getter) {
        List<Double> values = rows.stream().map(getter).filter(Objects::nonNull).toList();
        if (values.isEmpty()) {
            return null;
        }
        return round(values.stream().mapToDouble(Double::doubleValue).average().orElse(0));
    }

    private static <T> Double averageDouble(List<T> rows, Function<T, Double> getter) {
        return average(rows, getter);
    }

    private static List<PlayerFeatureRow> last(List<PlayerFeatureRow> rows, int count) {
        return rows.subList(Math.max(0, rows.size() - count), rows.size());
    }

    private static Double minutesTrend(List<PlayerFeatureRow> rows) {
        if (rows.size() < 6) {
            return null;
        }
        double latest = averageMinutes(rows.subList(rows.size() - 3, rows.size()));
        double previous = averageMinutes(rows.subList(rows.size() - 6, rows.size() - 3));
        return round(latest - previous);
    }

    private static Long daysRest(PlayerFeatureRow target, List<PlayerFeatureRow> priorPlayerGames) {
        if (priorPlayerGames.isEmpty()) {
            return null;
        }
        PlayerFeatureRow previous = priorPlayerGames.get(priorPlayerGames.size() - 1);
        return ChronoUnit.DAYS.between(previous.gameDateTime().toLocalDate(), target.gameDateTime().toLocalDate());
    }

    private static Boolean isBackToBack(PlayerFeatureRow target, List<PlayerFeatureRow> priorPlayerGames) {
        Long daysRest = daysRest(target, priorPlayerGames);
        return daysRest == null ? null : daysRest == 1;
    }

    private static double round(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
