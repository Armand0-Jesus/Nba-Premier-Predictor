package com.armandorodriguez.nba_premier_predictor.service;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.armandorodriguez.nba_premier_predictor.dto.ContextDtos.ContextIngestionResponse;
import com.armandorodriguez.nba_premier_predictor.dto.ContextDtos.DraftPickRequest;
import com.armandorodriguez.nba_premier_predictor.dto.ContextDtos.DraftPickResponse;
import com.armandorodriguez.nba_premier_predictor.dto.ContextDtos.InjuryReportRequest;
import com.armandorodriguez.nba_premier_predictor.dto.ContextDtos.InjuryReportResponse;
import com.armandorodriguez.nba_premier_predictor.dto.ContextDtos.RosterSnapshotRequest;
import com.armandorodriguez.nba_premier_predictor.dto.ContextDtos.RosterSnapshotResponse;
import com.armandorodriguez.nba_premier_predictor.dto.ContextDtos.TransactionRequest;
import com.armandorodriguez.nba_premier_predictor.dto.ContextDtos.TransactionResponse;

@Service
public class ContextDataService {

    private final JdbcTemplate jdbcTemplate;

    public ContextDataService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public ContextIngestionResponse ingestRosters(List<RosterSnapshotRequest> records) {
        for (RosterSnapshotRequest record : records) {
            jdbcTemplate.update("""
                    delete from roster_snapshots
                    where snapshot_date = ? and team_id = ? and player_id = ?
                    """, record.snapshotDate(), record.teamId(), record.playerId());
            jdbcTemplate.update("""
                    insert into roster_snapshots (
                        snapshot_date, team_id, player_id, position, roster_status,
                        projected_minutes, source
                    ) values (?, ?, ?, ?, ?, ?, ?)
                    """,
                    record.snapshotDate(),
                    record.teamId(),
                    record.playerId(),
                    record.position(),
                    record.rosterStatus(),
                    record.projectedMinutes(),
                    record.source());
        }
        return new ContextIngestionResponse(records.size());
    }

    @Transactional
    public ContextIngestionResponse ingestTransactions(List<TransactionRequest> records) {
        for (TransactionRequest record : records) {
            jdbcTemplate.update("""
                    insert into transactions (
                        player_id, from_team_id, to_team_id, transaction_type,
                        transaction_date, source, source_url, source_status,
                        confidence, affects_projection, reported_at, notes
                    ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    record.playerId(),
                    record.fromTeamId(),
                    record.toTeamId(),
                    record.transactionType(),
                    record.transactionDate(),
                    record.source(),
                    record.sourceUrl(),
                    sourceStatus(record.sourceStatus()),
                    confidence(record.confidence(), record.sourceStatus()),
                    affectsProjection(record.affectsProjection(), record.sourceStatus()),
                    timestamp(record.reportedAt()),
                    record.notes());
        }
        return new ContextIngestionResponse(records.size());
    }

    @Transactional
    public ContextIngestionResponse ingestDraftPicks(List<DraftPickRequest> records) {
        for (DraftPickRequest record : records) {
            if (record.playerId() == null) {
                jdbcTemplate.update("""
                        delete from draft_picks
                        where team_id = ? and draft_year = ? and draft_number = ?
                        """, record.teamId(), record.draftYear(), record.draftNumber());
            } else {
                jdbcTemplate.update("""
                        delete from draft_picks
                        where player_id = ? and draft_year = ?
                        """, record.playerId(), record.draftYear());
            }
            jdbcTemplate.update("""
                    insert into draft_picks (
                        player_id, team_id, draft_year, draft_round, draft_number,
                        rookie_season_start_year
                    ) values (?, ?, ?, ?, ?, ?)
                    """,
                    record.playerId(),
                    record.teamId(),
                    record.draftYear(),
                    record.draftRound(),
                    record.draftNumber(),
                    record.rookieSeasonStartYear());
        }
        return new ContextIngestionResponse(records.size());
    }

    @Transactional
    public ContextIngestionResponse ingestInjuries(List<InjuryReportRequest> records) {
        for (InjuryReportRequest record : records) {
            jdbcTemplate.update("""
                    insert into injury_reports (
                        report_date, game_date, team_id, player_id, injury_status,
                        reason, source, confidence, valid_until
                    ) values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    record.reportDate(),
                    record.gameDate(),
                    record.teamId(),
                    record.playerId(),
                    record.injuryStatus(),
                    record.reason(),
                    record.source(),
                    record.confidence(),
                    timestamp(record.validUntil()));
        }
        return new ContextIngestionResponse(records.size());
    }

