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
public class TeamFeatureCalculator {

    public Map<String, Object> calculate(
            TeamFeatureRow target,
            List<TeamFeatureRow> priorTeamGames,
            List<TeamFeatureRow> priorOpponentGames) {

        List<TeamFeatureRow> sameSeason = priorTeamGames.stream()
                .filter(row -> Objects.equals(row.seasonStartYear(), target.seasonStartYear()))
                .toList();

        Map<String, Object> features = new LinkedHashMap<>();
        features.put("games_played_prior", priorTeamGames.size());
        features.put("last_3_team_score_avg", averageLast(priorTeamGames, 3, TeamFeatureRow::teamScore));
        features.put("last_5_team_score_avg", averageLast(priorTeamGames, 5, TeamFeatureRow::teamScore));
        features.put("last_10_team_score_avg", averageLast(priorTeamGames, 10, TeamFeatureRow::teamScore));
        features.put("last_3_points_allowed_avg", averageLast(priorTeamGames, 3, TeamFeatureRow::opponentScore));
        features.put("last_5_points_allowed_avg", averageLast(priorTeamGames, 5, TeamFeatureRow::opponentScore));
        features.put("last_10_points_allowed_avg", averageLast(priorTeamGames, 10, TeamFeatureRow::opponentScore));
        features.put("last_5_point_differential_avg", averageLastDouble(priorTeamGames, 5, TeamFeatureCalculator::pointDifferential));
        features.put("season_team_score_avg", averageAll(sameSeason, TeamFeatureRow::teamScore));
        features.put("season_points_allowed_avg", averageAll(sameSeason, TeamFeatureRow::opponentScore));
        features.put("season_point_differential_avg", averageDouble(sameSeason, TeamFeatureCalculator::pointDifferential));
        features.put("last_5_assists_avg", averageLast(priorTeamGames, 5, TeamFeatureRow::assists));
        features.put("last_5_rebounds_avg", averageLast(priorTeamGames, 5, TeamFeatureRow::rebounds));
        features.put("last_5_turnovers_avg", averageLast(priorTeamGames, 5, TeamFeatureRow::turnovers));
        features.put("home_team_score_avg", averageAll(priorTeamGames.stream().filter(TeamFeatureCalculator::home).toList(), TeamFeatureRow::teamScore));
        features.put("away_team_score_avg", averageAll(priorTeamGames.stream().filter(TeamFeatureCalculator::away).toList(), TeamFeatureRow::teamScore));
        features.put("days_rest", daysRest(target, priorTeamGames));
        features.put("back_to_back", Boolean.TRUE.equals(isBackToBack(target, priorTeamGames)));
        features.put("is_home", Boolean.TRUE.equals(target.home()));
        features.put("opponent_games_prior", priorOpponentGames.size());
        features.put("opponent_team_score_avg", averageAll(priorOpponentGames, TeamFeatureRow::teamScore));
        features.put("opponent_points_allowed_avg", averageAll(priorOpponentGames, TeamFeatureRow::opponentScore));
        features.put("opponent_point_differential_avg", averageDouble(priorOpponentGames, TeamFeatureCalculator::pointDifferential));
        return features;
    }

    private static boolean home(TeamFeatureRow row) {
        return Boolean.TRUE.equals(row.home());
    }

    private static boolean away(TeamFeatureRow row) {
        return Boolean.FALSE.equals(row.home());
    }

    private static Double pointDifferential(TeamFeatureRow row) {
        if (row.teamScore() == null || row.opponentScore() == null) {
            return null;
        }
        return (double) row.teamScore() - row.opponentScore();
    }

    private static Double averageLast(List<TeamFeatureRow> rows, int count, Function<TeamFeatureRow, Integer> getter) {
        return averageAll(last(rows, count), getter);
    }

    private static Double averageLastDouble(List<TeamFeatureRow> rows, int count, Function<TeamFeatureRow, Double> getter) {
        return averageDouble(last(rows, count), getter);
    }

    private static <T> Double averageAll(List<T> rows, Function<T, Integer> getter) {
        return average(rows, row -> {
            Integer value = getter.apply(row);
            return value == null ? null : value.doubleValue();
        });
    }

    private static <T> Double averageDouble(List<T> rows, Function<T, Double> getter) {
        return average(rows, getter);
    }

    private static <T> Double average(List<T> rows, Function<T, Double> getter) {
        List<Double> values = rows.stream().map(getter).filter(Objects::nonNull).toList();
        if (values.isEmpty()) {
            return null;
        }
        return round(values.stream().mapToDouble(Double::doubleValue).average().orElse(0));
    }

    private static List<TeamFeatureRow> last(List<TeamFeatureRow> rows, int count) {
        return rows.subList(Math.max(0, rows.size() - count), rows.size());
    }

    private static Long daysRest(TeamFeatureRow target, List<TeamFeatureRow> priorTeamGames) {
        if (priorTeamGames.isEmpty()) {
            return null;
        }
        TeamFeatureRow previous = priorTeamGames.get(priorTeamGames.size() - 1);
        return ChronoUnit.DAYS.between(previous.gameDateTime().toLocalDate(), target.gameDateTime().toLocalDate());
    }

    private static Boolean isBackToBack(TeamFeatureRow target, List<TeamFeatureRow> priorTeamGames) {
        Long daysRest = daysRest(target, priorTeamGames);
        return daysRest == null ? null : daysRest == 1;
    }

    private static double round(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
