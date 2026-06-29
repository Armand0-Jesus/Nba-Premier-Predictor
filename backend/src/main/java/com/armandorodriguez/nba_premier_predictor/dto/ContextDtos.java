package com.armandorodriguez.nba_premier_predictor.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

public final class ContextDtos {

    private ContextDtos() {
    }

    public record ContextIngestionResponse(int recordsAccepted) {
    }

    public record ContextRefreshResponse(
            int sourcesChecked,
            int sourcesSucceeded,
            int sourcesFailed,
            int recordsAccepted,
            int transactionsAccepted,
            int rosterSnapshotsAccepted,
            int draftPicksAccepted,
            int injuryReportsAccepted,
            boolean projectionsRefreshed,
            boolean retrainingTriggered,
            String message) {

        public static ContextRefreshResponse empty(String message) {
            return new ContextRefreshResponse(0, 0, 0, 0, 0, 0, 0, 0, false, false, message);
        }
    }

    public record RosterSnapshotRequest(
            @NotNull LocalDate snapshotDate,
            @NotNull @Positive Long teamId,
            @NotNull @Positive Long playerId,
            String position,
            String rosterStatus,
            @DecimalMin("0.0") BigDecimal projectedMinutes,
            String source) {
    }

    public record RosterSnapshotResponse(
            Long id,
            LocalDate snapshotDate,
            Long teamId,
            Long playerId,
            String position,
            String rosterStatus,
            BigDecimal projectedMinutes,
            String source,
            Instant ingestedAt) {
    }

    public record TransactionRequest(
            @NotNull @Positive Long playerId,
            @Positive Long fromTeamId,
            @Positive Long toTeamId,
            @NotBlank String transactionType,
            LocalDate transactionDate,
            String source,
            String sourceUrl,
            @Pattern(regexp = "official|trusted_report|rumor|unconfirmed", flags = Pattern.Flag.CASE_INSENSITIVE)
            String sourceStatus,
            @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal confidence,
            Boolean affectsProjection,
            Instant reportedAt,
            String notes) {
    }

    public record TransactionResponse(
            Long id,
            Long playerId,
            Long fromTeamId,
            Long toTeamId,
            String transactionType,
            LocalDate transactionDate,
            String source,
            String sourceUrl,
            String sourceStatus,
            BigDecimal confidence,
            Boolean affectsProjection,
            Instant reportedAt,
            String notes,
            Instant ingestedAt) {
    }

    public record DraftPickRequest(
            @Positive Long playerId,
            @NotNull @Positive Long teamId,
            @NotNull @Min(1946) Integer draftYear,
            @Positive Integer draftRound,
            @Positive Integer draftNumber,
            @Min(1946) Integer rookieSeasonStartYear) {
    }

    public record DraftPickResponse(
            Long id,
            Long playerId,
            Long teamId,
            Integer draftYear,
            Integer draftRound,
            Integer draftNumber,
            Integer rookieSeasonStartYear) {
    }

    public record InjuryReportRequest(
            @NotNull LocalDate reportDate,
            LocalDate gameDate,
            @Positive Long teamId,
            @Positive Long playerId,
            @NotBlank String injuryStatus,
            String reason,
            String source,
            @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal confidence,
            LocalDateTime validUntil) {
    }

    public record InjuryReportResponse(
            Long id,
            LocalDate reportDate,
            LocalDate gameDate,
            Long teamId,
            Long playerId,
            String injuryStatus,
            String reason,
            String source,
            BigDecimal confidence,
            LocalDateTime validUntil,
            Instant scrapedAt,
            Instant ingestedAt) {
    }
}
