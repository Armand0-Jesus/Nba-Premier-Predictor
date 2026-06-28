package com.armandorodriguez.nba_premier_predictor.ingestion;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.armandorodriguez.nba_premier_predictor.util.NbaSeasonResolver;

@Service
public class HistoricalDataImportService {

    private static final int BATCH_SIZE = 1_000;
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final JdbcTemplate jdbcTemplate;

    public HistoricalDataImportService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public ImportResult importFrom(Path source) throws IOException {
        Long refreshId = startRefresh();
        try {
            int teams = importTeams(source);
            Set<Long> validTeamIds = loadExistingIds("teams", "team_id");
            int players = importPlayers(source);
            Set<Long> validPlayerIds = loadExistingIds("players", "player_id");
            int seasons = importSeasons(source);
            int games = importGames(source, validTeamIds);
            Set<Long> validGameIds = loadExistingIds("games", "game_id");
            int teamStats = importTeamStats(source, validGameIds, validTeamIds);
            int playerStats = importPlayerStats(source, validGameIds, validTeamIds, validPlayerIds);
            int total = teams + players + seasons + games + teamStats + playerStats;
            finishRefresh(refreshId, "success", total, null);
            return new ImportResult(teams, players, seasons, games, teamStats, playerStats);
        } catch (IOException | RuntimeException ex) {
            finishRefresh(refreshId, "failed", 0, ex.getMessage());
            throw ex;
        }
    }

    private int importTeams(Path source) throws IOException {
        String sql = """
                insert into teams (team_id, city, name, abbreviation, season_founded, season_active_till, league, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, now())
                on conflict (team_id) do update set
                    city = excluded.city,
                    name = excluded.name,
                    abbreviation = excluded.abbreviation,
                    season_founded = excluded.season_founded,
                    season_active_till = excluded.season_active_till,
                    league = excluded.league,
                    updated_at = now()
                where excluded.season_active_till >= teams.season_active_till
                """;
        List<Object[]> batch = new ArrayList<>(BATCH_SIZE);
        int[] count = {0};

        readCsv(source, "TeamHistories.csv", row -> {
            batch.add(new Object[] {
                    row.longValue("teamId"),
                    row.string("teamCity"),
                    row.string("teamName"),
                    row.string("teamAbbrev"),
                    row.intValue("seasonFounded"),
                    row.intValue("seasonActiveTill"),
                    row.string("league")
            });
            count[0]++;
            flushIfFull(sql, batch);
        });

        flush(sql, batch);
        logSource("TeamHistories.csv", count[0]);
        return count[0];
    }

    private int importPlayers(Path source) throws IOException {
        String sql = """
                insert into players (
                    player_id, first_name, last_name, birth_date, school, country, height_inches, body_weight_lbs,
                    jersey, is_guard, is_forward, is_center, dleague_flag, nba_flag, games_played_flag,
                    draft_year, draft_round, draft_number, from_year, to_year, updated_at
                )
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now())
                on conflict (player_id) do update set
                    first_name = excluded.first_name,
                    last_name = excluded.last_name,
                    birth_date = excluded.birth_date,
                    school = excluded.school,
                    country = excluded.country,
                    height_inches = excluded.height_inches,
                    body_weight_lbs = excluded.body_weight_lbs,
                    jersey = excluded.jersey,
                    is_guard = excluded.is_guard,
                    is_forward = excluded.is_forward,
                    is_center = excluded.is_center,
                    dleague_flag = excluded.dleague_flag,
                    nba_flag = excluded.nba_flag,
                    games_played_flag = excluded.games_played_flag,
                    draft_year = excluded.draft_year,
                    draft_round = excluded.draft_round,
                    draft_number = excluded.draft_number,
                    from_year = excluded.from_year,
                    to_year = excluded.to_year,
                    updated_at = now()
                """;
        List<Object[]> batch = new ArrayList<>(BATCH_SIZE);
        int[] count = {0};

        readCsv(source, "Players.csv", row -> {
            String firstName = firstNonNull(row.string("firstName"), row.string("lastName"), "Unknown");
            batch.add(new Object[] {
                    row.longValue("personId"),
                    firstName,
                    row.string("lastName"),
                    sqlDate(row.string("birthDate")),
                    row.string("school"),
                    row.string("country"),
                    row.intValue("heightInches"),
                    row.intValue("bodyWeightLbs"),
                    row.string("jersey"),
                    Boolean.TRUE.equals(row.flag("guard")),
                    Boolean.TRUE.equals(row.flag("forward")),
                    Boolean.TRUE.equals(row.flag("center")),
                    Boolean.TRUE.equals(row.flag("dleagueFlag")),
                    Boolean.TRUE.equals(row.flag("nbaFlag")),
                    Boolean.TRUE.equals(row.flag("gamesPlayedFlag")),
                    positiveOrNull(row.intValue("draftYear")),
                    positiveOrNull(row.intValue("draftRound")),
                    positiveOrNull(row.intValue("draftNumber")),
                    positiveOrNull(row.intValue("fromYear")),
                    positiveOrNull(row.intValue("toYear"))
            });
            count[0]++;
            flushIfFull(sql, batch);
        });

        flush(sql, batch);
        logSource("Players.csv", count[0]);
        return count[0];
    }

