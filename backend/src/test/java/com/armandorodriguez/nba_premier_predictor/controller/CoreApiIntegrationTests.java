package com.armandorodriguez.nba_premier_predictor.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.nullValue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest
@Sql(scripts = {"/test-cleanup.sql", "/test-data.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class CoreApiIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearCaches() {
        cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
    }

    @Test
    void playersEndpointReturnsSearchResults() throws Exception {
        mockMvc.perform(get("/api/players").param("query", "Stephen"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(201939))
                .andExpect(jsonPath("$.content[0].fullName").value("Stephen Curry"))
                .andExpect(jsonPath("$.content[0].position").value("G"))
                .andExpect(jsonPath("$.content[0].active").value(true));
    }

    @Test
    void retiredPlayersAreNotMarkedActive() throws Exception {
        mockMvc.perform(get("/api/players").param("query", "Michael Jordan"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(893))
                .andExpect(jsonPath("$.content[0].toYear").value(2002))
                .andExpect(jsonPath("$.content[0].active").value(false));
    }

    @Test
    void playersEndpointCanFilterToActivePlayers() throws Exception {
        mockMvc.perform(get("/api/players")
                        .param("query", "Michael Jordan")
                        .param("activeOnly", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)));
    }

    @Test
    void playerDetailEndpointReturnsOnePlayer() throws Exception {
        mockMvc.perform(get("/api/players/201939"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(201939))
                .andExpect(jsonPath("$.firstName").value("Stephen"))
                .andExpect(jsonPath("$.lastName").value("Curry"))
                .andExpect(jsonPath("$.draftYear").value(2009));
    }

    @Test
    void playerDetailEndpointPopulatesCache() throws Exception {
        mockMvc.perform(get("/api/players/201939"))
                .andExpect(status().isOk());

        assertThat(cacheManager.getCache("playerDetails").get(201939L)).isNotNull();
    }

    @Test
    void playerGamesEndpointReturnsSeededGameLog() throws Exception {
        mockMvc.perform(get("/api/players/201939/games").param("season", "2023"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].gameId").value(12300001))
                .andExpect(jsonPath("$.content[0].seasonLabel").value("2023-2024"))
                .andExpect(jsonPath("$.content[0].opponentTeamName").value("Los Angeles Lakers"))
                .andExpect(jsonPath("$.content[0].recordAfterGame").value("2-0"))
                .andExpect(jsonPath("$.content[0].teamScore").value(120))
                .andExpect(jsonPath("$.content[0].opponentScore").value(115))
                .andExpect(jsonPath("$.content[0].points").value(32))
                .andExpect(jsonPath("$.content[0].assists").value(7))
                .andExpect(jsonPath("$.content[0].rebounds").value(5))
                .andExpect(jsonPath("$.content[0].fieldGoalPercentage").value(0.5));
    }

    @Test
    @Sql(
            scripts = {"/test-cleanup.sql", "/test-data.sql"},
            statements = {
                    "insert into player_game_stats (game_id, player_id, team_id, opponent_team_id, win, home, num_minutes, points, assists, blocks, steals, field_goals_attempted, field_goals_made, field_goals_percentage, rebounds_total, turnovers, plus_minus_points, starting_position) values (12300001, 893, null, 1610612747, true, true, 34.00, 27, 5, 1, 2, 20, 10, 0.500, 7, 2, 6, null)"
            },
            executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void playerGamesDeriveTeamAndRecordWhenStatTeamIdIsMissing() throws Exception {
        mockMvc.perform(get("/api/players/893/games").param("season", "2023"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].teamId").value(1610612744))
                .andExpect(jsonPath("$.content[0].teamName").value("Golden State Warriors"))
                .andExpect(jsonPath("$.content[0].recordAfterGame").value("2-0"));
    }

    @Test
    @Sql(
            scripts = {"/test-cleanup.sql", "/test-data.sql"},
            statements = {
                    "insert into player_game_stats (game_id, player_id, team_id, opponent_team_id, win, home, num_minutes, points, assists, blocks, steals, field_goals_attempted, field_goals_made, field_goals_percentage, rebounds_total, turnovers, plus_minus_points, starting_position) values (12300001, 893, null, 1610612747, true, true, 34.00, 27, 5, 1, 2, 20, 10, 0.500, 7, 2, 6, null)"
            },
            executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void playerDashboardDerivesSeasonTeamsWhenStatTeamIdIsMissing() throws Exception {
        mockMvc.perform(get("/api/players/893/dashboard").param("season", "2023"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seasonTeams", hasSize(1)))
                .andExpect(jsonPath("$.seasonTeams[0].teamId").value(1610612744))
                .andExpect(jsonPath("$.seasonTeams[0].teamName").value("Golden State Warriors"));
    }

    @Test
    @Sql(
            scripts = {"/test-cleanup.sql", "/test-data.sql"},
            statements = {
                    "insert into games (game_id, season_start_year, game_date_time_est, game_date, home_team_id, away_team_id, home_team_city, home_team_name, away_team_city, away_team_name, home_score, away_score, winner_team_id, game_type, game_label, arena_name, arena_city, arena_state) values (12390002, 2023, null, '2024-01-20', 1610612744, 1610612747, 'Golden State', 'Warriors', 'Los Angeles', 'Lakers', 121, 110, 1610612744, 'Regular Season', 'Regular Season', 'Chase Center', 'San Francisco', 'CA')",
                    "insert into player_game_stats (game_id, player_id, team_id, opponent_team_id, win, home, num_minutes, points, assists, blocks, steals, field_goals_attempted, field_goals_made, field_goals_percentage, rebounds_total, turnovers, plus_minus_points, starting_position) values (12390002, 893, null, null, true, true, 34.00, 27, 5, 1, 2, 20, 10, 0.500, 7, 2, 6, null)"
            },
            executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void playerGamesDeriveRecordWhenStatTeamIdAndGameDateTimeAreMissing() throws Exception {
        mockMvc.perform(get("/api/players/893/games")
                        .param("season", "2023")
                        .param("query", "12390002"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].teamId").value(1610612744))
                .andExpect(jsonPath("$.content[0].teamName").value("Golden State Warriors"))
                .andExpect(jsonPath("$.content[0].opponentTeamName").value("Los Angeles Lakers"))
                .andExpect(jsonPath("$.content[0].recordAfterGame").value("3-0"));
    }

    @Test
    void playerGamesEndpointSupportsOpponentSearch() throws Exception {
        mockMvc.perform(get("/api/players/201939/games")
                        .param("season", "2023")
                        .param("query", "Lakers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].gameId").value(12300001));
    }

    @Test
    void playerSeasonsEndpointReturnsReadableLabels() throws Exception {
        mockMvc.perform(get("/api/players/201939/seasons"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].seasonStartYear").value(2023))
                .andExpect(jsonPath("$[0].label").value("2023-2024"))
                .andExpect(jsonPath("$[0].gameCount").value(2));
    }

    @Test
    void teamsEndpointReturnsSearchResults() throws Exception {
        mockMvc.perform(get("/api/teams").param("query", "GSW"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(1610612744))
                .andExpect(jsonPath("$.content[0].fullName").value("Golden State Warriors"))
                .andExpect(jsonPath("$.content[0].abbreviation").value("GSW"));
    }

    @Test
    void teamsEndpointDefaultsToCurrentNbaTeams() throws Exception {
        mockMvc.perform(get("/api/teams").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].fullName").value("Los Angeles Lakers"))
                .andExpect(jsonPath("$.content[0].championshipYears", hasSize(0)))
                .andExpect(jsonPath("$.content[1].fullName").value("Golden State Warriors"));
    }

    @Test
    void teamsEndpointCanIncludeHistoricalTeams() throws Exception {
        mockMvc.perform(get("/api/teams")
                        .param("query", "Sheboygan")
                        .param("currentOnly", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].fullName").value("Sheboygan Red Skins"));
    }

    @Test
    void gamesEndpointReturnsSeededGames() throws Exception {
        mockMvc.perform(get("/api/games").param("season", "2023").param("teamId", "1610612744"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].id").value(12300001))
                .andExpect(jsonPath("$.content[0].homeTeamName").value("Golden State Warriors"))
                .andExpect(jsonPath("$.content[0].awayTeamName").value("Los Angeles Lakers"))
                .andExpect(jsonPath("$.content[0].homeScore").value(120))
                .andExpect(jsonPath("$.content[0].awayScore").value(115));
    }

    @Test
    void seasonsEndpointReturnsImportedSeasons() throws Exception {
        mockMvc.perform(get("/api/seasons"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].seasonStartYear").value(2023))
                .andExpect(jsonPath("$[0].label").value("2023-2024"))
                .andExpect(jsonPath("$[0].gameCount").value(2));
    }

    @Test
    void teamGamesEndpointReturnsOpponentAndScoreContext() throws Exception {
        mockMvc.perform(get("/api/teams/1610612744/games")
                        .param("season", "2023")
                .param("query", "Lakers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].gameId").value(12300001))
                .andExpect(jsonPath("$.content[0].seasonLabel").value("2023-2024"))
                .andExpect(jsonPath("$.content[0].opponentTeamName").value("Los Angeles Lakers"))
                .andExpect(jsonPath("$.content[0].recordAfterGame").value("2-0"))
                .andExpect(jsonPath("$.content[0].teamScore").value(120))
                .andExpect(jsonPath("$.content[0].opponentScore").value(115));
    }

    @Test
    @Sql(
            scripts = {"/test-cleanup.sql", "/test-data.sql"},
            statements = {
                    "insert into games (game_id, season_start_year, game_date_time_est, game_date, home_team_id, away_team_id, home_team_city, home_team_name, away_team_city, away_team_name, home_score, away_score, winner_team_id, game_type, game_label, arena_name, arena_city, arena_state) values (12300003, 2023, '2024-04-20 22:00:00', '2024-04-20', 1610612744, 1610612747, 'Golden State', 'Warriors', 'Los Angeles', 'Lakers', 105, 100, 1610612744, 'Playoffs', 'Playoffs', 'Chase Center', 'San Francisco', 'CA')",
                    "insert into player_game_stats (game_id, player_id, team_id, opponent_team_id, win, home, num_minutes, points, assists, blocks, steals, field_goals_attempted, field_goals_made, field_goals_percentage, rebounds_total, turnovers, plus_minus_points, starting_position) values (12300003, 201939, 1610612744, 1610612747, true, true, 38.00, 35, 8, 1, 2, 24, 12, 0.500, 6, 3, 9, 'G')",
                    "insert into team_game_stats (game_id, team_id, opponent_team_id, home, win, team_score, opponent_score, assists, blocks, steals, field_goals_made, field_goals_attempted, field_goals_percentage, rebounds_total, turnovers, num_minutes) values (12300003, 1610612744, 1610612747, true, true, 105, 100, 25, 5, 8, 39, 86, 0.453, 42, 13, 240.00)"
            },
            executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void playoffGameLogsUsePlayoffRecord() throws Exception {
        mockMvc.perform(get("/api/players/201939/games")
                        .param("season", "2023")
                        .param("query", "Playoffs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].recordAfterGame").value("1-0"));

        mockMvc.perform(get("/api/teams/1610612744/games")
                        .param("season", "2023")
                        .param("query", "Playoffs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].recordAfterGame").value("1-0"));
    }

    @Test
    void teamDashboardReturnsRegularSeasonRecord() throws Exception {
        mockMvc.perform(get("/api/teams/1610612744/dashboard").param("season", "2023"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.regularSeasonRecord.wins").value(2))
                .andExpect(jsonPath("$.regularSeasonRecord.losses").value(0))
                .andExpect(jsonPath("$.regularSeasonRecord.winPercentage").value(1.0));
    }

    @Test
    void teamSeasonsEndpointReturnsReadableLabels() throws Exception {
        mockMvc.perform(get("/api/teams/1610612744/seasons"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].seasonStartYear").value(2023))
                .andExpect(jsonPath("$[0].label").value("2023-2024"));
    }

    @Test
    void gamesEndpointSupportsMatchupSearch() throws Exception {
        mockMvc.perform(get("/api/games")
                        .param("season", "2023")
                .param("query", "Lakers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].id").value(12300001));
    }

    @Test
    void gameBoxScoreEndpointReturnsTeamTotalsAndPlayerRows() throws Exception {
        mockMvc.perform(get("/api/games/12300001/box-score"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.game.id").value(12300001))
                .andExpect(jsonPath("$.homeTeam.teamId").value(1610612744))
                .andExpect(jsonPath("$.awayTeam.teamId").value(1610612747))
                .andExpect(jsonPath("$.homePlayers", hasSize(1)))
                .andExpect(jsonPath("$.awayPlayers", hasSize(1)))
                .andExpect(jsonPath("$.homePlayers[0].playerName").value("Stephen Curry"))
                .andExpect(jsonPath("$.awayPlayers[0].playerName").value("LeBron James"));
    }

    @Test
    @Sql(
            scripts = {"/test-cleanup.sql", "/test-data.sql"},
            statements = {
                    "insert into games (game_id, season_start_year, game_date_time_est, game_date, home_team_id, away_team_id, home_team_city, home_team_name, away_team_city, away_team_name, home_score, away_score, winner_team_id, game_type, game_label, arena_name, arena_city, arena_state) values (12390003, 2023, null, '2024-01-20', 1610612744, 1610612747, 'Golden State', 'Warriors', 'Los Angeles', 'Lakers', 121, 110, 1610612744, 'Regular Season', 'Regular Season', 'Chase Center', 'San Francisco', 'CA')",
                    "insert into player_game_stats (game_id, player_id, team_id, opponent_team_id, win, home, num_minutes, points, assists, blocks, steals, field_goals_attempted, field_goals_made, field_goals_percentage, rebounds_total, turnovers, plus_minus_points, starting_position) values (12390003, 893, null, 1610612747, true, true, 34.00, 27, 5, 1, 2, 20, 10, 0.500, 7, 2, 6, null)"
            },
            executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void gameBoxScoreKeepsPlayerRowsWhenStatTeamIdIsMissing() throws Exception {
        mockMvc.perform(get("/api/games/12390003/box-score"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.homePlayers", hasSize(1)))
                .andExpect(jsonPath("$.homePlayers[0].playerName").value("Michael Jordan"))
                .andExpect(jsonPath("$.homePlayers[0].teamId").value(1610612744))
                .andExpect(jsonPath("$.homePlayers[0].teamName").value("Golden State Warriors"))
                .andExpect(jsonPath("$.homePlayers[0].startingPosition").value(nullValue()));
    }

    @Test
    @Sql(
            scripts = {"/test-cleanup.sql", "/test-data.sql"},
            statements = {
                    "insert into transactions (player_id, from_team_id, to_team_id, transaction_type, transaction_date, source, notes) values (2544, 1610612747, 1610612744, 'trade', '2024-07-01', 'test', 'Phase 11 roster impact')",
                    "insert into draft_picks (player_id, team_id, draft_year, draft_round, draft_number, rookie_season_start_year) values (null, 1610612747, 2024, 1, 10, 2024)",
                    "insert into injury_reports (report_date, game_date, team_id, player_id, injury_status, reason, source, confidence) values ('2024-07-02', null, 1610612744, 201939, 'questionable', 'ankle', 'test', 0.8000)"
            },
            executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void standingsProjectionWorksBeforeScheduleReleaseAndPersistsRows() throws Exception {
        mockMvc.perform(get("/api/standings/projections/2024"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seasonStartYear").value(2024))
                .andExpect(jsonPath("$.scheduleAvailable").value(false))
                .andExpect(jsonPath("$.projectionMethod").value("Roster-aware projection before schedule release"))
                .andExpect(jsonPath("$.easternConference", hasSize(0)))
                .andExpect(jsonPath("$.westernConference", hasSize(2)))
                .andExpect(jsonPath("$.westernConference[0].teamId").value(1610612744))
                .andExpect(jsonPath("$.westernConference[0].topReasons[0]").value("Previous season record: 2-0"));

        assertThat(countRows("standings_projections")).isEqualTo(2);
        assertThat(countRows("team_strength_ratings")).isEqualTo(2);
        assertThat(countRows("team_context_snapshots")).isEqualTo(2);
    }

    @Test
    @Sql(
            scripts = {"/test-cleanup.sql", "/test-data.sql"},
            statements = {
                    "insert into games (game_id, season_start_year, game_date_time_est, game_date, home_team_id, away_team_id, home_team_city, home_team_name, away_team_city, away_team_name, home_score, away_score, winner_team_id, game_type, game_label, game_sub_label, arena_name, arena_city, arena_state) values (12300004, 2023, '2023-12-09 22:00:00', '2023-12-09', 1610612744, 1610612747, 'Golden State', 'Warriors', 'Los Angeles', 'Lakers', 100, 105, 1610612747, 'NBA Emirates Cup', 'NBA Cup', 'Championship', 'T-Mobile Arena', 'Las Vegas', 'NV')"
            },
            executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void standingsProjectionExcludesCupChampionshipFromPreviousRecord() throws Exception {
        mockMvc.perform(get("/api/standings/projections/2024"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.westernConference[0].teamId").value(1610612744))
                .andExpect(jsonPath("$.westernConference[0].topReasons[0]").value("Previous season record: 2-0"));
    }

    @Test
    @Sql(
            scripts = {"/test-cleanup.sql", "/test-data.sql"},
            statements = {
                    "insert into games (game_id, season_start_year, game_date_time_est, game_date, home_team_id, away_team_id, home_team_city, home_team_name, away_team_city, away_team_name, home_score, away_score, winner_team_id, game_type, game_label, arena_name, arena_city, arena_state) values (22400001, 2024, '2024-10-22 22:00:00', '2024-10-22', 1610612744, 1610612747, 'Golden State', 'Warriors', 'Los Angeles', 'Lakers', 109, 110, 1610612747, 'Regular Season', 'Regular Season', 'Chase Center', 'San Francisco', 'CA')"
            },
            executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void standingsProjectionUsesOfficialCompletedRecordWhenImportedSeasonIsShort() throws Exception {
        mockMvc.perform(get("/api/standings/projections/2025"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.westernConference[0].teamId").value(1610612747))
                .andExpect(jsonPath("$.westernConference[0].topReasons[0]").value("Previous season record: 50-32"))
                .andExpect(jsonPath("$.westernConference[1].topReasons[0]").value("Previous season record: 48-34"));
    }

    @Test
    @Sql(
            scripts = {"/test-cleanup.sql", "/test-data.sql"},
            statements = {
                    "insert into games (game_id, season_start_year, game_date_time_est, game_date, home_team_id, away_team_id, home_team_city, home_team_name, away_team_city, away_team_name, game_type, game_label, arena_name, arena_city, arena_state) values (22400001, 2024, '2024-10-22 22:00:00', '2024-10-22', 1610612744, 1610612747, 'Golden State', 'Warriors', 'Los Angeles', 'Lakers', 'Regular Season', 'Regular Season', 'Chase Center', 'San Francisco', 'CA')"
            },
            executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void standingsProjectionUsesScheduleWhenTargetSeasonGamesExist() throws Exception {
        mockMvc.perform(get("/api/standings/projections/2024"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scheduleAvailable").value(true))
                .andExpect(jsonPath("$.projectionMethod").value("Roster-aware projection with schedule context"))
                .andExpect(jsonPath("$.westernConference", hasSize(2)))
                .andExpect(jsonPath("$.westernConference[0].topReasons[0]").value("Schedule context included for 1 listed game"));
    }

    @Test
    @Sql(
            scripts = {"/test-cleanup.sql", "/test-data.sql"},
            statements = {
                    "insert into games (game_id, season_start_year, game_date_time_est, game_date, home_team_id, away_team_id, home_team_city, home_team_name, away_team_city, away_team_name, home_score, away_score, winner_team_id, game_type, game_label, arena_name, arena_city, arena_state) values (14900001, 1949, '1949-12-01 20:00:00', '1949-12-01', 1610610036, 1610612744, 'Sheboygan', 'Red Skins', 'Golden State', 'Warriors', 90, 88, 1610610036, 'Regular Season', 'Regular Season', 'Test Arena', 'Sheboygan', 'WI')",
                    "insert into games (game_id, season_start_year, game_date_time_est, game_date, home_team_id, away_team_id, home_team_city, home_team_name, away_team_city, away_team_name, home_score, away_score, winner_team_id, game_type, game_label, arena_name, arena_city, arena_state) values (15000001, 1950, '1950-12-01 20:00:00', '1950-12-01', 1610610036, 1610612744, 'Sheboygan', 'Red Skins', 'Golden State', 'Warriors', 80, 85, 1610612744, 'Regular Season', 'Regular Season', 'Test Arena', 'Sheboygan', 'WI')"
            },
            executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void standingsProjectionUsesTeamsFromHistoricalSeason() throws Exception {
        mockMvc.perform(get("/api/standings/projections/1950"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.westernConference[*].teamId", hasItem(1610610036)))
                .andExpect(jsonPath("$.westernConference[*].teamName", hasItem("Sheboygan Red Skins")))
                .andExpect(jsonPath("$.westernConference[?(@.teamId == 1610610036)].topReasons[*]", hasItem("Previous season record: 1-0")));
    }

    @Test
    @Sql(
            scripts = {"/test-cleanup.sql", "/test-data.sql"},
            statements = {
                    "insert into games (game_id, season_start_year, game_date_time_est, game_date, home_team_id, away_team_id, home_team_city, home_team_name, away_team_city, away_team_name, home_score, away_score, winner_team_id, game_type, game_label, arena_name, arena_city, arena_state) values (14900001, 1949, '1949-12-01 20:00:00', '1949-12-01', 1610610036, 1610612744, 'Sheboygan', 'Red Skins', 'Golden State', 'Warriors', 90, 88, null, 'Regular Season', 'Regular Season', 'Test Arena', 'Sheboygan', 'WI')",
                    "insert into games (game_id, season_start_year, game_date_time_est, game_date, home_team_id, away_team_id, home_team_city, home_team_name, away_team_city, away_team_name, home_score, away_score, winner_team_id, game_type, game_label, arena_name, arena_city, arena_state) values (15000001, 1950, '1950-12-01 20:00:00', '1950-12-01', 1610610036, 1610612744, 'Sheboygan', 'Red Skins', 'Golden State', 'Warriors', 80, 85, null, 'Regular Season', 'Regular Season', 'Test Arena', 'Sheboygan', 'WI')"
            },
            executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void standingsProjectionDerivesPreviousRecordFromScoreWhenWinnerIdIsMissing() throws Exception {
        mockMvc.perform(get("/api/standings/projections/1950"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.westernConference[?(@.teamId == 1610610036)].topReasons[*]", hasItem("Previous season record: 1-0")));
    }

    @Test
    @Sql(
            scripts = {"/test-cleanup.sql", "/test-data.sql"},
            statements = {
                    "insert into games (game_id, season_start_year, game_date_time_est, game_date, home_team_id, away_team_id, home_team_city, home_team_name, away_team_city, away_team_name, home_score, away_score, winner_team_id, game_type, game_label, arena_name, arena_city, arena_state) values (14600001, 1946, '1946-11-01 20:00:00', '1946-11-01', 1610612744, 1610612747, 'Golden State', 'Warriors', 'Los Angeles', 'Lakers', 70, 65, null, 'Regular Season', 'Regular Season', 'Test Arena', 'Philadelphia', 'PA')"
            },
            executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void standingsProjectionShowsNoPreviousSeasonRecordForFirstNbaSeason() throws Exception {
        mockMvc.perform(get("/api/standings/projections/1946"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.westernConference[*].topReasons[*]", hasItem("Previous season record: No previous season record")));
    }

    @Test
    @Sql(
            scripts = {"/test-cleanup.sql", "/test-data.sql"},
            statements = {
                    "insert into transactions (player_id, from_team_id, to_team_id, transaction_type, transaction_date, source, notes) values (2544, 1610612747, 1610612744, 'trade', '2024-07-01', 'test', 'Phase 11 roster impact')",
                    "insert into injury_reports (report_date, game_date, team_id, player_id, injury_status, reason, source, confidence) values ('2024-07-02', null, 1610612744, 201939, 'questionable', 'ankle', 'test', 0.8000)"
            },
            executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void teamProjectionAndRosterImpactExplainConfirmedMovement() throws Exception {
        mockMvc.perform(get("/api/teams/1610612744/projection").param("season", "2024"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teamId").value(1610612744))
                .andExpect(jsonPath("$.sourceSeasonStartYear").value(2023))
                .andExpect(jsonPath("$.projectedWins").isNumber())
                .andExpect(jsonPath("$.rosterImpactScore").isNumber());

        mockMvc.perform(get("/api/teams/1610612744/roster-impact").param("season", "2024"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.playersAdded").value(1))
                .andExpect(jsonPath("$.playersLost").value(0))
                .andExpect(jsonPath("$.injuryFlagCount").value(1))
                .andExpect(jsonPath("$.explanations[0]").value("Added LeBron James"));
    }

    @Test
    @Sql(
            scripts = {"/test-cleanup.sql", "/test-data.sql"},
            statements = {
                    "insert into transactions (player_id, from_team_id, to_team_id, transaction_type, transaction_date, source, source_status, confidence, affects_projection, notes) values (201939, 1610612744, 1610612747, 'trade', '2024-07-01', 'ESPN', 'trusted_report', 0.9000, true, 'trusted report should affect projection')"
            },
            executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void trustedReportedStarMoveRaisesRosterImpact() throws Exception {
        mockMvc.perform(get("/api/teams/1610612747/roster-impact").param("season", "2024"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.playersAdded").value(1))
                .andExpect(jsonPath("$.rosterImpactScore").value(greaterThan(5.0)))
                .andExpect(jsonPath("$.explanations[0]").value("Added Stephen Curry"));

        mockMvc.perform(get("/api/teams/1610612747/projection").param("season", "2024"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rosterImpactScore").value(greaterThan(5.0)))
                .andExpect(jsonPath("$.topReasons[3]").value("Added Stephen Curry"));
    }

    @Test
    @Sql(
            scripts = {"/test-cleanup.sql", "/test-data.sql"},
            statements = {
                    "insert into transactions (player_id, from_team_id, to_team_id, transaction_type, transaction_date, source, source_status, confidence, affects_projection, notes) values (201939, 1610612744, 1610612747, 'trade', '2024-07-01', 'rumor-feed', 'rumor', 0.2500, false, 'rumor should not affect projection')"
            },
            executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void rumorTransactionDoesNotAffectProjection() throws Exception {
        mockMvc.perform(get("/api/teams/1610612747/roster-impact").param("season", "2024"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.playersAdded").value(0))
                .andExpect(jsonPath("$.rosterImpactScore").value(0.0))
                .andExpect(jsonPath("$.explanations[0]").value("No major confirmed roster movement found for this projection window"));
    }

    @Test
    @Sql(
            scripts = {"/test-cleanup.sql", "/test-data.sql"},
            statements = {
                    "insert into transactions (player_id, from_team_id, to_team_id, transaction_type, transaction_date, source, source_status, confidence, affects_projection, notes) values (201939, 1610612744, 1610612747, 'trade', '2024-07-01', 'trusted-feed', 'trusted_report', 0.5000, true, 'low-confidence report should not affect projection')"
            },
            executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void lowConfidenceTransactionDoesNotAffectProjection() throws Exception {
        mockMvc.perform(get("/api/teams/1610612747/roster-impact").param("season", "2024"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.playersAdded").value(0))
                .andExpect(jsonPath("$.rosterImpactScore").value(0.0))
                .andExpect(jsonPath("$.explanations[0]").value("No major confirmed roster movement found for this projection window"));
    }

    @Test
    @Sql(
            scripts = {"/test-cleanup.sql", "/test-data.sql"},
            statements = {
                    "insert into draft_picks (player_id, team_id, draft_year, draft_round, draft_number, rookie_season_start_year) values (null, 1610612747, 2024, 1, 1, 2024)"
            },
            executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void topDraftPickAddsRosterImpactBeforePlayerIsKnown() throws Exception {
        mockMvc.perform(get("/api/teams/1610612747/roster-impact").param("season", "2024"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rookieCount").value(1))
                .andExpect(jsonPath("$.rosterImpactScore").value(2.8))
                .andExpect(jsonPath("$.explanations[0]").value("Added No. 1 pick"));
    }

    @Test
    void standingsSimulationPersistsRunAndProjectedRecords() throws Exception {
        mockMvc.perform(post("/api/standings/simulate")
                        .param("season", "2024")
                        .param("runs", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.simulationRunId").isNumber())
                .andExpect(jsonPath("$.seasonStartYear").value(2024))
                .andExpect(jsonPath("$.runCount").value(100))
                .andExpect(jsonPath("$.scheduleAvailable").value(false))
                .andExpect(jsonPath("$.projectedRecords", hasSize(2)));

        assertThat(countRows("season_simulation_runs")).isEqualTo(1);
        assertThat(countRows("projected_team_records")).isEqualTo(2);
    }

    private Integer countRows(String tableName) {
        return jdbcTemplate.queryForObject("select count(*) from " + tableName, Integer.class);
    }
}
