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
    private static final int FIRST_NBA_SEASON = 1946;
    private static final Map<Integer, Map<Long, TeamRecord>> OFFICIAL_COMPLETED_RECORDS = Map.of(
            2024,
            Map.ofEntries(
                    Map.entry(1610612737L, new TeamRecord(40, 42)),
                    Map.entry(1610612738L, new TeamRecord(61, 21)),
                    Map.entry(1610612739L, new TeamRecord(64, 18)),
                    Map.entry(1610612740L, new TeamRecord(21, 61)),
                    Map.entry(1610612741L, new TeamRecord(39, 43)),
                    Map.entry(1610612742L, new TeamRecord(39, 43)),
                    Map.entry(1610612743L, new TeamRecord(50, 32)),
                    Map.entry(1610612744L, new TeamRecord(48, 34)),
                    Map.entry(1610612745L, new TeamRecord(52, 30)),
                    Map.entry(1610612746L, new TeamRecord(50, 32)),
                    Map.entry(1610612747L, new TeamRecord(50, 32)),
                    Map.entry(1610612748L, new TeamRecord(37, 45)),
                    Map.entry(1610612749L, new TeamRecord(48, 34)),
                    Map.entry(1610612750L, new TeamRecord(49, 33)),
                    Map.entry(1610612751L, new TeamRecord(26, 56)),
                    Map.entry(1610612752L, new TeamRecord(51, 31)),
                    Map.entry(1610612753L, new TeamRecord(41, 41)),
                    Map.entry(1610612754L, new TeamRecord(50, 32)),
                    Map.entry(1610612755L, new TeamRecord(24, 58)),
                    Map.entry(1610612756L, new TeamRecord(36, 46)),
                    Map.entry(1610612757L, new TeamRecord(36, 46)),
                    Map.entry(1610612758L, new TeamRecord(40, 42)),
                    Map.entry(1610612759L, new TeamRecord(34, 48)),
                    Map.entry(1610612760L, new TeamRecord(68, 14)),
                    Map.entry(1610612761L, new TeamRecord(30, 52)),
                    Map.entry(1610612762L, new TeamRecord(17, 65)),
                    Map.entry(1610612763L, new TeamRecord(48, 34)),
                    Map.entry(1610612764L, new TeamRecord(18, 64)),
                    Map.entry(1610612765L, new TeamRecord(44, 38)),
                    Map.entry(1610612766L, new TeamRecord(19, 63))));

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
                        ? "Roster-aware season range using schedule context"
                        : "Roster-aware season range using team strength",
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
        List<TeamProjectionResponse> unseeded = projectionTeams(season, scheduleAvailable).stream()
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
        int teamSourceSeason = sourceSeasonForTeam(team.teamId(), season, sourceSeason);
        TeamBaseline baseline = baseline(team.teamId(), teamSourceSeason);
        RosterImpact impact = rosterImpact(team.teamId(), season, teamSourceSeason);
        double rating = ((baseline.winPercentage() - 0.5) * 20.0)
                + baseline.pointDifferential()
                + impact.impactScore();
        double winPercentage = clamp(0.20, 0.78, 0.5 + (rating / 40.0));
        double projectedWins = winPercentage * FULL_SEASON_GAMES;
        double uncertainty = 5.5
                + (impact.turnoverScore() * 5.0)
                + (impact.injuryRiskScore() * 3.0)
                + (baseline.gamesPlayed() < 20 ? 4.0 : 0.0);
        List<String> reasons = reasons(baseline, impact, season <= FIRST_NBA_SEASON);
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
                teamSourceSeason,
                seasonLabel(teamSourceSeason),
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
                select home_team_id,
                       away_team_id,
                       case
                           when winner_team_id is not null then winner_team_id
                           when home_score is not null and away_score is not null and home_score > away_score then home_team_id
                           when home_score is not null and away_score is not null and away_score > home_score then away_team_id
                           else null
                       end as winner_team_id
                from games
                where season_start_year = ?
                  and home_team_id is not null
                  and away_team_id is not null
                  and lower(coalesce(game_type, '')) in ('regular season', 'nba emirates cup', 'in-season tournament')
                  and not (lower(coalesce(game_label, '')) like '%cup%' and lower(coalesce(game_sub_label, '')) = 'championship')
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
                    coalesce(sum(case
                        when g.winner_team_id = ? then 1
                        when g.winner_team_id is null and g.home_score is not null and g.away_score is not null
                             and ((g.home_team_id = ? and g.home_score > g.away_score)
                               or (g.away_team_id = ? and g.away_score > g.home_score)) then 1
                        else 0
                    end), 0) as wins,
                    coalesce(sum(case
                        when g.winner_team_id is not null and g.winner_team_id <> ? then 1
                        when g.winner_team_id is null and g.home_score is not null and g.away_score is not null
                             and ((g.home_team_id = ? and g.home_score < g.away_score)
                               or (g.away_team_id = ? and g.away_score < g.home_score)) then 1
                        else 0
                    end), 0) as losses,
                    coalesce(avg(case
                        when t.team_score is not null and t.opponent_score is not null
                        then t.team_score - t.opponent_score
                    end), 0) as point_differential
                from games g
                left join team_game_stats t on t.game_id = g.game_id and t.team_id = ?
                where g.season_start_year = ?
                  and lower(coalesce(g.game_type, '')) in ('regular season', 'nba emirates cup', 'in-season tournament')
                  and not (lower(coalesce(g.game_label, '')) like '%cup%' and lower(coalesce(g.game_sub_label, '')) = 'championship')
                  and (g.home_team_id = ? or g.away_team_id = ?)
                """, (rs, rowNum) -> {
            TeamRecord officialRecord = officialRecord(teamId, sourceSeason).orElse(null);
            int wins = officialRecord == null ? rs.getInt("wins") : officialRecord.wins();
            int losses = officialRecord == null ? rs.getInt("losses") : officialRecord.losses();
            int games = wins + losses;
            double winPercentage = games == 0 ? 0.5 : (double) wins / games;
            return new TeamBaseline(wins, losses, games, winPercentage, rs.getDouble("point_differential"));
        }, teamId, teamId, teamId, teamId, teamId, teamId, teamId, sourceSeason, teamId, teamId);
    }

    private RosterImpact rosterImpact(Long teamId, int season, int sourceSeason) {
        LocalDate fromDate = LocalDate.of(season, 6, 1);
        LocalDate throughDate = LocalDate.of(season + 1, 6, 30);
        List<TransactionRow> transactions = jdbcTemplate.query("""
                select t.player_id, t.from_team_id, t.to_team_id,
                       coalesce(nullif(trim(concat(coalesce(p.first_name, ''), ' ', coalesce(p.last_name, ''))), ''), cast(t.player_id as varchar)) as player_name
                from transactions t
                left join players p on p.player_id = t.player_id
                where t.transaction_date >= ?
                  and t.transaction_date <= ?
                  and (t.from_team_id = ? or t.to_team_id = ?)
                  and coalesce(t.affects_projection, true) = true
                  and lower(coalesce(t.source_status, 'official')) in ('official', 'trusted_report')
                  and coalesce(t.confidence, 1.0000) >= 0.7000
                order by t.transaction_date, t.id
                """, (rs, rowNum) -> new TransactionRow(
                nullableLong(rs, "player_id"),
                nullableLong(rs, "from_team_id"),
                nullableLong(rs, "to_team_id"),
                rs.getString("player_name")),
                fromDate, throughDate, teamId, teamId);
        int added = 0;
        int lost = 0;
        double incomingMinutes = 0;
        double outgoingMinutes = 0;
        double incomingImpact = 0;
        double outgoingImpact = 0;
        List<String> additions = new ArrayList<>();
        List<String> departures = new ArrayList<>();
        for (TransactionRow transaction : transactions) {
            if (transaction.playerId() == null) {
                continue;
            }
            PlayerImpact playerImpact = playerImpact(transaction.playerId(), sourceSeason);
            if (teamId.equals(transaction.toTeamId())) {
                added++;
                incomingMinutes += playerImpact.minutes();
                incomingImpact += playerImpact.rating();
                additions.add(transaction.playerName());
            }
            if (teamId.equals(transaction.fromTeamId())) {
                lost++;
                outgoingMinutes += playerImpact.minutes();
                outgoingImpact += playerImpact.rating();
                departures.add(transaction.playerName());
            }
        }
        RookieImpact rookieImpact = rookieImpact(teamId, season);
        int injuryFlags = injuryFlagCount(teamId, fromDate, throughDate);
        double turnoverScore = clamp(0, 1, (added + lost + rookieImpact.count()) / 15.0);
        double injuryRisk = clamp(0, 1, injuryFlags / 5.0);
        double impactScore = (incomingImpact - outgoingImpact)
                + rookieImpact.score()
                - (injuryRisk * 2.0);
        List<String> explanations = new ArrayList<>();
        if (!additions.isEmpty()) {
            explanations.add(additions.size() <= 3
                    ? "Added " + String.join(", ", additions)
                    : added + " confirmed roster additions");
        }
        if (!departures.isEmpty()) {
            explanations.add(departures.size() <= 3
                    ? "Lost " + String.join(", ", departures)
                    : lost + " confirmed departures");
        }
        explanations.addAll(rookieImpact.explanations());
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
        List<DraftPickRow> picks = jdbcTemplate.query("""
                select d.player_id, d.draft_round, d.draft_number,
                       coalesce(nullif(trim(concat(coalesce(p.first_name, ''), ' ', coalesce(p.last_name, ''))), ''), null) as player_name
                from draft_picks d
                left join players p on p.player_id = d.player_id
                where team_id = ?
                  and rookie_season_start_year = ?
                order by draft_number nulls last, id
                """, (rs, rowNum) -> new DraftPickRow(
                nullableLong(rs, "player_id"),
                nullableInt(rs, "draft_round"),
                nullableInt(rs, "draft_number"),
                rs.getString("player_name")), teamId, season);
        double score = 0;
        List<String> explanations = new ArrayList<>();
        for (DraftPickRow pick : picks) {
            int number = pick.draftNumber() == null ? 99 : pick.draftNumber();
            if (number == 1) {
                score += 2.8;
                explanations.add("Added No. 1 pick" + named(pick.playerName()));
            } else if (number <= 5) {
                score += 2.0;
                explanations.add("Added top-five pick" + named(pick.playerName()));
            } else if (number <= 14) {
                score += 1.2;
                explanations.add("Added lottery pick" + named(pick.playerName()));
            } else if (Integer.valueOf(1).equals(pick.draftRound())) {
                score += 0.6;
                explanations.add("Added first-round rookie" + named(pick.playerName()));
            } else {
                score += 0.2;
            }
        }
        return new RookieImpact(picks.size(), score, explanations);
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

    private PlayerImpact playerImpact(Long playerId, int sourceSeason) {
        return jdbcTemplate.queryForObject("""
                select count(*) as games_played,
                       coalesce(avg(p.num_minutes), 0) as minutes,
                       coalesce(avg(p.points), 0) as points,
                       coalesce(avg(p.rebounds_total), 0) as rebounds,
                       coalesce(avg(p.assists), 0) as assists,
                       coalesce(avg(p.steals), 0) as steals,
                       coalesce(avg(p.blocks), 0) as blocks,
                       coalesce(avg(p.turnovers), 0) as turnovers
                from player_game_stats p
                join games g on g.game_id = p.game_id
                where p.player_id = ?
                  and p.num_minutes is not null
                  and g.season_start_year = ?
                  and lower(coalesce(g.game_type, '')) in ('regular season', 'nba emirates cup', 'in-season tournament')
                  and not (lower(coalesce(g.game_label, '')) like '%cup%' and lower(coalesce(g.game_sub_label, '')) = 'championship')
                """, (rs, rowNum) -> {
            int gamesPlayed = rs.getInt("games_played");
            double minutes = rs.getDouble("minutes");
            if (gamesPlayed == 0) {
                return new PlayerImpact(0, 0);
            }
            double rating = (rs.getDouble("points") * 0.18)
                    + (rs.getDouble("rebounds") * 0.10)
                    + (rs.getDouble("assists") * 0.13)
                    + (rs.getDouble("steals") * 0.45)
                    + (rs.getDouble("blocks") * 0.35)
                    - (rs.getDouble("turnovers") * 0.12)
                    + (minutes * 0.03);
            return new PlayerImpact(minutes, clamp(0, 8.5, rating));
        }, playerId, sourceSeason);
    }

    private List<String> reasons(TeamBaseline baseline, RosterImpact impact, boolean noPreviousSeason) {
        List<String> reasons = new ArrayList<>();
        if (baseline.gamesPlayed() == 0) {
            reasons.add(noPreviousSeason
                    ? "Previous season record: No previous season record"
                    : "Previous season record: No previous season record");
            reasons.add(noPreviousSeason
                    ? "First NBA season in the imported data"
                    : "No recent regular-season sample found, using league-average baseline");
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

    private List<TeamSeed> projectionTeams(int season, boolean scheduleAvailable) {
        if (!scheduleAvailable) {
            return currentTeams();
        }
        List<TeamSeed> teams = jdbcTemplate.query("""
                select distinct t.team_id, t.city, t.name, t.abbreviation
                from teams t
                join (
                    select home_team_id as team_id
                    from games
                    where season_start_year = ?
                      and home_team_id is not null
                      and lower(coalesce(game_type, '')) in ('regular season', 'nba emirates cup', 'in-season tournament')
                      and not (lower(coalesce(game_label, '')) like '%cup%' and lower(coalesce(game_sub_label, '')) = 'championship')
                    union
                    select away_team_id as team_id
                    from games
                    where season_start_year = ?
                      and away_team_id is not null
                      and lower(coalesce(game_type, '')) in ('regular season', 'nba emirates cup', 'in-season tournament')
                      and not (lower(coalesce(game_label, '')) like '%cup%' and lower(coalesce(game_sub_label, '')) = 'championship')
                ) season_teams on season_teams.team_id = t.team_id
                order by t.name, t.city
                """, (rs, rowNum) -> new TeamSeed(
                rs.getLong("team_id"),
                rs.getString("city") + " " + rs.getString("name"),
                rs.getString("abbreviation"),
                Optional.ofNullable(FranchiseMetadata.conference(rs.getLong("team_id"))).orElse("Western")), season, season);
        return teams.isEmpty() ? currentTeams() : teams;
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
                  and not (lower(coalesce(game_label, '')) like '%cup%' and lower(coalesce(game_sub_label, '')) = 'championship')
                """, Integer.class, season);
        return source == null ? season - 1 : source;
    }

    private int sourceSeasonForTeam(Long teamId, int season, int fallback) {
        Integer source = jdbcTemplate.queryForObject("""
                select max(season_start_year)
                from games
                where season_start_year < ?
                  and lower(coalesce(game_type, '')) in ('regular season', 'nba emirates cup', 'in-season tournament')
                  and not (lower(coalesce(game_label, '')) like '%cup%' and lower(coalesce(game_sub_label, '')) = 'championship')
                  and (home_team_id = ? or away_team_id = ?)
                """, Integer.class, season, teamId, teamId);
        return source == null ? fallback : source;
    }

    private static Optional<TeamRecord> officialRecord(Long teamId, int sourceSeason) {
        return Optional.ofNullable(OFFICIAL_COMPLETED_RECORDS.getOrDefault(sourceSeason, Map.of()).get(teamId));
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
                ? "Roster-aware projection with schedule context"
                : "Roster-aware projection before schedule release";
    }

    private static String seasonLabel(int season) {
        return season + "-" + (season + 1);
    }

    private static String signed(double value) {
        return value > 0 ? "+" + value : String.valueOf(value);
    }

    private static String named(String playerName) {
        return playerName == null || playerName.isBlank() ? "" : " " + playerName;
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

    private static Integer nullableInt(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        int value = rs.getInt(column);
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

    private record TeamRecord(int wins, int losses) {
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

    private record PlayerImpact(double minutes, double rating) {
    }

    private record RookieImpact(int count, double score, List<String> explanations) {
    }

    private record DraftPickRow(Long playerId, Integer draftRound, Integer draftNumber, String playerName) {
    }

    private record TransactionRow(
            Long playerId,
            Long fromTeamId,
            Long toTeamId,
            String playerName) {
    }

    private record GameScheduleRow(Long homeTeamId, Long awayTeamId, Long winnerTeamId) {
    }

    private static final class ScheduleRecord {
        private int listedGames;
        private double wins;
        private double losses;
    }
}