    private int importSeasons(Path source) throws IOException {
        Set<Integer> seasons = new HashSet<>();
        readCsv(source, "Games.csv", row -> {
            Integer season = NbaSeasonResolver.seasonStartYear(gameDate(row));
            if (season != null) {
                seasons.add(season);
            }
        });

        String sql = """
                insert into seasons (season_start_year, label, starts_on, ends_on)
                values (?, ?, ?, ?)
                on conflict (season_start_year) do nothing
                """;
        List<Object[]> batch = new ArrayList<>(seasons.size());
        for (Integer season : seasons) {
            batch.add(new Object[] {
                    season,
                    season + "-" + String.format("%02d", (season + 1) % 100),
                    Date.valueOf(LocalDate.of(season, 7, 1)),
                    Date.valueOf(LocalDate.of(season + 1, 6, 30))
            });
        }
        flush(sql, batch);
        return seasons.size();
    }

    private int importGames(Path source, Set<Long> validTeamIds) throws IOException {
        String sql = """
                insert into games (
                    game_id, season_start_year, game_date_time_est, game_date, home_team_id, away_team_id,
                    home_team_city, home_team_name, away_team_city, away_team_name, home_score, away_score,
                    winner_team_id, game_type, game_subtype, game_label, game_sub_label, series_game_number,
                    attendance, arena_id, arena_name, arena_city, arena_state, officials, updated_at
                )
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now())
                on conflict (game_id) do update set
                    season_start_year = excluded.season_start_year,
                    game_date_time_est = excluded.game_date_time_est,
                    game_date = excluded.game_date,
                    home_team_id = excluded.home_team_id,
                    away_team_id = excluded.away_team_id,
                    home_team_city = excluded.home_team_city,
                    home_team_name = excluded.home_team_name,
                    away_team_city = excluded.away_team_city,
                    away_team_name = excluded.away_team_name,
                    home_score = excluded.home_score,
                    away_score = excluded.away_score,
                    winner_team_id = excluded.winner_team_id,
                    game_type = excluded.game_type,
                    game_subtype = excluded.game_subtype,
                    game_label = excluded.game_label,
                    game_sub_label = excluded.game_sub_label,
                    series_game_number = excluded.series_game_number,
                    attendance = excluded.attendance,
                    arena_id = excluded.arena_id,
                    arena_name = excluded.arena_name,
                    arena_city = excluded.arena_city,
                    arena_state = excluded.arena_state,
                    officials = excluded.officials,
                    updated_at = now()
                """;
        List<Object[]> batch = new ArrayList<>(BATCH_SIZE);
        int[] count = {0};

        readCsv(source, "Games.csv", row -> {
            Long homeTeamId = row.longValue("hometeamId");
            Long awayTeamId = row.longValue("awayteamId");
            if (!validTeamIds.contains(homeTeamId) || !validTeamIds.contains(awayTeamId)) {
                return;
            }
            LocalDateTime gameDateTime = gameDateTime(row, "gameDateTimeEst");
            LocalDate gameDate = gameDate(row);
            batch.add(new Object[] {
                    row.longValue("gameId"),
                    NbaSeasonResolver.seasonStartYear(gameDate),
                    timestamp(gameDateTime),
                    date(gameDate),
                    homeTeamId,
                    awayTeamId,
                    row.string("hometeamCity"),
                    row.string("hometeamName"),
                    row.string("awayteamCity"),
                    row.string("awayteamName"),
                    row.intValue("homeScore"),
                    row.intValue("awayScore"),
                    row.longValue("winner"),
                    row.string("gameType"),
                    row.string("gameSubtype"),
                    row.string("gameLabel"),
                    row.string("gameSubLabel"),
                    row.optionalIntValue("seriesGameNumber"),
                    row.optionalIntValue("attendance"),
                    row.optionalLongValue("arenaId"),
                    row.string("arenaName"),
                    row.string("arenaCity"),
                    row.string("arenaState"),
                    row.string("officials")
            });
            count[0]++;
            flushIfFull(sql, batch);
        });

        flush(sql, batch);
        logSource("Games.csv", count[0]);
        return count[0];
    }

