package com.armandorodriguez.nba_premier_predictor.service;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
import com.armandorodriguez.nba_premier_predictor.feature.PlayerFeatureCalculator;
import com.armandorodriguez.nba_premier_predictor.feature.PlayerFeatureRow;
import com.armandorodriguez.nba_premier_predictor.feature.TeamFeatureRow;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class PlayerFeatureSnapshotService {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final PlayerFeatureCalculator calculator;

    public PlayerFeatureSnapshotService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper, PlayerFeatureCalculator calculator) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.calculator = calculator;
    }

    @Transactional
    public FeatureGenerationResponse generate(Integer seasonStartYear) {
        List<PlayerFeatureRow> playerRows = loadPlayerRows(seasonStartYear);
        Map<Long, List<TeamFeatureRow>> teamRows = loadTeamRows(seasonStartYear).stream()
                .collect(Collectors.groupingBy(TeamFeatureRow::teamId, LinkedHashMap::new, Collectors.toList()));

        List<Object[]> batch = new ArrayList<>();
        Map<Long, List<PlayerFeatureRow>> byPlayer = playerRows.stream()
                .collect(Collectors.groupingBy(PlayerFeatureRow::playerId, LinkedHashMap::new, Collectors.toList()));

        for (List<PlayerFeatureRow> rows : byPlayer.values()) {
            rows.sort(Comparator.comparing(PlayerFeatureRow::gameDateTime));
            List<PlayerFeatureRow> prior = new ArrayList<>();
            for (PlayerFeatureRow row : rows) {
                LocalDateTime dataCutoff = row.gameDateTime().minusSeconds(1);
                batch.add(new Object[] {
                        Timestamp.valueOf(dataCutoff),
                        row.gameId(),
                        row.playerId(),
                        row.teamId(),
                        Timestamp.valueOf(dataCutoff),
                        toJson(calculator.calculate(row, prior, opponentRowsBefore(teamRows, row)))
                });
                prior.add(row);
            }
        }

        persist(batch);
        return new FeatureGenerationResponse("player", seasonStartYear, batch.size());
    }

    private List<PlayerFeatureRow> loadPlayerRows(Integer seasonStartYear) {
        List<PlayerFeatureRow> rows = jdbcTemplate.query("""
                select s.game_id, s.player_id, s.team_id, s.opponent_team_id, g.season_start_year,
                       g.game_date_time_est, s.home, s.num_minutes, s.points, s.rebounds_total,
                       s.assists, s.turnovers, s.steals, s.blocks, p.birth_date, p.from_year,
                       (
                           select count(*)
                           from player_game_stats prior_stats
                           join games prior_games on prior_games.game_id = prior_stats.game_id
                           where prior_stats.player_id = s.player_id
                             and prior_games.game_date_time_est < g.game_date_time_est
                       ) as career_games_played_before_game,
                       (
                           select coalesce(sum(prior_stats.num_minutes), 0)
                           from player_game_stats prior_stats
                           join games prior_games on prior_games.game_id = prior_stats.game_id
                           where prior_stats.player_id = s.player_id
                             and prior_games.game_date_time_est < g.game_date_time_est
                       ) as career_minutes_played_before_game,
                       (
                           select count(*)
                           from injury_reports injuries
                           where injuries.player_id = s.player_id
                             and coalesce(injuries.game_date, injuries.report_date) < g.game_date
                       ) as injury_history_count_before_game
                from player_game_stats s
                join games g on g.game_id = s.game_id
                join players p on p.player_id = s.player_id
                where (? is null or g.season_start_year = ?)
                  and g.game_date_time_est is not null
                order by s.player_id, g.game_date_time_est
                """, this::mapPlayerRow, seasonStartYear, seasonStartYear);
        Map<Long, List<RosterContextRow>> rosterRows = loadRosterContextRows().stream()
                .collect(Collectors.groupingBy(RosterContextRow::teamId, LinkedHashMap::new, Collectors.toList()));
        rosterRows.values().forEach(teamRows -> teamRows.sort(Comparator.comparing(RosterContextRow::snapshotDate)));
        List<InjuryContextRow> injuryRows = loadInjuryContextRows();
        List<TransactionContextRow> transactionRows = loadTransactionContextRows();
        return rows.stream()
                .map(row -> withContext(row,
                        rosterRows.getOrDefault(row.teamId(), List.of()),
                        injuryRows,
                        transactionRows))
                .toList();
    }

    private List<TeamFeatureRow> loadTeamRows(Integer seasonStartYear) {
        return jdbcTemplate.query("""
                select t.game_id, t.team_id, t.opponent_team_id, g.season_start_year,
                       g.game_date_time_est, t.home, t.team_score, t.opponent_score,
                       t.assists, t.rebounds_total, t.turnovers
                from team_game_stats t
                join games g on g.game_id = t.game_id
                where (? is null or g.season_start_year = ?)
                  and g.game_date_time_est is not null
                order by t.team_id, g.game_date_time_est
                """, this::mapTeamRow, seasonStartYear, seasonStartYear);
    }

    private PlayerFeatureRow mapPlayerRow(ResultSet rs, int rowNum) throws SQLException {
        return new PlayerFeatureRow(
                rs.getLong("game_id"),
                rs.getLong("player_id"),
                nullableLong(rs, "team_id"),
                nullableLong(rs, "opponent_team_id"),
                nullableInt(rs, "season_start_year"),
                rs.getTimestamp("game_date_time_est").toLocalDateTime(),
                nullableBoolean(rs, "home"),
                rs.getBigDecimal("num_minutes"),
                nullableInt(rs, "points"),
                nullableInt(rs, "rebounds_total"),
                nullableInt(rs, "assists"),
                nullableInt(rs, "turnovers"),
                nullableInt(rs, "steals"),
                nullableInt(rs, "blocks"),
                nullableDate(rs, "birth_date"),
                nullableInt(rs, "from_year"),
                nullableInt(rs, "career_games_played_before_game"),
                rs.getBigDecimal("career_minutes_played_before_game"),
                nullableInt(rs, "injury_history_count_before_game"),
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

    private PlayerFeatureRow withContext(
            PlayerFeatureRow row,
            List<RosterContextRow> teamRosterRows,
            List<InjuryContextRow> injuryRows,
            List<TransactionContextRow> transactionRows) {
        LocalDate gameDate = row.gameDateTime().toLocalDate();
        List<RosterContextRow> currentRoster = latestRoster(teamRosterRows, gameDate);
        RosterContextRow playerRoster = currentRoster.stream()
                .filter(rosterRow -> rosterRow.playerId().equals(row.playerId()))
                .findFirst()
                .orElse(null);
        Integer missingStarters = missingStarterCount(row.teamId(), gameDate, currentRoster, injuryRows);
        Boolean projectedStarter = playerRoster == null || playerRoster.projectedMinutes() == null
                ? null
                : playerRoster.projectedMinutes().doubleValue() >= 28.0;
        Double teamRosterTurnoverScore = teamRosterTurnoverScore(row.teamId(), gameDate, transactionRows);
        VacatedContext vacatedContext = vacatedContext(row.teamId(), gameDate, teamRosterRows, transactionRows);
        return new PlayerFeatureRow(
                row.gameId(),
                row.playerId(),
                row.teamId(),
                row.opponentTeamId(),
                row.seasonStartYear(),
                row.gameDateTime(),
                row.home(),
                row.minutes(),
                row.points(),
                row.rebounds(),
                row.assists(),
                row.turnovers(),
                row.steals(),
                row.blocks(),
                row.birthDate(),
                row.fromYear(),
                row.careerGamesPlayedBeforeGame(),
                row.careerMinutesPlayedBeforeGame(),
                row.injuryHistoryCountBeforeGame(),
                projectedStarter,
                playerChangedTeamBeforeGame(row.playerId(), gameDate, transactionRows),
                samePositionCompetition(playerRoster, currentRoster),
                missingStarters,
                teamRosterTurnoverScore,
                vacatedContext.minutesVacated(),
                vacatedContext.usageVacated(),
                missingStarters == null ? null : round(missingStarters * (Boolean.TRUE.equals(projectedStarter) ? 0.08 : 0.04)),
                missingStarters == null ? null : round(missingStarters * (Boolean.TRUE.equals(projectedStarter) ? 2.5 : 1.25)));
    }

    private List<RosterContextRow> latestRoster(List<RosterContextRow> rows, LocalDate gameDate) {
        LocalDate latestSnapshot = rows.stream()
                .map(RosterContextRow::snapshotDate)
                .filter(snapshotDate -> !snapshotDate.isAfter(gameDate))
                .max(LocalDate::compareTo)
                .orElse(null);
        if (latestSnapshot == null) {
            return List.of();
        }
        return rows.stream()
                .filter(row -> latestSnapshot.equals(row.snapshotDate()))
                .filter(PlayerFeatureSnapshotService::activeRosterStatus)
                .toList();
    }

    private static Integer samePositionCompetition(RosterContextRow playerRoster, List<RosterContextRow> rosterRows) {
        if (playerRoster == null || playerRoster.position() == null) {
            return null;
        }
        return (int) rosterRows.stream()
                .filter(row -> !row.playerId().equals(playerRoster.playerId()))
                .filter(row -> playerRoster.position().equalsIgnoreCase(row.position()))
                .filter(row -> row.projectedMinutes() != null && row.projectedMinutes().doubleValue() >= 10.0)
                .count();
    }

    private static Integer missingStarterCount(
            Long teamId,
            LocalDate gameDate,
            List<RosterContextRow> rosterRows,
            List<InjuryContextRow> injuryRows) {
        if (teamId == null || rosterRows.isEmpty()) {
            return null;
        }
        return (int) rosterRows.stream()
                .filter(row -> row.projectedMinutes() != null && row.projectedMinutes().doubleValue() >= 28.0)
                .filter(row -> injuryRows.stream().anyMatch(injury ->
                        teamId.equals(injury.teamId())
                                && row.playerId().equals(injury.playerId())
                                && gameDate.equals(injury.gameDate())
                                && missingStatus(injury.status())))
                .count();
    }

    private static boolean playerChangedTeamBeforeGame(
            Long playerId,
            LocalDate gameDate,
            List<TransactionContextRow> transactionRows) {
        return transactionRows.stream()
                .anyMatch(row -> playerId.equals(row.playerId())
                        && row.transactionDate() != null
                        && row.transactionDate().isBefore(gameDate));
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
            List<RosterContextRow> rosterRows,
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

            RosterContextRow departedRoster = latestPlayerRosterBefore(
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

    private static RosterContextRow latestPlayerRosterBefore(
            List<RosterContextRow> rosterRows,
            Long playerId,
            LocalDate transactionDate) {
        if (playerId == null || transactionDate == null) {
            return null;
        }
        return rosterRows.stream()
                .filter(row -> playerId.equals(row.playerId()))
                .filter(row -> !row.snapshotDate().isAfter(transactionDate))
                .filter(PlayerFeatureSnapshotService::activeRosterStatus)
                .max(Comparator.comparing(RosterContextRow::snapshotDate))
                .orElse(null);
    }

    private static boolean activeRosterStatus(RosterContextRow row) {
        String status = row.rosterStatus();
        return status == null
                || status.equalsIgnoreCase("ACTIVE")
                || status.equalsIgnoreCase("AVAILABLE")
                || status.equalsIgnoreCase("TWO_WAY");
    }

    private static boolean missingStatus(String status) {
        return status != null
                && (status.equalsIgnoreCase("OUT")
                || status.equalsIgnoreCase("DOUBTFUL"));
    }

    private List<RosterContextRow> loadRosterContextRows() {
        return jdbcTemplate.query("""
                select snapshot_date, team_id, player_id, position, roster_status, projected_minutes
                from roster_snapshots
                order by team_id, snapshot_date, player_id
                """, (rs, rowNum) -> new RosterContextRow(
                rs.getDate("snapshot_date").toLocalDate(),
                rs.getLong("team_id"),
                rs.getLong("player_id"),
                rs.getString("position"),
                rs.getString("roster_status"),
                rs.getBigDecimal("projected_minutes")));
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

    private List<TeamFeatureRow> opponentRowsBefore(Map<Long, List<TeamFeatureRow>> rowsByTeam, PlayerFeatureRow target) {
        List<TeamFeatureRow> rows = rowsByTeam.getOrDefault(target.opponentTeamId(), List.of());
        return rows.stream()
                .filter(row -> row.gameDateTime().isBefore(target.gameDateTime()))
                .toList();
    }

    private void persist(List<Object[]> batch) {
        if (batch.isEmpty()) {
            return;
        }

        String sql = isPostgres() ? """
                insert into player_feature_snapshots (snapshot_time, game_id, player_id, team_id, data_cutoff_time, features)
                values (?, ?, ?, ?, ?, cast(? as jsonb))
                on conflict (snapshot_time, game_id, player_id) do update set
                    team_id = excluded.team_id,
                    generated_at = now(),
                    data_cutoff_time = excluded.data_cutoff_time,
                    features = excluded.features
                """ : """
                insert into player_feature_snapshots (snapshot_time, game_id, player_id, team_id, data_cutoff_time, features)
                values (?, ?, ?, ?, ?, ?)
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
            throw new IllegalStateException("Could not serialize player feature snapshot", ex);
        }
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

    private static java.time.LocalDate nullableDate(ResultSet rs, String column) throws SQLException {
        java.sql.Date value = rs.getDate(column);
        return value == null ? null : value.toLocalDate();
    }

    private static double round(double value) {
        return BigDecimal.valueOf(value).setScale(2, java.math.RoundingMode.HALF_UP).doubleValue();
    }

    private record RosterContextRow(
            LocalDate snapshotDate,
            Long teamId,
            Long playerId,
            String position,
            String rosterStatus,
            BigDecimal projectedMinutes) {
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
