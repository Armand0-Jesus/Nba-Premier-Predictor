package com.armandorodriguez.nba_premier_predictor.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest
@Sql(scripts = {"/test-cleanup.sql", "/test-data.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class StandingsProjectionIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void standingsProjectionWorksBeforeScheduleReleaseAndPersistsRows() throws Exception {
        mockMvc.perform(get("/api/standings/projections/{season}", 2024))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seasonStartYear").value(2024))
                .andExpect(jsonPath("$.scheduleAvailable").value(false))
                .andExpect(jsonPath("$.projectionMethod").value("Roster-aware projection before schedule release"))
                .andExpect(jsonPath("$.westernConference", hasSize(2)));

        assertThat(countRows("standings_projections")).isEqualTo(2);
        assertThat(countRows("team_strength_ratings")).isEqualTo(2);
        assertThat(countRows("team_context_snapshots")).isEqualTo(2);
    }

    @Test
    @Sql(
            scripts = {"/test-cleanup.sql", "/test-data.sql"},
            statements = {
                    "insert into games (game_id, season_start_year, game_date_time_est, game_date, home_team_id, away_team_id, home_team_city, home_team_name, away_team_city, away_team_name, game_type, game_label, arena_name, arena_city, arena_state) values (22400001, 2024, '2024-10-22 22:00:00', '2024-10-22', 1610612744, 1610612747, 'Golden State', 'Warriors', 'Los Angeles', 'Lakers', 'Regular Season', 'Regular Season', 'Chase Center', 'San Francisco', 'CA')"
            },
            executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void standingsProjectionUsesScheduleWhenTargetSeasonGamesExist() throws Exception {
        mockMvc.perform(get("/api/standings/projections/{season}", 2024))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scheduleAvailable").value(true))
                .andExpect(jsonPath("$.projectionMethod").value("Roster-aware projection with schedule context"))
                .andExpect(jsonPath("$.westernConference[0].topReasons[0]").value("Schedule context included for 1 listed game"));
    }

    @Test
    @Sql(
            scripts = {"/test-cleanup.sql", "/test-data.sql"},
            statements = {
                    "insert into transactions (player_id, from_team_id, to_team_id, transaction_type, transaction_date, source, source_status, confidence, affects_projection, notes) values (2544, 1610612747, 1610612744, 'trade', '2024-07-01', 'ESPN', 'trusted_report', 0.9000, true, 'trusted report should affect projection')"
            },
            executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void teamProjectionAndRosterImpactUseTrustedTransactions() throws Exception {
        mockMvc.perform(get("/api/teams/{teamId}/roster-impact", 1610612744).param("season", "2024"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.playersAdded").value(1))
                .andExpect(jsonPath("$.rosterImpactScore").value(greaterThan(5.0)))
                .andExpect(jsonPath("$.explanations[0]").value("Added LeBron James"));

        mockMvc.perform(get("/api/teams/{teamId}/projection", 1610612744).param("season", "2024"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teamId").value(1610612744))
                .andExpect(jsonPath("$.rosterImpactScore").value(greaterThan(5.0)));
    }

    @Test
    @Sql(
            scripts = {"/test-cleanup.sql", "/test-data.sql"},
            statements = {
                    "insert into transactions (player_id, from_team_id, to_team_id, transaction_type, transaction_date, source, source_status, confidence, affects_projection, notes) values (201939, 1610612744, 1610612747, 'trade', '2024-07-01', 'rumor-feed', 'rumor', 0.2500, false, 'rumor should not affect projection')",
                    "insert into transactions (player_id, from_team_id, to_team_id, transaction_type, transaction_date, source, source_status, confidence, affects_projection, notes) values (2544, 1610612747, 1610612744, 'trade', '2024-07-01', 'trusted-feed', 'trusted_report', 0.5000, true, 'low-confidence report should not affect projection')"
            },
            executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void unconfirmedAndLowConfidenceTransactionsAreIgnored() throws Exception {
        mockMvc.perform(get("/api/teams/{teamId}/roster-impact", 1610612747).param("season", "2024"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.playersAdded").value(0))
                .andExpect(jsonPath("$.rosterImpactScore").value(0.0))
                .andExpect(jsonPath("$.explanations[0]").value("No major confirmed roster movement found for this projection window"));
    }

    @Test
    void standingsSimulationPersistsRunAndProjectedRecords() throws Exception {
        mockMvc.perform(post("/api/standings/simulate")
                        .param("season", "2024")
                        .param("runs", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.simulationRunId").isNumber())
                .andExpect(jsonPath("$.projectedRecords", hasSize(2)));

        assertThat(countRows("season_simulation_runs")).isEqualTo(1);
        assertThat(countRows("projected_team_records")).isEqualTo(2);
    }

    private Integer countRows(String tableName) {
        return jdbcTemplate.queryForObject("select count(*) from " + tableName, Integer.class);
    }
}
