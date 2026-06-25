package com.armandorodriguez.nba_premier_predictor.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
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
                .andExpect(jsonPath("$.content[0].position").value("G"));
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
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].gameId").value(12300001))
                .andExpect(jsonPath("$.content[0].points").value(32))
                .andExpect(jsonPath("$.content[0].assists").value(7))
                .andExpect(jsonPath("$.content[0].rebounds").value(5));
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
    void gamesEndpointReturnsSeededGames() throws Exception {
        mockMvc.perform(get("/api/games").param("season", "2023").param("teamId", "1610612744"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(12300001))
                .andExpect(jsonPath("$.content[0].homeTeamName").value("Golden State Warriors"))
                .andExpect(jsonPath("$.content[0].awayTeamName").value("Los Angeles Lakers"))
                .andExpect(jsonPath("$.content[0].homeScore").value(120))
                .andExpect(jsonPath("$.content[0].awayScore").value(115));
    }
}
