package com.armandorodriguez.nba_premier_predictor.controller;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.armandorodriguez.nba_premier_predictor.dto.ContextDtos.ContextIngestionResponse;
import com.armandorodriguez.nba_premier_predictor.dto.ContextDtos.ContextRefreshResponse;
import com.armandorodriguez.nba_premier_predictor.dto.ContextDtos.DraftPickRequest;
import com.armandorodriguez.nba_premier_predictor.dto.ContextDtos.DraftPickResponse;
import com.armandorodriguez.nba_premier_predictor.dto.ContextDtos.InjuryReportRequest;
import com.armandorodriguez.nba_premier_predictor.dto.ContextDtos.InjuryReportResponse;
import com.armandorodriguez.nba_premier_predictor.dto.ContextDtos.RosterSnapshotRequest;
import com.armandorodriguez.nba_premier_predictor.dto.ContextDtos.RosterSnapshotResponse;
import com.armandorodriguez.nba_premier_predictor.dto.ContextDtos.TransactionRequest;
import com.armandorodriguez.nba_premier_predictor.dto.ContextDtos.TransactionResponse;
import com.armandorodriguez.nba_premier_predictor.service.ContextDataService;
import com.armandorodriguez.nba_premier_predictor.service.ContextRefreshService;

@Validated
@RestController
@RequestMapping("/api/context")
public class ContextController {

    private final ContextDataService contextDataService;
    private final ContextRefreshService contextRefreshService;

    public ContextController(ContextDataService contextDataService, ContextRefreshService contextRefreshService) {
        this.contextDataService = contextDataService;
        this.contextRefreshService = contextRefreshService;
    }

    @PostMapping("/refresh")
    ContextRefreshResponse refreshContext() {
        return contextRefreshService.refresh("manual_context_refresh");
    }

    @PostMapping("/rosters")
    ContextIngestionResponse ingestRosters(
            @RequestBody @NotEmpty List<@Valid RosterSnapshotRequest> records) {
        return contextDataService.ingestRosters(records);
    }

    @GetMapping("/rosters")
    List<RosterSnapshotResponse> rosters(
            @RequestParam(required = false) @Positive Long teamId,
            @RequestParam(required = false) LocalDate snapshotDate,
            @RequestParam(defaultValue = "100") @Min(1) @Max(500) int limit) {
        return contextDataService.rosters(teamId, snapshotDate, limit);
    }

    @PostMapping("/transactions")
    ContextIngestionResponse ingestTransactions(
            @RequestBody @NotEmpty List<@Valid TransactionRequest> records) {
        return contextDataService.ingestTransactions(records);
    }

    @GetMapping("/transactions")
    List<TransactionResponse> transactions(
            @RequestParam(required = false) @Positive Long playerId,
            @RequestParam(required = false) @Positive Long teamId,
            @RequestParam(required = false) LocalDate fromDate,
            @RequestParam(required = false) LocalDate toDate,
            @RequestParam(defaultValue = "100") @Min(1) @Max(500) int limit) {
        return contextDataService.transactions(playerId, teamId, fromDate, toDate, limit);
    }

    @PostMapping("/draft-picks")
    ContextIngestionResponse ingestDraftPicks(
            @RequestBody @NotEmpty List<@Valid DraftPickRequest> records) {
        return contextDataService.ingestDraftPicks(records);
    }

    @GetMapping("/draft-picks")
    List<DraftPickResponse> draftPicks(
            @RequestParam(required = false) @Min(1946) Integer draftYear,
            @RequestParam(required = false) @Positive Long teamId,
            @RequestParam(required = false) @Positive Long playerId,
            @RequestParam(defaultValue = "100") @Min(1) @Max(500) int limit) {
        return contextDataService.draftPicks(draftYear, teamId, playerId, limit);
    }

    @PostMapping("/injuries")
    ContextIngestionResponse ingestInjuries(
            @RequestBody @NotEmpty List<@Valid InjuryReportRequest> records) {
        return contextDataService.ingestInjuries(records);
    }

    @GetMapping("/injuries")
    List<InjuryReportResponse> injuries(
            @RequestParam(required = false) @Positive Long teamId,
            @RequestParam(required = false) @Positive Long playerId,
            @RequestParam(required = false) LocalDate gameDate,
            @RequestParam(defaultValue = "100") @Min(1) @Max(500) int limit) {
        return contextDataService.injuries(teamId, playerId, gameDate, limit);
    }
}