    public List<RosterSnapshotResponse> rosters(Long teamId, LocalDate snapshotDate, int limit) {
        StringBuilder sql = new StringBuilder("""
                select id, snapshot_date, team_id, player_id, position, roster_status,
                       projected_minutes, source, ingested_at
                from roster_snapshots
                where 1 = 1
                """);
        List<Object> params = new ArrayList<>();
        if (teamId != null) {
            sql.append(" and team_id = ?");
            params.add(teamId);
        }
        if (snapshotDate != null) {
            sql.append(" and snapshot_date = ?");
            params.add(snapshotDate);
        }
        sql.append(" order by snapshot_date desc, team_id, player_id limit ?");
        params.add(limit);
        return jdbcTemplate.query(sql.toString(), ContextDataService::roster, params.toArray());
    }

    public List<TransactionResponse> transactions(
            Long playerId,
            Long teamId,
            LocalDate fromDate,
            LocalDate toDate,
            int limit) {
        StringBuilder sql = new StringBuilder("""
                select id, player_id, from_team_id, to_team_id, transaction_type,
                       transaction_date, source, source_url, source_status, confidence,
                       affects_projection, reported_at, notes, ingested_at
                from transactions
                where 1 = 1
                """);
        List<Object> params = new ArrayList<>();
        if (playerId != null) {
            sql.append(" and player_id = ?");
            params.add(playerId);
        }
        if (teamId != null) {
            sql.append(" and (from_team_id = ? or to_team_id = ?)");
            params.add(teamId);
            params.add(teamId);
        }
        if (fromDate != null) {
            sql.append(" and transaction_date >= ?");
            params.add(fromDate);
        }
        if (toDate != null) {
            sql.append(" and transaction_date <= ?");
            params.add(toDate);
        }
        sql.append(" order by transaction_date desc nulls last, id desc limit ?");
        params.add(limit);
        return jdbcTemplate.query(sql.toString(), ContextDataService::transaction, params.toArray());
    }

    public List<DraftPickResponse> draftPicks(Integer draftYear, Long teamId, Long playerId, int limit) {
        StringBuilder sql = new StringBuilder("""
                select id, player_id, team_id, draft_year, draft_round, draft_number,
                       rookie_season_start_year
                from draft_picks
                where 1 = 1
                """);
        List<Object> params = new ArrayList<>();
        if (draftYear != null) {
            sql.append(" and draft_year = ?");
            params.add(draftYear);
        }
        if (teamId != null) {
            sql.append(" and team_id = ?");
            params.add(teamId);
        }
        if (playerId != null) {
            sql.append(" and player_id = ?");
            params.add(playerId);
        }
        sql.append(" order by draft_year desc, draft_number nulls last, id desc limit ?");
        params.add(limit);
        return jdbcTemplate.query(sql.toString(), ContextDataService::draftPick, params.toArray());
    }

    public List<InjuryReportResponse> injuries(Long teamId, Long playerId, LocalDate gameDate, int limit) {
        StringBuilder sql = new StringBuilder("""
                select id, report_date, game_date, team_id, player_id, injury_status,
                       reason, source, confidence, valid_until, scraped_at, ingested_at
                from injury_reports
                where 1 = 1
                """);
        List<Object> params = new ArrayList<>();
        if (teamId != null) {
            sql.append(" and team_id = ?");
            params.add(teamId);
        }
        if (playerId != null) {
            sql.append(" and player_id = ?");
            params.add(playerId);
        }
        if (gameDate != null) {
            sql.append(" and game_date = ?");
            params.add(gameDate);
        }
        sql.append(" order by report_date desc, id desc limit ?");
        params.add(limit);
        return jdbcTemplate.query(sql.toString(), ContextDataService::injury, params.toArray());
    }

