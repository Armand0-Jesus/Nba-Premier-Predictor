package com.armandorodriguez.nba_premier_predictor.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest
@Sql(scripts = {"/test-cleanup.sql", "/test-data.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class ContextIngestionIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void ingestsAndReadsPhaseFiveContextData() throws Exception {
        mockMvc.perform(post("/api/context/rosters")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                [
                                  {
                                    "snapshotDate": "2024-01-15",
                                    "teamId": 1610612744,
                                    "playerId": 201939,
                                    "position": "PG",
                                    "rosterStatus": "ACTIVE",
                                    "projectedMinutes": 34.5,
                                    "source": "test-seed"
                                  }
                                ]
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recordsAccepted").value(1));

        mockMvc.perform(get("/api/context/rosters")
                        .param("teamId", "1610612744")
                        .param("snapshotDate", "2024-01-15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].playerId").value(201939))
                .andExpect(jsonPath("$[0].rosterStatus").value("ACTIVE"))
                .andExpect(jsonPath("$[0].projectedMinutes").value(34.5));

        mockMvc.perform(post("/api/context/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                [
                                  {
                                    "playerId": 2544,
                                    "fromTeamId": 1610612747,
                                    "toTeamId": 1610612744,
                                    "transactionType": "trade",
                                    "transactionDate": "2024-01-10",
                                    "source": "test-seed",
                                    "sourceUrl": "https://example.test/transaction",
                                    "sourceStatus": "trusted_report",
                                    "confidence": 0.90,
                                    "affectsProjection": true,
                                    "reportedAt": "2024-01-10T15:00:00Z",
                                    "notes": "Context ingestion test move"
                                  }
                                ]
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recordsAccepted").value(1));

        mockMvc.perform(get("/api/context/transactions")
                        .param("teamId", "1610612744"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].playerId").value(2544))
                .andExpect(jsonPath("$[0].toTeamId").value(1610612744))
                .andExpect(jsonPath("$[0].transactionType").value("trade"))
                .andExpect(jsonPath("$[0].sourceStatus").value("trusted_report"))
                .andExpect(jsonPath("$[0].affectsProjection").value(true))
                .andExpect(jsonPath("$[0].confidence").value(0.90));

        mockMvc.perform(post("/api/context/draft-picks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                [
                                  {
                                    "teamId": 1610612744,
                                    "draftYear": 2024,
                                    "draftRound": 1,
                                    "draftNumber": 1,
                                    "rookieSeasonStartYear": 2024
                                  }
                                ]
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recordsAccepted").value(1));

        mockMvc.perform(get("/api/context/draft-picks")
                        .param("draftYear", "2024"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].teamId").value(1610612744))
                .andExpect(jsonPath("$[0].draftNumber").value(1));

        mockMvc.perform(post("/api/context/injuries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                [
                                  {
                                    "reportDate": "2024-01-14",
                                    "gameDate": "2024-01-15",
                                    "teamId": 1610612744,
                                    "playerId": 201939,
                                    "injuryStatus": "questionable",
                                    "reason": "ankle",
                                    "source": "test-seed",
                                    "confidence": 0.85,
                                    "validUntil": "2024-01-15T21:00:00"
                                  }
                                ]
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recordsAccepted").value(1));

        mockMvc.perform(get("/api/context/injuries")
                        .param("teamId", "1610612744")
                        .param("gameDate", "2024-01-15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].playerId").value(201939))
                .andExpect(jsonPath("$[0].injuryStatus").value("questionable"))
                .andExpect(jsonPath("$[0].confidence").value(0.85));
    }

    @Test
    void rejectsInvalidInjuryContext() throws Exception {
        mockMvc.perform(post("/api/context/injuries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                [
                                  {
                                    "reportDate": "2024-01-14",
                                    "injuryStatus": "",
                                    "confidence": 1.50
                                  }
                                ]
                                """))
                .andExpect(status().isBadRequest());
    }
}
