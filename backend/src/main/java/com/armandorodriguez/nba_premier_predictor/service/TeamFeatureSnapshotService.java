package com.armandorodriguez.nba_premier_predictor.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.armandorodriguez.nba_premier_predictor.dto.FeatureGenerationResponse;
import com.armandorodriguez.nba_premier_predictor.feature.TeamFeatureCalculator;
import com.armandorodriguez.nba_premier_predictor.feature.TeamFeatureRow;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class TeamFeatureSnapshotService {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final TeamFeatureCalculator calculator;

    public TeamFeatureSnapshotService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper, TeamFeatureCalculator calculator) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.calculator = calculator;
    }

    @Transactional
    public FeatureGenerationResponse generateTeam(Integer seasonStartYear) {
        List<TeamFeatureRow> rows = loadTeamRows(seasonStartYear);
        Map<Long, List<TeamFeatureRow>> byTeam = groupByTeam(rows);
        List<Object[]> batch = new ArrayList<>();

        for (List<TeamFeatureRow> teamRows : byTeam.values()) {
            List<TeamFeatureRow> prior = new ArrayList<>();
            for (TeamFeatureRow row : teamRows) {
                LocalDateTime dataCutoff = row.gameDateTime().minusSeconds(1);
                batch.add(new Object[] {
                        Timestamp.valueOf(dataCutoff),
                        row.gameId(),
                        row.teamId(),
                        Timestamp.valueOf(dataCutoff),
                        toJson(calculator.calculate(row, prior, opponentRowsBefore(byTeam, row)))
                });
                prior.add(row);
            }
        }

        persistTeam(batch);
        return new FeatureGenerationResponse("team", seasonStartYear, batch.size());
    }

    @Transactional
    public FeatureGenerationResponse generateGame(Integer seasonStartYear) {
        List<TeamFeatureRow> rows = loadTeamRows(seasonStartYear);
        Map<Long, List<TeamFeatureRow>> byTeam = groupByTeam(rows);
        Map<Long, List<TeamFeatureRow>> byGame = rows.stream()
                .collect(Collectors.groupingBy(TeamFeatureRow::gameId, LinkedHashMap::new, Collectors.toList()));
        List<Object[]> batch = new ArrayList<>();

        for (Map.Entry<Long, List<TeamFeatureRow>> entry : byGame.entrySet()) {
            TeamFeatureRow home = findByHome(entry.getValue(), true);
            TeamFeatureRow away = findByHome(entry.getValue(), false);
            if (home == null || away == null) {
                continue;
            }

            Map<String, Object> homeFeatures = calculator.calculate(home, rowsBefore(byTeam, home), opponentRowsBefore(byTeam, home));
            Map<String, Object> awayFeatures = calculator.calculate(away, rowsBefore(byTeam, away), opponentRowsBefore(byTeam, away));
            Map<String, Object> features = new LinkedHashMap<>();
            features.put("home_team_id", home.teamId());
            features.put("away_team_id", away.teamId());
            putPrefixed(features, "home_", homeFeatures);
            putPrefixed(features, "away_", awayFeatures);
            features.put("games_played_prior_delta", difference(homeFeatures.get("games_played_prior"), awayFeatures.get("games_played_prior")));
            features.put("days_rest_delta", difference(homeFeatures.get("days_rest"), awayFeatures.get("days_rest")));
            features.put("season_point_differential_delta", difference(
                    homeFeatures.get("season_point_differential_avg"),
                    awayFeatures.get("season_point_differential_avg")));
            features.put("last_5_point_differential_delta", difference(
                    homeFeatures.get("last_5_point_differential_avg"),
                    awayFeatures.get("last_5_point_differential_avg")));

            LocalDateTime dataCutoff = home.gameDateTime().minusSeconds(1);
            batch.add(new Object[] {
                    Timestamp.valueOf(dataCutoff),
                    entry.getKey(),
                    Timestamp.valueOf(dataCutoff),
                    toJson(features)
            });
        }

        persistGame(batch);
        return new FeatureGenerationResponse("game", seasonStartYear, batch.size());
    }

    private List<TeamFeatureRow> loadTeamRows(Integer seasonStartYear) {
        List<TeamFeatureRow> rows = jdbcTemplate.query("""
                select t.game_id, t.team_id, t.opponent_team_id, g.season_start_year,
                       g.game_date_time_est, t.home, t.team_score, t.opponent_score,
                       t.assists, t.rebounds_total, t.turnovers
                from team_game_stats t
                join games g on g.game_id = t.game_id
                where (? is null or g.season_start_year = ?)
                  and g.game_date_time_est is not null
                order by g.game_date_time_est, t.game_id, t.team_id
                """, this::mapTeamRow, seasonStartYear, seasonStartYear);
        Map<Long, List<RosterAgeRow>> rosterRows = loadRosterAgeRows().stream()
                .collect(Collectors.groupingBy(RosterAgeRow::teamId, LinkedHashMap::new, Collectors.toList()));
        rosterRows.values().forEach(teamRows -> teamRows.sort(Comparator.comparing(RosterAgeRow::snapshotDate)));
        List<InjuryContextRow> injuryRows = loadInjuryContextRows();
        List<TransactionContextRow> transactionRows = loadTransactionContextRows();
        return rows.stream()
                .map(row -> withAgeContext(row, rosterRows.getOrDefault(row.teamId(), List.of()), injuryRows, transactionRows))
                .toList();
    }

    private TeamFeatureRow mapTeamRow(ResultSet rs, int rowNum) throws SQLException {
        return new TeamFeatureRow(
                rs.getLong("game_id"),
                rs.getLong("team_id"),
                nullableLong(rs, "opponent_team_id"),
                nullableInt(rs, "season_start_year"),
                rs.getTimestamp("game_date_time_est").toLocalDateTime(),
                nullableBoolean(rs, "home"),
                nullableInt(rs, "team_score"),
                nullableInt(rs, "opponent_score"),
                nullableInt(rs, "assists"),
                nullableInt(rs, "rebounds_total"),
                nullableInt(rs, "turnovers"),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }

    private List<RosterAgeRow> loadRosterAgeRows() {
        return jdbcTemplate.query("""
                select r.snapshot_date, r.team_id, r.player_id, r.roster_status,
                       r.projected_minutes, p.birth_date
                from roster_snapshots r
                join players p on p.player_id = r.player_id
                where p.birth_date is not null
                order by r.team_id, r.snapshot_date, r.player_id
                """, (rs, rowNum) -> new RosterAgeRow(
                rs.getDate("snapshot_date").toLocalDate(),
                rs.getLong("team_id"),
                rs.getLong("player_id"),
                rs.getString("roster_status"),
                rs.getBigDecimal("projected_minutes"),
                rs.getDate("birth_date").toLocalDate()));
    }

    private TeamFeatureRow withAgeContext(
            TeamFeatureRow row,
            List<RosterAgeRow> rosterRows,
            List<InjuryContextRow> injuryRows,
            List<TransactionContextRow> transactionRows) {
        TeamAgeContext ageContext = ageContext(row, rosterRows, injuryRows, transactionRows);
        return new TeamFeatureRow(
                row.gameId(),
                row.teamId(),
                row.opponentTeamId(),
                row.seasonStartYear(),
                row.gameDateTime(),
                row.home(),
                row.teamScore(),
                row.opponentScore(),
                row.assists(),
                row.rebounds(),
                row.turnovers(),
                ageContext.averageTeamAge(),
                ageContext.starterAverageAge(),
                ageContext.rotationAverageAge(),
                ageContext.youngTeamFlag(),
                ageContext.veteranTeamFlag(),
                ageContext.teamRosterTurnoverScore(),
                ageContext.teamMinutesVacatedByDepartures(),
                ageContext.teamUsageVacatedByDepartures(),
                ageContext.teamMissingStartersCount(),
                ageContext.projectedStartersAvailableCount());
    }

    private TeamAgeContext ageContext(
            TeamFeatureRow row,
            List<RosterAgeRow> rosterRows,
            List<InjuryContextRow> injuryRows,
            List<TransactionContextRow> transactionRows) {
        LocalDate gameDate = row.gameDateTime().toLocalDate();
        LocalDate latestSnapshot = rosterRows.stream()
                .map(RosterAgeRow::snapshotDate)
                .filter(snapshotDate -> !snapshotDate.isAfter(gameDate))
                .max(LocalDate::compareTo)
                .orElse(null);
        if (latestSnapshot == null) {
            return new TeamAgeContext(null, null, null, null, null,
                    teamRosterTurnoverScore(row.teamId(), gameDate, transactionRows),
                    null,
                    null,
                    null,
                    null);
        }

        List<RosterAgeRow> activeRows = rosterRows.stream()
                .filter(rosterRow -> latestSnapshot.equals(rosterRow.snapshotDate()))
                .filter(TeamFeatureSnapshotService::activeRosterStatus)
                .toList();
        if (activeRows.isEmpty()) {
            return new TeamAgeContext(null, null, null, null, null,
                    teamRosterTurnoverScore(row.teamId(), gameDate, transactionRows),
                    null,
                    null,
                    null,
                    null);
        }

        VacatedContext vacatedContext = vacatedContext(row.teamId(), gameDate, rosterRows, transactionRows);
        Double averageTeamAge = averageAge(activeRows, gameDate);
        List<RosterAgeRow> projectedMinuteRows = activeRows.stream()
                .filter(rosterRow -> rosterRow.projectedMinutes() != null)
                .sorted(Comparator.comparing(RosterAgeRow::projectedMinutes).reversed())
                .toList();
        Integer missingStarters = missingStarterCount(row.teamId(), gameDate, projectedMinuteRows, injuryRows);
        Integer projectedStarters = projectedMinuteRows.isEmpty() || missingStarters == null
                ? null
                : Math.min(5, projectedMinuteRows.size()) - missingStarters;
        Double starterAverageAge = averageAge(projectedMinuteRows.stream().limit(5).toList(), gameDate);
        Double rotationAverageAge = averageAge(projectedMinuteRows.stream()
                .filter(rosterRow -> rosterRow.projectedMinutes().doubleValue() >= 10.0)
                .toList(), gameDate);
        return new TeamAgeContext(
                averageTeamAge,
                starterAverageAge,
                rotationAverageAge,
                averageTeamAge == null ? null : averageTeamAge <= 25.5,
                averageTeamAge == null ? null : averageTeamAge >= 30.0,
                teamRosterTurnoverScore(row.teamId(), gameDate, transactionRows),
                vacatedContext.minutesVacated(),
                vacatedContext.usageVacated(),
                missingStarters,
                projectedStarters);
    }

    private static Integer missingStarterCount(
            Long teamId,
            LocalDate gameDate,
            List<RosterAgeRow> projectedMinuteRows,
            List<InjuryContextRow> injuryRows) {
        if (teamId == null || projectedMinuteRows.isEmpty()) {
            return null;
        }
        return (int) projectedMinuteRows.stream()
                .limit(5)
                .filter(row -> injuryRows.stream().anyMatch(injury ->
                        teamId.equals(injury.teamId())
                                && row.playerId().equals(injury.playerId())
                                && gameDate.equals(injury.gameDate())
                                && missingStatus(injury.status())))
                .count();
    }

    private static Double teamRosterTurnoverScore(
            Long teamId,
            LocalDate gameDate,
            List<TransactionContextRow> transactionRows) {
        if (teamId == null) {
            return null;
        }
        long transactions = transactionRows.stream()
                .filter(row -> row.transactionDate() != null && row.transactionDate().isBefore(gameDate))
                .filter(row -> teamId.equals(row.fromTeamId()) || teamId.equals(row.toTeamId()))
                .count();
        return round(Math.min(1.0, transactions / 15.0));
    }

    private static VacatedContext vacatedContext(
            Long teamId,
            LocalDate gameDate,
            List<RosterAgeRow> rosterRows,
            List<TransactionContextRow> transactionRows) {
        if (teamId == null) {
            return new VacatedContext(null, null);
        }

        double minutesVacated = 0.0;
        double usageVacated = 0.0;
        for (TransactionContextRow transaction : transactionRows) {
            if (!teamId.equals(transaction.fromTeamId())
                    || transaction.transactionDate() == null
                    || !transaction.transactionDate().isBefore(gameDate)) {
                continue;
            }

            RosterAgeRow departedRoster = latestPlayerRosterBefore(
                    rosterRows,
                    transaction.playerId(),
                    transaction.transactionDate());
            Double projectedMinutes = departedRoster == null || departedRoster.projectedMinutes() == null
                    ? null
                    : departedRoster.projectedMinutes().doubleValue();
            if (projectedMinutes != null) {
                minutesVacated += projectedMinutes;
            }
            usageVacated += transaction.usagePercentageBeforeTransaction() == null
                    ? projectedMinutes == null ? 0.0 : projectedMinutes / 240.0
                    : transaction.usagePercentageBeforeTransaction().doubleValue();
        }

        return new VacatedContext(round(minutesVacated), round(Math.min(1.0, usageVacated)));
    }

    private static RosterAgeRow latestPlayerRosterBefore(
            List<RosterAgeRow> rosterRows,
            Long playerId,
            LocalDate transactionDate) {
        if (playerId == null || transactionDate == null) {
            return null;
        }
        return rosterRows.stream()
                .filter(row -> playerId.equals(row.playerId()))
                .filter(row -> !row.snapshotDate().isAfter(transactionDate))
                .filter(TeamFeatureSnapshotService::activeRosterStatus)
                .max(Comparator.comparing(RosterAgeRow::snapshotDate))
                .orElse(null);
    }

    private static boolean missingStatus(String status) {
        return status != null
                && (status.equalsIgnoreCase("OUT")
                || status.equalsIgnoreCase("DOUBTFUL"));
    }

    private static boolean activeRosterStatus(RosterAgeRow row) {
        String status = row.rosterStatus();
        return status == null
                || status.equalsIgnoreCase("ACTIVE")
                || status.equalsIgnoreCase("AVAILABLE")
                || status.equalsIgnoreCase("TWO_WAY");
    }

    private static Double averageAge(List<RosterAgeRow> rosterRows, LocalDate gameDate) {
        List<Double> ages = rosterRows.stream()
                .map(rosterRow -> (double) ChronoUnit.YEARS.between(rosterRow.birthDate(), gameDate))
                .toList();
        if (ages.isEmpty()) {
            return null;
        }
        return round(ages.stream().mapToDouble(Double::doubleValue).average().orElse(0));
    }

    private List<InjuryContextRow> loadInjuryContextRows() {
        return jdbcTemplate.query("""
                select team_id, player_id, game_date, injury_status
                from injury_reports
                where game_date is not null
                """, (rs, rowNum) -> new InjuryContextRow(
                nullableLong(rs, "team_id"),
                nullableLong(rs, "player_id"),
                rs.getDate("game_date").toLocalDate(),
                rs.getString("injury_status")));
    }

    private List<TransactionContextRow> loadTransactionContextRows() {
        return jdbcTemplate.query("""
                select t.player_id, t.from_team_id, t.to_team_id, t.transaction_date,
                       (
                           select avg(pa.usage_percentage)
                           from player_advanced_stats pa
                           join games usage_games on usage_games.game_id = pa.game_id
                           where pa.player_id = t.player_id
                             and pa.team_id = t.from_team_id
                             and t.transaction_date is not null
                             and usage_games.game_date < t.transaction_date
                       ) as usage_percentage_before_transaction
                from transactions t
                where transaction_date is not null
                """, (rs, rowNum) -> new TransactionContextRow(
                nullableLong(rs, "player_id"),
                nullableLong(rs, "from_team_id"),
                nullableLong(rs, "to_team_id"),
                rs.getDate("transaction_date").toLocalDate(),
                rs.getBigDecimal("usage_percentage_before_transaction")));
    }

    private Map<Long, List<TeamFeatureRow>> groupByTeam(List<TeamFeatureRow> rows) {
        Map<Long, List<TeamFeatureRow>> byTeam = rows.stream()
                .collect(Collectors.groupingBy(TeamFeatureRow::teamId, LinkedHashMap::new, Collectors.toList()));
        byTeam.values().forEach(teamRows -> teamRows.sort(Comparator.comparing(TeamFeatureRow::gameDateTime)
                .thenComparing(TeamFeatureRow::gameId)));
        return byTeam;
    }

    private List<TeamFeatureRow> opponentRowsBefore(Map<Long, List<TeamFeatureRow>> rowsByTeam, TeamFeatureRow target) {
        return rowsBefore(rowsByTeam, target.opponentTeamId(), target.gameDateTime());
    }

    private List<TeamFeatureRow> rowsBefore(Map<Long, List<TeamFeatureRow>> rowsByTeam, TeamFeatureRow target) {
        return rowsBefore(rowsByTeam, target.teamId(), target.gameDateTime());
    }

    private List<TeamFeatureRow> rowsBefore(Map<Long, List<TeamFeatureRow>> rowsByTeam, Long teamId, LocalDateTime gameDateTime) {
        return rowsByTeam.getOrDefault(teamId, List.of()).stream()
                .filter(row -> row.gameDateTime().isBefore(gameDateTime))
                .toList();
    }

    private TeamFeatureRow findByHome(List<TeamFeatureRow> rows, boolean home) {
        return rows.stream()
                .filter(row -> Boolean.valueOf(home).equals(row.home()))
                .findFirst()
                .orElse(null);
    }

    private void persistTeam(List<Object[]> batch) {
        if (batch.isEmpty()) {
            return;
        }

        String sql = isPostgres() ? """
                insert into team_feature_snapshots (snapshot_time, game_id, team_id, data_cutoff_time, features)
                values (?, ?, ?, ?, cast(? as jsonb))
                on conflict (snapshot_time, game_id, team_id) do update set
                    generated_at = now(),
                    data_cutoff_time = excluded.data_cutoff_time,
                    features = excluded.features
                """ : """
                insert into team_feature_snapshots (snapshot_time, game_id, team_id, data_cutoff_time, features)
                values (?, ?, ?, ?, ?)
                """;
        jdbcTemplate.batchUpdate(sql, batch);
    }

    private void persistGame(List<Object[]> batch) {
        if (batch.isEmpty()) {
            return;
        }

        String sql = isPostgres() ? """
                insert into game_feature_snapshots (snapshot_time, game_id, data_cutoff_time, features)
                values (?, ?, ?, cast(? as jsonb))
                on conflict (snapshot_time, game_id) do update set
                    generated_at = now(),
                    data_cutoff_time = excluded.data_cutoff_time,
                    features = excluded.features
                """ : """
                insert into game_feature_snapshots (snapshot_time, game_id, data_cutoff_time, features)
                values (?, ?, ?, ?)
                """;
        jdbcTemplate.batchUpdate(sql, batch);
    }

    private boolean isPostgres() {
        return Boolean.TRUE.equals(jdbcTemplate.execute((ConnectionCallback<Boolean>) connection ->
                connection.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT).contains("postgres")));
    }

    private String toJson(Map<String, Object> features) {
        try {
            return objectMapper.writeValueAsString(features);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not serialize feature snapshot", ex);
        }
    }

    private static void putPrefixed(Map<String, Object> target, String prefix, Map<String, Object> source) {
        source.forEach((key, value) -> target.put(prefix + key, value));
    }

    private static Double difference(Object left, Object right) {
        if (!(left instanceof Number leftNumber) || !(right instanceof Number rightNumber)) {
            return null;
        }
        return round(leftNumber.doubleValue() - rightNumber.doubleValue());
    }

    private static double round(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static Integer nullableInt(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private static Boolean nullableBoolean(ResultSet rs, String column) throws SQLException {
        boolean value = rs.getBoolean(column);
        return rs.wasNull() ? null : value;
    }

    private record RosterAgeRow(
            LocalDate snapshotDate,
            Long teamId,
            Long playerId,
            String rosterStatus,
            BigDecimal projectedMinutes,
            LocalDate birthDate) {
    }

    private record TeamAgeContext(
            Double averageTeamAge,
            Double starterAverageAge,
            Double rotationAverageAge,
            Boolean youngTeamFlag,
            Boolean veteranTeamFlag,
            Double teamRosterTurnoverScore,
            Double teamMinutesVacatedByDepartures,
            Double teamUsageVacatedByDepartures,
            Integer teamMissingStartersCount,
            Integer projectedStartersAvailableCount) {
    }

    private record InjuryContextRow(
            Long teamId,
            Long playerId,
            LocalDate gameDate,
            String status) {
    }

    private record TransactionContextRow(
            Long playerId,
            Long fromTeamId,
            Long toTeamId,
            LocalDate transactionDate,
            BigDecimal usagePercentageBeforeTransaction) {
    }

    private record VacatedContext(
            Double minutesVacated,
            Double usageVacated) {
    }
}