    private static RosterSnapshotResponse roster(ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new RosterSnapshotResponse(
                rs.getLong("id"),
                rs.getDate("snapshot_date").toLocalDate(),
                rs.getLong("team_id"),
                rs.getLong("player_id"),
                rs.getString("position"),
                rs.getString("roster_status"),
                rs.getBigDecimal("projected_minutes"),
                rs.getString("source"),
                instant(rs, "ingested_at"));
    }

    private static TransactionResponse transaction(ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new TransactionResponse(
                rs.getLong("id"),
                rs.getLong("player_id"),
                nullableLong(rs, "from_team_id"),
                nullableLong(rs, "to_team_id"),
                rs.getString("transaction_type"),
                nullableDate(rs, "transaction_date"),
                rs.getString("source"),
                rs.getString("source_url"),
                rs.getString("source_status"),
                rs.getBigDecimal("confidence"),
                rs.getBoolean("affects_projection"),
                instant(rs, "reported_at"),
                rs.getString("notes"),
                instant(rs, "ingested_at"));
    }

    private static DraftPickResponse draftPick(ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new DraftPickResponse(
                rs.getLong("id"),
                nullableLong(rs, "player_id"),
                nullableLong(rs, "team_id"),
                rs.getInt("draft_year"),
                nullableInt(rs, "draft_round"),
                nullableInt(rs, "draft_number"),
                nullableInt(rs, "rookie_season_start_year"));
    }

    private static InjuryReportResponse injury(ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new InjuryReportResponse(
                rs.getLong("id"),
                rs.getDate("report_date").toLocalDate(),
                nullableDate(rs, "game_date"),
                nullableLong(rs, "team_id"),
                nullableLong(rs, "player_id"),
                rs.getString("injury_status"),
                rs.getString("reason"),
                rs.getString("source"),
                rs.getBigDecimal("confidence"),
                nullableDateTime(rs, "valid_until"),
                instant(rs, "scraped_at"),
                instant(rs, "ingested_at"));
    }

    private static String sourceStatus(String value) {
        if (value == null || value.isBlank()) {
            return "official";
        }
        return value.trim().toLowerCase();
    }

    private static BigDecimal confidence(BigDecimal value, String sourceStatus) {
        if (value != null) {
            return value;
        }
        return switch (sourceStatus(sourceStatus)) {
            case "rumor" -> new BigDecimal("0.2500");
            case "unconfirmed" -> new BigDecimal("0.5000");
            case "trusted_report" -> new BigDecimal("0.8500");
            default -> BigDecimal.ONE;
        };
    }

    private static boolean affectsProjection(Boolean value, String sourceStatus) {
        if (value != null) {
            return value;
        }
        return switch (sourceStatus(sourceStatus)) {
            case "rumor", "unconfirmed" -> false;
            default -> true;
        };
    }

    private static Timestamp timestamp(LocalDateTime value) {
        return value == null ? null : Timestamp.valueOf(value);
    }

    private static Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private static Instant instant(ResultSet rs, String column) throws java.sql.SQLException {
        Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static Long nullableLong(ResultSet rs, String column) throws java.sql.SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static Integer nullableInt(ResultSet rs, String column) throws java.sql.SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private static LocalDate nullableDate(ResultSet rs, String column) throws java.sql.SQLException {
        java.sql.Date value = rs.getDate(column);
        return value == null ? null : value.toLocalDate();
    }

    private static LocalDateTime nullableDateTime(ResultSet rs, String column) throws java.sql.SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toLocalDateTime();
    }
}
