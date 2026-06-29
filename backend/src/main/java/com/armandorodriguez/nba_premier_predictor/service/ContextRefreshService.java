package com.armandorodriguez.nba_premier_predictor.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.armandorodriguez.nba_premier_predictor.config.ContextRefreshProperties;
import com.armandorodriguez.nba_premier_predictor.dto.ContextDtos.ContextRefreshResponse;
import com.armandorodriguez.nba_premier_predictor.dto.ContextDtos.DraftPickRequest;
import com.armandorodriguez.nba_premier_predictor.dto.ContextDtos.InjuryReportRequest;
import com.armandorodriguez.nba_premier_predictor.dto.ContextDtos.RosterSnapshotRequest;
import com.armandorodriguez.nba_premier_predictor.dto.ContextDtos.TransactionRequest;
import com.armandorodriguez.nba_premier_predictor.dto.ModelRetrainRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class ContextRefreshService {

    private static final String DEFAULT_SOURCE_STATUS = "trusted_report";

    private final ContextRefreshProperties properties;
    private final ContextRefreshSourceClient sourceClient;
    private final ObjectMapper objectMapper;
    private final ContextDataService contextDataService;
    private final StandingsProjectionService standingsProjectionService;
    private final ModelMetadataService modelMetadataService;
    private final JdbcTemplate jdbcTemplate;

    public ContextRefreshService(
            ContextRefreshProperties properties,
            ContextRefreshSourceClient sourceClient,
            ObjectMapper objectMapper,
            ContextDataService contextDataService,
            StandingsProjectionService standingsProjectionService,
            ModelMetadataService modelMetadataService,
            JdbcTemplate jdbcTemplate) {
        this.properties = properties;
        this.sourceClient = sourceClient;
        this.objectMapper = objectMapper;
        this.contextDataService = contextDataService;
        this.standingsProjectionService = standingsProjectionService;
        this.modelMetadataService = modelMetadataService;
        this.jdbcTemplate = jdbcTemplate;
    }

    public ContextRefreshResponse refresh(String triggeredBy) {
        Instant startedAt = Instant.now();
        List<String> sourceUrls = properties.normalizedSourceUrls();
        if (sourceUrls.isEmpty()) {
            ContextRefreshResponse response = ContextRefreshResponse.empty("No trusted context sources configured");
            logRefresh(startedAt, response, triggeredBy, null);
            return response;
        }

        int sourcesSucceeded = 0;
        int sourcesFailed = 0;
        int transactionsAccepted = 0;
        int rosterSnapshotsAccepted = 0;
        int draftPicksAccepted = 0;
        int injuryReportsAccepted = 0;
        String lastError = null;

        for (String sourceUrl : sourceUrls) {
            try {
                ContextRefreshBatch batch = parse(sourceUrl, sourceClient.fetch(sourceUrl));
                int sourceRecords = batch.totalRecords();
                if (!batch.rosters().isEmpty()) {
                    rosterSnapshotsAccepted += contextDataService.ingestRosters(batch.rosters()).recordsAccepted();
                }
                if (!batch.transactions().isEmpty()) {
                    transactionsAccepted += contextDataService.ingestTransactions(batch.transactions()).recordsAccepted();
                }
                if (!batch.draftPicks().isEmpty()) {
                    draftPicksAccepted += contextDataService.ingestDraftPicks(batch.draftPicks()).recordsAccepted();
                }
                if (!batch.injuries().isEmpty()) {
                    injuryReportsAccepted += contextDataService.ingestInjuries(batch.injuries()).recordsAccepted();
                }
                sourcesSucceeded++;
                logSource(sourceUrl, "completed", sourceRecords, null);
            } catch (RuntimeException ex) {
                sourcesFailed++;
                lastError = ex.getMessage();
                logSource(sourceUrl, "failed", 0, lastError);
            }
        }

        int totalAccepted = transactionsAccepted + rosterSnapshotsAccepted + draftPicksAccepted + injuryReportsAccepted;
        boolean projectionsRefreshed = false;
        boolean retrainingTriggered = false;
        if (totalAccepted > 0 && properties.isRefreshProjections()) {
            standingsProjectionService.projections(properties.getProjectionSeason());
            projectionsRefreshed = true;
        }
        if (totalAccepted > 0 && properties.isRetrainAfterIngestion()) {
            modelMetadataService.retrain(new ModelRetrainRequest(null, null, null, null, null, "context_refresh"));
            retrainingTriggered = true;
        }

        String message = sourcesFailed == 0
                ? "Context refresh completed"
                : "Context refresh completed with source errors";
        ContextRefreshResponse response = new ContextRefreshResponse(
                sourceUrls.size(),
                sourcesSucceeded,
                sourcesFailed,
                totalAccepted,
                transactionsAccepted,
                rosterSnapshotsAccepted,
                draftPicksAccepted,
                injuryReportsAccepted,
                projectionsRefreshed,
                retrainingTriggered,
                message);
        logRefresh(startedAt, response, triggeredBy, lastError);
        return response;
    }

    private ContextRefreshBatch parse(String sourceUrl, String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            String defaultSource = text(root, "source", sourceUrl);
            String defaultSourceUrl = text(root, "sourceUrl", sourceUrl);
            String defaultStatus = text(root, "sourceStatus", DEFAULT_SOURCE_STATUS);
            BigDecimal defaultConfidence = decimal(root, "confidence");
            Instant defaultReportedAt = instant(root, "reportedAt");
            return new ContextRefreshBatch(
                    rosters(root, defaultSource),
                    transactions(root, defaultSource, defaultSourceUrl, defaultStatus, defaultConfidence, defaultReportedAt),
                    draftPicks(root),
                    injuries(root, defaultSource));
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Context source did not return valid JSON", ex);
        }
    }

    private List<RosterSnapshotRequest> rosters(JsonNode root, String defaultSource) {
        List<RosterSnapshotRequest> records = new ArrayList<>();
        for (JsonNode node : array(root, "rosters", "rosterSnapshots")) {
            records.add(new RosterSnapshotRequest(
                    localDate(node, "snapshotDate"),
                    longValue(node, "teamId"),
                    longValue(node, "playerId"),
                    text(node, "position", null),
                    text(node, "rosterStatus", null),
                    decimal(node, "projectedMinutes"),
                    text(node, "source", defaultSource)));
        }
        return records;
    }

    private List<TransactionRequest> transactions(
            JsonNode root,
            String defaultSource,
            String defaultSourceUrl,
            String defaultStatus,
            BigDecimal defaultConfidence,
            Instant defaultReportedAt) {
        List<TransactionRequest> records = new ArrayList<>();
        for (JsonNode node : array(root, "transactions")) {
            records.add(new TransactionRequest(
                    longValue(node, "playerId"),
                    longValueOrNull(node, "fromTeamId"),
                    longValueOrNull(node, "toTeamId"),
                    requiredText(node, "transactionType"),
                    localDateOrNull(node, "transactionDate"),
                    text(node, "source", defaultSource),
                    text(node, "sourceUrl", defaultSourceUrl),
                    text(node, "sourceStatus", defaultStatus),
                    decimal(node, "confidence", defaultConfidence),
                    booleanValue(node, "affectsProjection"),
                    instant(node, "reportedAt", defaultReportedAt),
                    text(node, "notes", null)));
        }
        return records;
    }

    private List<DraftPickRequest> draftPicks(JsonNode root) {
        List<DraftPickRequest> records = new ArrayList<>();
        for (JsonNode node : array(root, "draftPicks", "draft_picks")) {
            records.add(new DraftPickRequest(
                    longValueOrNull(node, "playerId"),
                    longValue(node, "teamId"),
                    intValue(node, "draftYear"),
                    intValueOrNull(node, "draftRound"),
                    intValueOrNull(node, "draftNumber"),
                    intValueOrNull(node, "rookieSeasonStartYear")));
        }
        return records;
    }

    private List<InjuryReportRequest> injuries(JsonNode root, String defaultSource) {
        List<InjuryReportRequest> records = new ArrayList<>();
        for (JsonNode node : array(root, "injuries", "injuryReports")) {
            records.add(new InjuryReportRequest(
                    localDate(node, "reportDate"),
                    localDateOrNull(node, "gameDate"),
                    longValueOrNull(node, "teamId"),
                    longValueOrNull(node, "playerId"),
                    requiredText(node, "injuryStatus"),
                    text(node, "reason", null),
                    text(node, "source", defaultSource),
                    decimal(node, "confidence"),
                    localDateTimeOrNull(node, "validUntil")));
        }
        return records;
    }

    private void logRefresh(
            Instant startedAt,
            ContextRefreshResponse response,
            String triggeredBy,
            String errorMessage) {
        String status = response.sourcesFailed() == 0 ? "completed" : response.sourcesSucceeded() == 0 ? "failed" : "partial";
        jdbcTemplate.update("""
                insert into refresh_logs (
                    refresh_type, started_at, finished_at, status, records_processed, error_message
                ) values (?, ?, ?, ?, ?, ?)
                """,
                trigger(triggeredBy),
                java.sql.Timestamp.from(startedAt),
                java.sql.Timestamp.from(Instant.now()),
                status,
                response.recordsAccepted(),
                errorMessage);
    }

    private void logSource(String sourceUrl, String status, int recordsProcessed, String notes) {
        jdbcTemplate.update("""
                insert into data_source_logs (
                    source_name, source_type, source_path, status, records_processed, notes
                ) values (?, ?, ?, ?, ?, ?)
                """,
                sourceUrl,
                "context_feed",
                sourceUrl,
                status,
                recordsProcessed,
                notes);
    }

    private static String trigger(String triggeredBy) {
        return triggeredBy == null || triggeredBy.isBlank() ? "manual_context_refresh" : triggeredBy;
    }

    private static Iterable<JsonNode> array(JsonNode root, String... names) {
        for (String name : names) {
            JsonNode node = root.get(name);
            if (node != null && node.isArray()) {
                return node;
            }
        }
        return List.of();
    }

    private static String requiredText(JsonNode node, String name) {
        String value = text(node, name, null);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }

    private static String text(JsonNode node, String name, String fallback) {
        JsonNode value = node.get(name);
        if (value == null || value.isNull()) {
            return fallback;
        }
        String text = value.asText();
        return text == null || text.isBlank() ? fallback : text;
    }

    private static Long longValue(JsonNode node, String name) {
        Long value = longValueOrNull(node, name);
        if (value == null) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }

    private static Long longValueOrNull(JsonNode node, String name) {
        JsonNode value = node.get(name);
        return value == null || value.isNull() ? null : value.asLong();
    }

    private static Integer intValue(JsonNode node, String name) {
        Integer value = intValueOrNull(node, name);
        if (value == null) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }

    private static Integer intValueOrNull(JsonNode node, String name) {
        JsonNode value = node.get(name);
        return value == null || value.isNull() ? null : value.asInt();
    }

    private static Boolean booleanValue(JsonNode node, String name) {
        JsonNode value = node.get(name);
        return value == null || value.isNull() ? null : value.asBoolean();
    }

    private static BigDecimal decimal(JsonNode node, String name) {
        return decimal(node, name, null);
    }

    private static BigDecimal decimal(JsonNode node, String name, BigDecimal fallback) {
        JsonNode value = node.get(name);
        return value == null || value.isNull() ? fallback : value.decimalValue();
    }

    private static LocalDate localDate(JsonNode node, String name) {
        LocalDate value = localDateOrNull(node, name);
        if (value == null) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }

    private static LocalDate localDateOrNull(JsonNode node, String name) {
        String value = text(node, name, null);
        return value == null ? null : LocalDate.parse(value);
    }

    private static LocalDateTime localDateTimeOrNull(JsonNode node, String name) {
        String value = text(node, name, null);
        return value == null ? null : LocalDateTime.parse(value);
    }

    private static Instant instant(JsonNode node, String name) {
        return instant(node, name, null);
    }

    private static Instant instant(JsonNode node, String name, Instant fallback) {
        String value = text(node, name, null);
        if (value == null) {
            return fallback;
        }
        return value.endsWith("Z") || value.contains("+") || value.matches(".*-\\d\\d:\\d\\d$")
                ? OffsetDateTime.parse(value).toInstant()
                : LocalDateTime.parse(value).atZone(java.time.ZoneOffset.UTC).toInstant();
    }

    private record ContextRefreshBatch(
            List<RosterSnapshotRequest> rosters,
            List<TransactionRequest> transactions,
            List<DraftPickRequest> draftPicks,
            List<InjuryReportRequest> injuries) {

        int totalRecords() {
            return rosters.size() + transactions.size() + draftPicks.size() + injuries.size();
        }
    }
}
