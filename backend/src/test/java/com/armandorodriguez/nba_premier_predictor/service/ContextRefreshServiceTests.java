package com.armandorodriguez.nba_premier_predictor.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import com.armandorodriguez.nba_premier_predictor.config.ContextRefreshProperties;
import com.armandorodriguez.nba_premier_predictor.dto.ContextDtos.ContextIngestionResponse;
import com.armandorodriguez.nba_premier_predictor.dto.ContextDtos.ContextRefreshResponse;
import com.armandorodriguez.nba_premier_predictor.dto.ContextDtos.TransactionRequest;
import com.fasterxml.jackson.databind.ObjectMapper;

class ContextRefreshServiceTests {

    @Test
    void refreshIngestsTrustedFeedAndRefreshesProjectionsAndRetraining() {
        ContextRefreshProperties properties = new ContextRefreshProperties();
        properties.setSourceUrls(List.of("https://trusted.test/nba-context.json"));
        properties.setRetrainAfterIngestion(true);

        ContextRefreshSourceClient sourceClient = mock(ContextRefreshSourceClient.class);
        ContextDataService contextDataService = mock(ContextDataService.class);
        StandingsProjectionService standingsProjectionService = mock(StandingsProjectionService.class);
        ModelMetadataService modelMetadataService = mock(ModelMetadataService.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(contextDataService.ingestTransactions(any())).thenReturn(new ContextIngestionResponse(1));
        when(contextDataService.ingestRosters(any())).thenReturn(new ContextIngestionResponse(1));
        when(contextDataService.ingestDraftPicks(any())).thenReturn(new ContextIngestionResponse(1));
        when(contextDataService.ingestInjuries(any())).thenReturn(new ContextIngestionResponse(1));
        when(sourceClient.fetch("https://trusted.test/nba-context.json")).thenReturn("""
                {
                  "source": "Trusted NBA desk",
                  "sourceStatus": "trusted_report",
                  "confidence": 0.92,
                  "transactions": [
                    {
                      "playerId": 2544,
                      "fromTeamId": 1610612747,
                      "toTeamId": 1610612748,
                      "transactionType": "trade",
                      "transactionDate": "2026-06-28",
                      "notes": "Test move"
                    }
                  ],
                  "rosters": [
                    {
                      "snapshotDate": "2026-06-29",
                      "teamId": 1610612748,
                      "playerId": 2544,
                      "rosterStatus": "ACTIVE"
                    }
                  ],
                  "draftPicks": [
                    {
                      "teamId": 1610612764,
                      "draftYear": 2026,
                      "draftRound": 1,
                      "draftNumber": 1,
                      "rookieSeasonStartYear": 2026
                    }
                  ],
                  "injuries": [
                    {
                      "reportDate": "2026-06-29",
                      "teamId": 1610612748,
                      "playerId": 2544,
                      "injuryStatus": "available",
                      "confidence": 1.0
                    }
                  ]
                }
                """);

        ContextRefreshService service = new ContextRefreshService(
                properties,
                sourceClient,
                new ObjectMapper().findAndRegisterModules(),
                contextDataService,
                standingsProjectionService,
                modelMetadataService,
                jdbcTemplate);

        ContextRefreshResponse response = service.refresh("manual_context_refresh");

        assertThat(response.recordsAccepted()).isEqualTo(4);
        assertThat(response.transactionsAccepted()).isEqualTo(1);
        assertThat(response.rosterSnapshotsAccepted()).isEqualTo(1);
        assertThat(response.draftPicksAccepted()).isEqualTo(1);
        assertThat(response.injuryReportsAccepted()).isEqualTo(1);
        assertThat(response.projectionsRefreshed()).isTrue();
        assertThat(response.retrainingTriggered()).isTrue();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<TransactionRequest>> transactionCaptor = ArgumentCaptor.forClass(List.class);
        verify(contextDataService).ingestTransactions(transactionCaptor.capture());
        TransactionRequest transaction = transactionCaptor.getValue().getFirst();
        assertThat(transaction.source()).isEqualTo("Trusted NBA desk");
        assertThat(transaction.sourceUrl()).isEqualTo("https://trusted.test/nba-context.json");
        assertThat(transaction.sourceStatus()).isEqualTo("trusted_report");
        assertThat(transaction.confidence()).isEqualByComparingTo("0.92");

        verify(standingsProjectionService).projections(null);
        verify(modelMetadataService).retrain(argThat(request ->
                request != null && "context_refresh".equals(request.normalizedTriggeredBy())));
    }

    @Test
    void refreshDoesNothingWhenNoSourcesAreConfigured() {
        ContextRefreshProperties properties = new ContextRefreshProperties();
        ContextRefreshSourceClient sourceClient = mock(ContextRefreshSourceClient.class);
        ContextDataService contextDataService = mock(ContextDataService.class);
        StandingsProjectionService standingsProjectionService = mock(StandingsProjectionService.class);
        ModelMetadataService modelMetadataService = mock(ModelMetadataService.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        ContextRefreshService service = new ContextRefreshService(
                properties,
                sourceClient,
                new ObjectMapper().findAndRegisterModules(),
                contextDataService,
                standingsProjectionService,
                modelMetadataService,
                jdbcTemplate);

        ContextRefreshResponse response = service.refresh("manual_context_refresh");

        assertThat(response.recordsAccepted()).isZero();
        assertThat(response.message()).isEqualTo("No trusted context sources configured");
        verifyNoInteractions(sourceClient, contextDataService, standingsProjectionService, modelMetadataService);
    }
}
