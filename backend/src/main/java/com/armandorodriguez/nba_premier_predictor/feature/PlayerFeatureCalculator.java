package com.armandorodriguez.nba_premier_predictor.feature;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
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

        Integer ageAtGame = ageOn(target.birthDate(), target.gameDateTime().toLocalDate());
        Integer ageEnteringSeason = target.seasonStartYear() == null
                ? null
                : ageOn(target.birthDate(), LocalDate.of(target.seasonStartYear(), 7, 1));
        Integer yearsExperience = yearsExperience(target);
        Double recentMinutesTrend = minutesTrend(priorPlayerGames);
        Double fantasyVolatility = fantasyVolatility(priorPlayerGames);
        Long daysRest = daysRest(target, priorPlayerGames);
        Boolean backToBack = isBackToBack(target, priorPlayerGames);

        Map<String, Object> features = new LinkedHashMap<>();
        features.put("games_played_prior", priorPlayerGames.size());
        features.put("age_at_game", ageAtGame);
        features.put("age_entering_season", ageEnteringSeason);
        features.put("years_experience_at_game", yearsExperience);
        features.put("career_stage", careerStage(ageAtGame, yearsExperience, target.careerGamesPlayedBeforeGame()));
        features.put("career_games_played_before_game", target.careerGamesPlayedBeforeGame());
        features.put("career_minutes_played_before_game", decimal(target.careerMinutesPlayedBeforeGame()));
        features.put("projected_starter", target.projectedStarter());
        features.put("player_changed_team_before_game", target.playerChangedTeamBeforeGame());
        features.put("same_position_competition", target.samePositionCompetition());
        features.put("team_missing_starters_count", target.teamMissingStartersCount());
        features.put("team_roster_turnover_score", target.teamRosterTurnoverScore());
        features.put("team_minutes_vacated_by_departures", target.teamMinutesVacatedByDepartures());
        features.put("team_usage_vacated_by_departures", target.teamUsageVacatedByDepartures());
        features.put("teammate_injury_usage_boost", target.teammateInjuryUsageBoost());
        features.put("teammate_injury_minutes_boost", target.teammateInjuryMinutesBoost());
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
        features.put("minutes_trend", recentMinutesTrend);
        features.put("recent_minutes_trend", recentMinutesTrend);
        features.put("home_points_avg", averageAll(priorPlayerGames.stream().filter(PlayerFeatureCalculator::home).toList(), PlayerFeatureRow::points));
        features.put("away_points_avg", averageAll(priorPlayerGames.stream().filter(row -> !home(row)).toList(), PlayerFeatureRow::points));
        features.put("days_rest", daysRest);
        features.put("back_to_back", Boolean.TRUE.equals(backToBack));
        features.put("rest_management_risk", restManagementRisk(ageAtGame, daysRest, backToBack,
                target.injuryHistoryCountBeforeGame(), averageMinutes(sameSeason), fantasyVolatility));
        features.put("injury_history_count_before_game", target.injuryHistoryCountBeforeGame());
        features.put("fantasy_volatility_score", fantasyVolatility);
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

    private static Integer ageOn(LocalDate birthDate, LocalDate date) {
        if (birthDate == null || date == null) {
            return null;
        }
        return (int) ChronoUnit.YEARS.between(birthDate, date);
    }

    private static Integer yearsExperience(PlayerFeatureRow target) {
        if (target.fromYear() == null || target.seasonStartYear() == null) {
            return null;
        }
        return Math.max(0, target.seasonStartYear() - target.fromYear());
    }

    private static String careerStage(Integer ageAtGame, Integer yearsExperience, Integer careerGames) {
        if ((yearsExperience != null && yearsExperience == 0)
                || (careerGames != null && careerGames < 50 && (yearsExperience == null || yearsExperience <= 1))) {
            return "rookie";
        }
        if (ageAtGame == null) {
            return null;
        }
        if (ageAtGame <= 25) {
            return "young_developing";
        }
        if (ageAtGame <= 31) {
            return "prime";
        }
        if (ageAtGame <= 34) {
            return "veteran";
        }
        return "late_career";
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
        Double latest = averageMinutes(rows.subList(rows.size() - 3, rows.size()));
        Double previous = averageMinutes(rows.subList(rows.size() - 6, rows.size() - 3));
        if (latest == null || previous == null) {
            return null;
        }
        return round(latest - previous);
    }

    private static Double fantasyVolatility(List<PlayerFeatureRow> rows) {
        List<Double> values = last(rows, 10).stream()
                .map(PlayerFeatureCalculator::fantasyPoints)
                .filter(Objects::nonNull)
                .toList();
        if (values.size() < 2) {
            return null;
        }
        double mean = values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double variance = values.stream()
                .mapToDouble(value -> Math.pow(value - mean, 2))
                .average()
                .orElse(0);
        return round(Math.sqrt(variance));
    }

    private static Double fantasyPoints(PlayerFeatureRow row) {
        if (row.points() == null || row.rebounds() == null || row.assists() == null) {
            return null;
        }
        return row.points()
                + (1.2 * row.rebounds())
                + (1.5 * row.assists())
                + (3.0 * valueOrZero(row.steals()))
                + (3.0 * valueOrZero(row.blocks()))
                - valueOrZero(row.turnovers());
    }

    private static double valueOrZero(Integer value) {
        return value == null ? 0.0 : value;
    }

    private static Double restManagementRisk(
            Integer ageAtGame,
            Long daysRest,
            Boolean backToBack,
            Integer injuryHistoryCount,
            Double seasonMinutes,
            Double fantasyVolatility) {
        double risk = 0.0;
        if (ageAtGame != null) {
            if (ageAtGame >= 35) {
                risk += 0.20;
            } else if (ageAtGame >= 32) {
                risk += 0.10;
            }
        }
        if (Boolean.TRUE.equals(backToBack)) {
            risk += ageAtGame != null && ageAtGame >= 32 ? 0.35 : 0.20;
        } else if (daysRest != null && daysRest <= 1) {
            risk += 0.15;
        }
        if (injuryHistoryCount != null) {
            risk += Math.min(0.25, injuryHistoryCount * 0.03);
        }
        if (seasonMinutes != null && seasonMinutes >= 34.0) {
            risk += 0.10;
        }
        if (fantasyVolatility != null && fantasyVolatility >= 12.0) {
            risk += 0.10;
        }
        return round(Math.min(1.0, risk));
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

    private static Double decimal(BigDecimal value) {
        return value == null ? null : round(value.doubleValue());
    }
}