    private int importTeamStats(Path source, Set<Long> validGameIds, Set<Long> validTeamIds) throws IOException {
        String sql = """
                insert into team_game_stats (
                    game_id, team_id, opponent_team_id, home, win, team_score, opponent_score, assists, blocks,
                    steals, field_goals_attempted, field_goals_made, field_goals_percentage,
                    three_pointers_attempted, three_pointers_made, three_pointers_percentage,
                    free_throws_attempted, free_throws_made, free_throws_percentage,
                    rebounds_defensive, rebounds_offensive, rebounds_total, fouls_personal, turnovers,
                    plus_minus_points, num_minutes, q1_points, q2_points, q3_points, q4_points, updated_at
                )
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now())
                on conflict (game_id, team_id) do update set
                    opponent_team_id = excluded.opponent_team_id,
                    home = excluded.home,
                    win = excluded.win,
                    team_score = excluded.team_score,
                    opponent_score = excluded.opponent_score,
                    assists = excluded.assists,
                    blocks = excluded.blocks,
                    steals = excluded.steals,
                    field_goals_attempted = excluded.field_goals_attempted,
                    field_goals_made = excluded.field_goals_made,
                    field_goals_percentage = excluded.field_goals_percentage,
                    three_pointers_attempted = excluded.three_pointers_attempted,
                    three_pointers_made = excluded.three_pointers_made,
                    three_pointers_percentage = excluded.three_pointers_percentage,
                    free_throws_attempted = excluded.free_throws_attempted,
                    free_throws_made = excluded.free_throws_made,
                    free_throws_percentage = excluded.free_throws_percentage,
                    rebounds_defensive = excluded.rebounds_defensive,
                    rebounds_offensive = excluded.rebounds_offensive,
                    rebounds_total = excluded.rebounds_total,
                    fouls_personal = excluded.fouls_personal,
                    turnovers = excluded.turnovers,
                    plus_minus_points = excluded.plus_minus_points,
                    num_minutes = excluded.num_minutes,
                    q1_points = excluded.q1_points,
                    q2_points = excluded.q2_points,
                    q3_points = excluded.q3_points,
                    q4_points = excluded.q4_points,
                    updated_at = now()
                """;
        List<Object[]> batch = new ArrayList<>(BATCH_SIZE);
        int[] count = {0};

        readCsv(source, "TeamStatistics.csv", row -> {
            Long gameId = row.longValue("gameId");
            Long teamId = row.longValue("teamId");
            Long opponentTeamId = row.longValue("opponentTeamId");
            if (!validGameIds.contains(gameId) || !knownOrNull(validTeamIds, teamId) || !knownOrNull(validTeamIds, opponentTeamId)) {
                return;
            }
            batch.add(new Object[] {
                    gameId,
                    teamId,
                    opponentTeamId,
                    row.flag("home"),
                    row.flag("win"),
                    row.intValue("teamScore"),
                    row.intValue("opponentScore"),
                    row.intValue("assists"),
                    row.intValue("blocks"),
                    row.intValue("steals"),
                    row.intValue("fieldGoalsAttempted"),
                    row.intValue("fieldGoalsMade"),
                    row.decimalValue("fieldGoalsPercentage"),
                    row.intValue("threePointersAttempted"),
                    row.intValue("threePointersMade"),
                    row.decimalValue("threePointersPercentage"),
                    row.intValue("freeThrowsAttempted"),
                    row.intValue("freeThrowsMade"),
                    row.decimalValue("freeThrowsPercentage"),
                    row.intValue("reboundsDefensive"),
                    row.intValue("reboundsOffensive"),
                    row.intValue("reboundsTotal"),
                    row.intValue("foulsPersonal"),
                    row.intValue("turnovers"),
                    row.intValue("plusMinusPoints"),
                    row.decimalValue("numMinutes"),
                    row.intValue("q1Points"),
                    row.intValue("q2Points"),
                    row.intValue("q3Points"),
                    row.intValue("q4Points")
            });
            count[0]++;
            flushIfFull(sql, batch);
        });

        flush(sql, batch);
        logSource("TeamStatistics.csv", count[0]);
        return count[0];
    }

