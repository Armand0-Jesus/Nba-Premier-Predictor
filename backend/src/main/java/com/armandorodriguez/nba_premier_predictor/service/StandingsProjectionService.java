package com.armandorodriguez.nba_premier_predictor.service;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.armandorodriguez.nba_premier_predictor.dto.RosterImpactResponse;
import com.armandorodriguez.nba_premier_predictor.dto.SeasonSimulationResponse;
import com.armandorodriguez.nba_premier_predictor.dto.StandingsProjectionResponse;
import com.armandorodriguez.nba_premier_predictor.dto.TeamProjectionResponse;
import com.armandorodriguez.nba_premier_predictor.exception.ResourceNotFoundException;

@Service
public class StandingsProjectionService {

    private static final int FULL_SEASON_GAMES = 82;

    private final JdbcTemplate jdbcTemplate;

    public StandingsProjectionService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public StandingsProjectionResponse projections(Integer requestedSeason) {
        int season = requestedSeason == null ? defaultProjectionSeason() : requestedSeason;
        ProjectionBuild build = buildProjection(season);
        persistProjection(build);
        return response(build);
    }

    @Transactional
    public SeasonSimulationResponse simulate(Integer requestedSeason, Integer requestedRuns) {
        int season = requestedSeason == null ? defaultProjectionSeason() : requestedSeason;
        int runs = requestedRuns == null ? 1000 : Math.max(100, Math.min(requestedRuns, 10000));
        ProjectionBuild build = buildProjection(season);
        persistProjection(build);

        Random random = new Random(season * 31L + runs);
        List<TeamProjectionResponse> simulated = build.rows().stream()
                .map(row -> simulateRow(row, runs, random))
                .sorted(Comparator.comparing(TeamProjectionResponse::projectedWins).reversed())
                .toList();
        Map<String, List<TeamProjectionResponse>> seeded = seedByConference(simulated);
        List<TeamProjectionResponse> allRows = new ArrayList<>();
        allRows.addAll(seeded.getOrDefault("Eastern", List.of()));
        allRows.addAll(seeded.getOrDefault("Western", List.of()));

        Long runId = insertSimulationRun(season, runs, build.scheduleAvailable());
        for (TeamProjectionResponse row : allRows) {
            jdbcTemplate.update("""
                    insert into projected_team_records (
                        simulation_run_id, team_id, expected_wins, expected_losses,
                        low_wins, median_wins, high_wins, conference_seed, playoff_probability
                    ) values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    runId,
                    row.teamId(),
                    row.projectedWins(),
                    row.projectedLosses(),
                    row.lowWins(),
                    row.medianWins(),
                    row.highWins(),
                    row.projectedSeed(),
                    row.playoffProbability());
        }

        return new SeasonSimulationResponse(
                runId,
                season,
                runs,
                build.scheduleAvailable(),
                build.generatedAt(),
                build.scheduleAvailable()
                        ? "Schedule-aware Monte Carlo projection using current team strength"
                        : "Schedule-free Monte Carlo projection using team strength and roster context",
                allRows);
    }

    @Transactional
    public TeamProjectionResponse teamProjection(Long teamId, Integer requestedSeason) {
        return allRows(projections(requestedSeason)).stream()
                .filter(row -> row.teamId().equals(teamId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Team projection not found: " + teamId));
    }

    public RosterImpactResponse rosterImpact(Long teamId, Integer requestedSeason) {
        int season = requestedSeason == null ? defaultProjectionSeason() : requestedSeason;
        TeamSeed team = findTeam(teamId);
        RosterImpact impact = rosterImpact(teamId, season, sourceSeason(season));
        return new RosterImpactResponse(
                season,
                teamId,
                team.fullName(),
                impact.playersAdded(),
                impact.playersLost(),
                impact.rookieCount(),
                impact.injuryFlagCount(),
                round(impact.incomingMinutes()),
                round(impact.outgoingMinutes()),
                round(impact.impactScore()),
                round(impact.turnoverScore()),
                impact.explanations());
    }

    private ProjectionBuild buildProjection(int season) {
        int sourceSeason = sourceSeason(season);
        boolean scheduleAvailable = hasSchedule(season);
        Instant generatedAt = Instant.now();
        List<TeamProjectionResponse> unseeded = currentTeams().stream()
                .map(team -> projectionRow(team, season, sourceSeason))
                .toList();
        if (scheduleAvailable) {
            unseeded = applyScheduleContext(season, unseeded);
        }
        Map<String, List<TeamProjectionResponse>> seeded = seedByConference(unseeded);
        List<TeamProjectionResponse> rows = new ArrayList<>();
        rows.addAll(seeded.getOrDefault("Eastern", List.of()));
        rows.addAll(seeded.getOrDefault("Western", List.of()));
        return new ProjectionBuild(season, sourceSeason, generatedAt, scheduleAvailable, rows);
    }

    private TeamProjectionResponse projectionRow(TeamSeed team, int season, int sourceSeason) {
        TeamBaseline baseline = baseline(team.teamId(), sourceSeason);
        RosterImpact impact = rosterImpact(team.teamId(), season, sourceSeason);
        double rating = ((baseline.winPercentage() - 0.5) * 20.0)
                + baseline.pointDifferential()
                + impact.impactScore();
        double winPercentage = clamp(0.20, 0.78, 0.5 + (rating / 40.0));
        double projectedWins = winPercentage * FULL_SEASON_GAMES;
        double uncertainty = 5.5
                + (impact.turnoverScore() * 5.0)
                + (impact.injuryRiskScore() * 3.0)
                + (baseline.gamesPlayed() < 20 ? 4.0 : 0.0);
        List<String> reasons = reasons(baseline, impact);
        List<String> uncertaintyFactors = uncertaintyFactors(baseline, impact);
        return new TeamProjectionResponse(
                team.teamId(),
                team.fullName(),
                team.abbreviation(),
                team.conference(),
                null,
                round(projectedWins),
                round(FULL_SEASON_GAMES - projectedWins),
                round(clamp(0, FULL_SEASON_GAMES, projectedWins - uncertainty)),
                round(projectedWins),
                round(clamp(0, FULL_SEASON_GAMES, projectedWins + uncertainty)),
                null,
                round(rating),
                round(impact.impactScore()),
                round(impact.turnoverScore()),
                round(impact.injuryRiskScore()),
                sourceSeason,
                seasonLabel(sourceSeason),
                reasons,
                uncertaintyFactors);
    }

    private Map<String, List<TeamProjectionResponse>> seedByConference(List<TeamProjectionResponse> rows) {
        Map<String, List<TeamProjectionResponse>> grouped = new LinkedHashMap<>();
        grouped.put("Eastern", seed(rows, "Eastern"));
        grouped.put("Western", seed(rows, "Western"));
        return grouped;
    }

    private List<TeamProjectionResponse> seed(List<TeamProjectionResponse> rows, String conference) {
        List<TeamProjectionResponse> sorted = rows.stream()
                .filter(row -> conference.equals(row.conference()))
                .sorted(Comparator.comparing(TeamProjectionResponse::projectedWins).reversed()
                        .thenComparing(TeamProjectionResponse::strengthRating, Comparator.reverseOrder())
                        .thenComparing(TeamProjectionResponse::teamName))
                .toList();
        List<TeamProjectionResponse> seeded = new ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            TeamProjectionResponse row = sorted.get(i);
            int seed = i + 1;
            seeded.add(withSeedAndProbability(row, seed));
        }
        return seeded;
    }

    private TeamProjectionResponse withSeedAndProbability(TeamProjectionResponse row, int seed) {
        double probability = 1.0 / (1.0 + Math.exp(-((row.projectedWins() - 41.0) / 5.5)));
        if (seed <= 6) {
            probability = Math.max(probability, 0.72);
        } else if (seed <= 10) {
            probability = Math.max(probability, 0.38);
        } else {
            probability = Math.min(probability, 0.35);
        }
        return new TeamProjectionResponse(
                row.teamId(),
                row.teamName(),
                row.abbreviation(),
                row.conference(),
                seed,
                row.projectedWins(),
                row.projectedLosses(),
                row.lowWins(),
                row.medianWins(),
                row.highWins(),
                round(clamp(0.02, 0.98, probability)),
                row.strengthRating(),
                row.rosterImpactScore(),
                row.rosterTurnoverScore(),
                row.injuryRiskScore(),
                row.sourceSeasonStartYear(),
                row.sourceSeasonLabel(),
                row.topReasons(),
                row.uncertaintyFactors());
    }

    private TeamProjectionResponse simulateRow(TeamProjectionResponse row, int runs, Random random) {
        double standardDeviation = Math.max(2.5, (row.highWins() - row.lowWins()) / 4.0);
        List<Double> samples = new ArrayList<>(runs);
        for (int i = 0; i < runs; i++) {
            samples.add(clamp(0, FULL_SEASON_GAMES, row.projectedWins() + random.nextGaussian() * standardDeviation));
        }
        samples.sort(Double::compareTo);
        double average = samples.stream().mapToDouble(Double::doubleValue).average().orElse(row.projectedWins());
        double low = samples.get(Math.max(0, (int) Math.floor(runs * 0.10) - 1));
        double median = samples.get(Math.max(0, (int) Math.floor(runs * 0.50) - 1));
        double high = samples.get(Math.max(0, (int) Math.floor(runs * 0.90) - 1));
        return new TeamProjectionResponse(
                row.teamId(),
                row.teamName(),
                row.abbreviation(),
                row.conference(),
                row.projectedSeed(),
                round(average),
                round(FULL_SEASON_GAMES - average),
                round(low),
                round(median),
                round(high),
                row.playoffProbability(),
                row.strengthRating(),
                row.rosterImpactScore(),
                row.rosterTurnoverScore(),
                row.injuryRiskScore(),
                row.sourceSeasonStartYear(),
                row.sourceSeasonLabel(),
                row.topReasons(),
                row.uncertaintyFactors());
    }

    private List<TeamProjectionResponse> applyScheduleContext(int season, List<TeamProjectionResponse> rows) {
        Map<Long, TeamProjectionResponse> byTeam = new LinkedHashMap<>();
        Map<Long, ScheduleRecord> records = new LinkedHashMap<>();
        for (TeamProjectionResponse row : rows) {
            byTeam.put(row.teamId(), row);
            records.put(row.teamId(), new ScheduleRecord());
        }
        for (GameScheduleRow game : scheduledGames(season)) {
            TeamProjectionResponse home = byTeam.get(game.homeTeamId());
            TeamProjectionResponse away = byTeam.get(game.awayTeamId());
            if (home == null || away == null) {
                continue;
            }
            ScheduleRecord homeRecord = records.get(home.teamId());
            ScheduleRecord awayRecord = records.get(away.teamId());
            homeRecord.listedGames++;
            awayRecord.listedGames++;
            if (game.winnerTeamId() != null) {
                boolean homeWon = game.winnerTeamId().equals(home.teamId());
                homeRecord.wins += homeWon ? 1 : 0;
                homeRecord.losses += homeWon ? 0 : 1;
                awayRecord.wins += homeWon ? 0 : 1;
                awayRecord.losses += homeWon ? 1 : 0;
            } else {
                double homeWinProbability = homeWinProbability(home.strengthRating(), away.strengthRating());
                homeRecord.wins += homeWinProbability;
                homeRecord.losses += 1 - homeWinProbability;
                awayRecord.wins += 1 - homeWinProbability;
                awayRecord.losses += homeWinProbability;
            }
        }
        return rows.stream()
                .map(row -> withScheduleProjection(row, records.get(row.teamId())))
                .toList();
    }

    private TeamProjectionResponse withScheduleProjection(TeamProjectionResponse row, ScheduleRecord record) {
        if (record == null || record.listedGames == 0) {
            return row;
        }
        double baseWinPercentage = row.projectedWins() / FULL_SEASON_GAMES;
        int missingGames = Math.max(0, FULL_SEASON_GAMES - record.listedGames);
        double projectedWins = record.wins + (missingGames * baseWinPercentage);
        double projectedLosses = record.losses + (missingGames * (1 - baseWinPercentage));
        double uncertainty = Math.max(3.5, (row.highWins() - row.lowWins()) * 0.35);
        List<String> reasons = new ArrayList<>();
        reasons.add("Schedule context included for " + record.listedGames + " listed game" + plural(record.listedGames));
        reasons.addAll(row.topReasons());
        List<String> uncertaintyFactors = new ArrayList<>(row.uncertaintyFactors());
        if (record.listedGames < FULL_SEASON_GAMES) {
            uncertaintyFactors.add("Partial schedule keeps the range wider");
        }
        return new TeamProjectionResponse(
                row.teamId(),
                row.teamName(),
                row.abbreviation(),
                row.conference(),
                row.projectedSeed(),
                round(projectedWins),
                round(projectedLosses),
                round(clamp(0, FULL_SEASON_GAMES, projectedWins - uncertainty)),
                round(projectedWins),
                round(clamp(0, FULL_SEASON_GAMES, projectedWins + uncertainty)),
                row.playoffProbability(),
                row.strengthRating(),
                row.rosterImpactScore(),
                row.rosterTurnoverScore(),
                row.injuryRiskScore(),
                row.sourceSeasonStartYear(),
                row.sourceSeasonLabel(),
                reasons,
                uncertaintyFactors);
    }

    private List<GameScheduleRow> scheduledGames(int season) {
        return jdbcTemplate.query("""
                select home_team_id, away_team_id, winner_team_id
                from games
                where season_start_year = ?
                  and home_team_id is not null
                  and away_team_id is not null
                  and lower(coalesce(game_type, '')) in ('regular season', 'nba emirates cup', 'in-season tournament')
                order by game_date_time_est, game_id
                """, (rs, rowNum) -> new GameScheduleRow(
                nullableLong(rs, "home_team_id"),
                nullableLong(rs, "away_team_id"),
                nullableLong(rs, "winner_team_id")), season);
    }

    private double homeWinProbability(double homeRating, double awayRating) {
        double edge = (homeRating - awayRating + 2.0) / 8.0;
        return clamp(0.15, 0.85, 1.0 / (1.0 + Math.exp(-edge)));
    }

    private TeamBaseline baseline(Long teamId, int sourceSeason) {
        return jdbcTemplate.queryForObject("""
                select
                    coalesce(sum(case when g.winner_team_id = ? then 1 else 0 end), 0) as wins,
                    coalesce(sum(case when g.winner_team_id is not null and g.winner_team_id <> ? then 1 else 0 end), 0) as losses,
                    coalesce(avg(case
                        when t.team_score is not null and t.opponent_score is not null
                        then t.team_score - t.opponent_score
                    end), 0) as point_differential
                from games g
                left join team_game_stats t on t.game_id = g.game_id and t.team_id = ?
                where g.season_start_year = ?
                  and lower(coalesce(g.game_type, '')) in ('regular season', 'nba emirates cup', 'in-season tournament')
                  and (g.home_team_id = ? or g.away_team_id = ?)
                """, (rs, rowNum) -> {
            int wins = rs.getInt("wins");
            int losses = rs.getInt("losses");
            int games = wins + losses;
            double winPercentage = games == 0 ? 0.5 : (double) wins / games;
            return new TeamBaseline(wins, losses, games, winPercentage, rs.getDouble("point_differential"));
        }, teamId, teamId, teamId, sourceSeason, teamId, teamId);
    }

    private RosterImpact rosterImpact(Long teamId, int season, int sourceSeason) {
        LocalDate fromDate = LocalDate.of(season, 6, 1);
        LocalDate throughDate = LocalDate.of(season + 1, 6, 30);
        List<TransactionRow> transactions = jdbcTemplate.query("""
                select player_id, from_team_id, to_team_id, transaction_type, transaction_date
                from transactions
                where transaction_date >= ?
                  and transaction_date <= ?
                  and (from_team_id = ? or to_team_id = ?)
                order by transaction_date, id
                """, (rs, rowNum) -> new TransactionRow(
                nullableLong(rs, "player_id"),
                nullableLong(rs, "from_team_id"),
                nullableLong(rs, "to_team_id"),
                rs.getString("transaction_type"),
                rs.getDate("transaction_date") == null ? null : rs.getDate("transaction_date").toLocalDate()),
                fromDate, throughDate, teamId, teamId);
        int added = 0;
        int lost = 0;
        double incomingMinutes = 0;
        double outgoingMinutes = 0;
        for (TransactionRow transaction : transactions) {
            if (transaction.playerId() == null) {
                continue;
            }
            double minutes = playerAverageMinutes(transaction.playerId(), sourceSeason);
            if (teamId.equals(transaction.toTeamId())) {
                added++;
                incomingMinutes += minutes;
            }
            if (teamId.equals(transaction.fromTeamId())) {
                lost++;
                outgoingMinutes += minutes;
            }
        }
        RookieImpact rookieImpact = rookieImpact(teamId, season);
        int injuryFlags = injuryFlagCount(teamId, fromDate, throughDate);
        double turnoverScore = clamp(0, 1, (added + lost + rookieImpact.count()) / 15.0);
        double injuryRisk = clamp(0, 1, injuryFlags / 5.0);
        double impactScore = ((incomingMinutes - outgoingMinutes) / 240.0) * 8.0
                + rookieImpact.score()
                - (injuryRisk * 2.0);
        List<String> explanations = new ArrayList<>();
        if (added > 0) {
            explanations.add(added + " confirmed roster addition" + plural(added));
        }
        if (lost > 0) {
            explanations.add(lost + " confirmed departure" + plural(lost));
        }
        if (rookieImpact.count() > 0) {
            explanations.add(rookieImpact.count() + " rookie addition" + plural(rookieImpact.count()));
        }
        if (injuryFlags > 0) {
            explanations.add(injuryFlags + " availability flag" + plural(injuryFlags) + " in current context");
        }
        if (explanations.isEmpty()) {
            explanations.add("No major confirmed roster movement found for this projection window");
        }
        return new RosterImpact(
                added,
                lost,
                rookieImpact.count(),
                injuryFlags,
                incomingMinutes,
                outgoingMinutes,
                impactScore,
                turnoverScore,
                injuryRisk,
                explanations);
    }

    private RookieImpact rookieImpact(Long teamId, int season) {
        return jdbcTemplate.queryForObject("""
                select count(*) as rookie_count,
                       coalesce(sum(case
                           when draft_number between 1 and 14 then 1.2
                           when draft_round = 1 then 0.6
                           else 0.2
                       end), 0) as rookie_score
                from draft_picks
                where team_id = ?
                  and rookie_season_start_year = ?
                """, (rs, rowNum) -> new RookieImpact(rs.getInt("rookie_count"), rs.getDouble("rookie_score")), teamId, season);
    }

    private int injuryFlagCount(Long teamId, LocalDate fromDate, LocalDate throughDate) {
        return jdbcTemplate.queryForObject("""
                select count(*)
                from injury_reports
                where team_id = ?
                  and report_date >= ?
                  and report_date <= ?
                  and lower(coalesce(injury_status, '')) in ('out', 'doubtful', 'questionable')
                """, Integer.class, teamId, fromDate, throughDate);
    }

    private double playerAverageMinutes(Long playerId, int sourceSeason) {
        Double value = jdbcTemplate.queryForObject("""
                select avg(p.num_minutes)
                from player_game_stats p
                join games g on g.game_id = p.game_id
                where p.player_id = ?
                  and p.num_minutes is not null
                  and g.season_start_year = ?
                  and lower(coalesce(g.game_type, '')) in ('regular season', 'nba emirates cup', 'in-season tournament')
                """, Double.class, playerId, sourceSeason);
        return value == null ? 0 : value;
    }

    private List<String> reasons(TeamBaseline baseline, RosterImpact impact) {
        List<String> reasons = new ArrayList<>();
        if (baseline.gamesPlayed() == 0) {
            reasons.add("No recent regular-season sample found, using league-average baseline");
        } else {
            reasons.add("Previous season record: " + baseline.wins() + "-" + baseline.losses());
            reasons.add("Previous season point differential: " + signed(round(baseline.pointDifferential())));
        }
        if (Math.abs(impact.impactScore()) >= 0.4) {
            reasons.add("Roster movement impact: " + signed(round(impact.impactScore())) + " rating points");
        }
        reasons.addAll(impact.explanations());
        return reasons;
    }

    private List<String> uncertaintyFactors(TeamBaseline baseline, RosterImpact impact) {
        List<String> factors = new ArrayList<>();
        if (baseline.gamesPlayed() < 20) {
            factors.add("Limited recent team sample");
        }
        if (impact.turnoverScore() > 0.25) {
            factors.add("Roster turnover adds projection uncertainty");
        }
        if (impact.injuryRiskScore() > 0) {
            factors.add("Availability flags could shift the win range");
        }
        if (factors.isEmpty()) {
            factors.add("Normal season variance");
        }
        return factors;
    }

    private void persistProjection(ProjectionBuild build) {
        for (TeamProjectionResponse row : build.rows()) {
            jdbcTemplate.update("delete from team_strength_ratings where season_start_year = ? and team_id = ?", build.season(), row.teamId());
            jdbcTemplate.update("""
                    insert into team_strength_ratings (
                        season_start_year, team_id, source_season_start_year, rating,
                        base_win_percentage, point_differential, roster_impact_score,
                        injury_risk_score, explanation
                    ) values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    build.season(),
                    row.teamId(),
                    row.sourceSeasonStartYear(),
                    row.strengthRating(),
                    null,
                    null,
                    row.rosterImpactScore(),
                    row.injuryRiskScore(),
                    String.join(" | ", row.topReasons()));

            jdbcTemplate.update("delete from standings_projections where season_start_year = ? and team_id = ?", build.season(), row.teamId());
            jdbcTemplate.update("""
                    insert into standings_projections (
                        season_start_year, team_id, conference, projected_wins,
                        projected_losses, low_wins, median_wins, high_wins,
                        projected_seed, playoff_probability, projection_method,
                        schedule_available, top_reasons, uncertainty_factors
                    ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    build.season(),
                    row.teamId(),
                    row.conference(),
                    row.projectedWins(),
                    row.projectedLosses(),
                    row.lowWins(),
                    row.medianWins(),
                    row.highWins(),
                    row.projectedSeed(),
                    row.playoffProbability(),
                    projectionMethod(build.scheduleAvailable()),
                    build.scheduleAvailable(),
                    String.join(" | ", row.topReasons()),
                    String.join(" | ", row.uncertaintyFactors()));

            jdbcTemplate.update("delete from team_context_snapshots where season_start_year = ? and team_id = ? and snapshot_date = ?",
                    build.season(), row.teamId(), LocalDate.now());
            jdbcTemplate.update("""
                    insert into team_context_snapshots (
                        season_start_year, team_id, snapshot_date, roster_turnover_score,
                        injury_risk_score, team_strength_rating, context_summary
                    ) values (?, ?, ?, ?, ?, ?, ?)
                    """,
                    build.season(),
                    row.teamId(),
                    LocalDate.now(),
                    row.rosterTurnoverScore(),
                    row.injuryRiskScore(),
                    row.strengthRating(),
                    String.join(" | ", row.topReasons()));
        }
    }

    private Long insertSimulationRun(int season, int runs, boolean scheduleAvailable) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    insert into season_simulation_runs (
                        season_start_year, run_count, schedule_available, notes
                    ) values (?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setInt(1, season);
            ps.setInt(2, runs);
            ps.setBoolean(3, scheduleAvailable);
            ps.setString(4, scheduleAvailable
                    ? "Schedule-aware Monte Carlo projection"
                    : "Schedule-free Monte Carlo projection");
            return ps;
        }, keyHolder);
        return generatedId(keyHolder);
    }

    private StandingsProjectionResponse response(ProjectionBuild build) {
        Map<String, List<TeamProjectionResponse>> grouped = seedByConference(build.rows());
        return new StandingsProjectionResponse(
                build.season(),
                seasonLabel(build.season()),
                build.generatedAt(),
                build.scheduleAvailable(),
                projectionMethod(build.scheduleAvailable()),
                grouped.getOrDefault("Eastern", List.of()),
                grouped.getOrDefault("Western", List.of()));
    }

    private List<TeamProjectionResponse> allRows(StandingsProjectionResponse response) {
        List<TeamProjectionResponse> rows = new ArrayList<>();
        rows.addAll(response.easternConference());
        rows.addAll(response.westernConference());
        return rows;
    }

    private List<TeamSeed> currentTeams() {
        List<Long> ids = TeamService.currentNbaTeamIds().stream().sorted().toList();
        String placeholders = String.join(",", ids.stream().map(id -> "?").toList());
        List<Object> params = new ArrayList<>(ids);
        return jdbcTemplate.query("""
                select team_id, city, name, abbreviation
                from teams
                where team_id in (%s)
                order by name, city
                """.formatted(placeholders), (rs, rowNum) -> new TeamSeed(
                rs.getLong("team_id"),
                rs.getString("city") + " " + rs.getString("name"),
                rs.getString("abbreviation"),
                Optional.ofNullable(FranchiseMetadata.conference(rs.getLong("team_id"))).orElse("Western")), params.toArray());
    }

    private TeamSeed findTeam(Long teamId) {
        return currentTeams().stream()
                .filter(team -> team.teamId().equals(teamId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Current NBA team not found: " + teamId));
    }

    private int defaultProjectionSeason() {
        Integer latestSeason = jdbcTemplate.queryForObject("select max(season_start_year) from games", Integer.class);
        if (latestSeason != null) {
            return latestSeason + 1;
        }
        LocalDate today = LocalDate.now();
        return today.getMonthValue() >= 7 ? today.getYear() : today.getYear() - 1;
    }

    private int sourceSeason(int season) {
        Integer source = jdbcTemplate.queryForObject("""
                select max(season_start_year)
                from games
                where season_start_year < ?
                  and lower(coalesce(game_type, '')) in ('regular season', 'nba emirates cup', 'in-season tournament')
                """, Integer.class, season);
        return source == null ? season - 1 : source;
    }

    private boolean hasSchedule(int season) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from games where season_start_year = ?",
                Integer.class,
                season);
        return count != null && count > 0;
    }

    private static String projectionMethod(boolean scheduleAvailable) {
        return scheduleAvailable
                ? "Schedule-aware team strength projection"
                : "Schedule-free team strength projection";
    }

    private static String seasonLabel(int season) {
        return season + "-" + (season + 1);
    }

    private static String signed(double value) {
        return value > 0 ? "+" + value : String.valueOf(value);
    }

    private static String plural(int value) {
        return value == 1 ? "" : "s";
    }

    private static double clamp(double min, double max, double value) {
        return Math.max(min, Math.min(max, value));
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static Long generatedId(KeyHolder keyHolder) {
        Map<String, Object> keys = keyHolder.getKeys();
        if (keys != null && keys.get("id") instanceof Number id) {
            return id.longValue();
        }
        if (keys != null && keys.get("ID") instanceof Number id) {
            return id.longValue();
        }
        return keyHolder.getKey().longValue();
    }

    private static Long nullableLong(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private record ProjectionBuild(
            int season,
            int sourceSeason,
            Instant generatedAt,
            boolean scheduleAvailable,
            List<TeamProjectionResponse> rows) {
    }

    private record TeamSeed(Long teamId, String fullName, String abbreviation, String conference) {
    }

    private record TeamBaseline(int wins, int losses, int gamesPlayed, double winPercentage, double pointDifferential) {
    }

    private record RosterImpact(
            int playersAdded,
            int playersLost,
            int rookieCount,
            int injuryFlagCount,
            double incomingMinutes,
            double outgoingMinutes,
            double impactScore,
            double turnoverScore,
            double injuryRiskScore,
            List<String> explanations) {
    }

    private record RookieImpact(int count, double score) {
    }

    private record TransactionRow(
            Long playerId,
            Long fromTeamId,
            Long toTeamId,
            String type,
            LocalDate date) {
    }

    private record GameScheduleRow(Long homeTeamId, Long awayTeamId, Long winnerTeamId) {
    }

    private static final class ScheduleRecord {
        private int listedGames;
        private double wins;
        private double losses;
    }
}