    private int importPlayerStats(
            Path source,
            Set<Long> validGameIds,
            Set<Long> validTeamIds,
            Set<Long> validPlayerIds) throws IOException {
        String sql = """
                insert into player_game_stats (
                    game_id, player_id, team_id, opponent_team_id, win, home, num_minutes, points, assists,
                    blocks, steals, field_goals_attempted, field_goals_made, field_goals_percentage,
                    three_pointers_attempted, three_pointers_made, three_pointers_percentage,
                    free_throws_attempted, free_throws_made, free_throws_percentage,
                    rebounds_defensive, rebounds_offensive, rebounds_total, fouls_personal, turnovers,
                    plus_minus_points, comment, starting_position, updated_at
                )
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now())
                on conflict (game_id, player_id, team_id) do update set
                    opponent_team_id = excluded.opponent_team_id,
                    win = excluded.win,
                    home = excluded.home,
                    num_minutes = excluded.num_minutes,
                    points = excluded.points,
                    assists = excluded.assists,
                    blocks = excluded.blocks,
                    steals = excluded.steals,
                    field_goals_attempted = excluded.field_goals_attempted,
                    field_goals_made = excluded.field_goals_made,
                    field_goals_percentage = excluded.field_goals_percentage,
                    three_pointers_attempted = excluded.three_pointers_attempted,
                    three_pointers_made = excluded.three_pointers_made,
                    three_pointers_percentage = excluded.three_pointers_percentage,
                    free_throws_attempted = excluded.free_throws_attempted,
                    free_throws_made = excluded.free_throws_made,
                    free_throws_percentage = excluded.free_throws_percentage,
                    rebounds_defensive = excluded.rebounds_defensive,
                    rebounds_offensive = excluded.rebounds_offensive,
                    rebounds_total = excluded.rebounds_total,
                    fouls_personal = excluded.fouls_personal,
                    turnovers = excluded.turnovers,
                    plus_minus_points = excluded.plus_minus_points,
                    comment = excluded.comment,
                    starting_position = excluded.starting_position,
                    updated_at = now()
                """;
        List<Object[]> batch = new ArrayList<>(BATCH_SIZE);
        int[] count = {0};

        readCsv(source, "PlayerStatistics.csv", row -> {
            Long gameId = row.longValue("gameId");
            Long playerId = row.longValue("personId");
            Long teamId = row.longValue("playerteamId");
            Long opponentTeamId = row.longValue("opponentteamId");
            if (!validGameIds.contains(gameId)
                    || !validPlayerIds.contains(playerId)
                    || !knownOrNull(validTeamIds, teamId)
                    || !knownOrNull(validTeamIds, opponentTeamId)) {
                return;
            }
            batch.add(new Object[] {
                    gameId,
                    playerId,
                    teamId,
                    opponentTeamId,
                    row.flag("win"),
                    row.flag("home"),
                    row.decimalValue("numMinutes"),
                    row.intValue("points"),
                    row.intValue("assists"),
                    row.intValue("blocks"),
                    row.intValue("steals"),
                    row.intValue("fieldGoalsAttempted"),
                    row.intValue("fieldGoalsMade"),
                    row.decimalValue("fieldGoalsPercentage"),
                    row.intValue("threePointersAttempted"),
                    row.intValue("threePointersMade"),
                    row.decimalValue("threePointersPercentage"),
                    row.intValue("freeThrowsAttempted"),
                    row.intValue("freeThrowsMade"),
                    row.decimalValue("freeThrowsPercentage"),
                    row.intValue("reboundsDefensive"),
                    row.intValue("reboundsOffensive"),
                    row.intValue("reboundsTotal"),
                    row.intValue("foulsPersonal"),
                    row.intValue("turnovers"),
                    row.intValue("plusMinusPoints"),
                    row.string("comment"),
                    row.string("startingPosition")
            });
            count[0]++;
            flushIfFull(sql, batch);
        });

        flush(sql, batch);
        logSource("PlayerStatistics.csv", count[0]);
        return count[0];
    }

    private Long startRefresh() {
        return jdbcTemplate.queryForObject(
                "insert into refresh_logs (refresh_type, status) values (?, ?) returning id",
                Long.class,
                "historical_import",
                "running");
    }

    private void finishRefresh(Long refreshId, String status, int recordsProcessed, String errorMessage) {
        jdbcTemplate.update(
                "update refresh_logs set finished_at = now(), status = ?, records_processed = ?, error_message = ? where id = ?",
                status,
                recordsProcessed,
                errorMessage,
                refreshId);
    }

    private void logSource(String fileName, int recordsProcessed) {
        jdbcTemplate.update("""
                insert into data_source_logs (source_name, source_type, source_path, status, records_processed)
                values (?, ?, ?, ?, ?)
                """, fileName, "kaggle_csv", fileName, "success", recordsProcessed);
    }

    private Set<Long> loadExistingIds(String tableName, String columnName) {
        return new HashSet<>(jdbcTemplate.queryForList("select " + columnName + " from " + tableName, Long.class));
    }

    private void readCsv(Path source, String fileName, CsvRowReader.CsvRowConsumer consumer) throws IOException {
        withReader(source, fileName, reader -> CsvRowReader.read(reader, consumer));
    }

    private void withReader(Path source, String fileName, ReaderConsumer consumer) throws IOException {
        if (Files.isDirectory(source)) {
            try (BufferedReader reader = Files.newBufferedReader(source.resolve(fileName), StandardCharsets.UTF_8)) {
                consumer.accept(reader);
            }
            return;
        }

        try (ZipFile zipFile = new ZipFile(source.toFile())) {
            ZipEntry entry = zipFile.getEntry(fileName);
            if (entry == null) {
                throw new FileNotFoundException(fileName + " not found in " + source);
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(zipFile.getInputStream(entry), StandardCharsets.UTF_8))) {
                consumer.accept(reader);
            }
        }
    }

    private void flushIfFull(String sql, List<Object[]> batch) {
        if (batch.size() >= BATCH_SIZE) {
            flush(sql, batch);
        }
    }

    private void flush(String sql, List<Object[]> batch) {
        if (!batch.isEmpty()) {
            jdbcTemplate.batchUpdate(sql, batch);
            batch.clear();
        }
    }

    private static Timestamp timestamp(LocalDateTime value) {
        return value == null ? null : Timestamp.valueOf(value);
    }

    private static Date date(LocalDate value) {
        return value == null ? null : Date.valueOf(value);
    }

    private static Date sqlDate(String value) {
        return value == null ? null : Date.valueOf(LocalDate.parse(value));
    }

    private static LocalDate gameDate(CsvRow row) {
        String value = firstNonNull(row.string("gameDate"), row.string("gameDateTimeEst"));
        if (value == null) {
            return null;
        }
        return LocalDate.parse(value.substring(0, 10));
    }

    private static LocalDateTime gameDateTime(CsvRow row, String column) {
        String value = row.string(column);
        if (value == null) {
            return null;
        }
        return LocalDateTime.parse(value.substring(0, 19).replace('T', ' '), DATE_TIME);
    }

    private static Integer positiveOrNull(Integer value) {
        return value == null || value < 0 ? null : value;
    }

    private static boolean knownOrNull(Set<Long> knownIds, Long value) {
        return value == null || knownIds.contains(value);
    }

    @SafeVarargs
    private static <T> T firstNonNull(T... values) {
        for (T value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    public record ImportResult(
            int teams,
            int players,
            int seasons,
            int games,
            int teamStats,
            int playerStats) {
    }

    @FunctionalInterface
    private interface ReaderConsumer {
        void accept(BufferedReader reader) throws IOException;
    }
}
