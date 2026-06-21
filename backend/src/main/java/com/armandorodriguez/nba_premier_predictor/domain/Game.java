package com.armandorodriguez.nba_premier_predictor.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "games")
public class Game {

    @Id
    @Column(name = "game_id")
    private Long id;

    @Column(name = "season_start_year")
    private Integer seasonStartYear;

    @Column(name = "game_date_time_est")
    private LocalDateTime gameDateTimeEst;

    @Column(name = "game_date")
    private LocalDate gameDate;

    @Column(name = "home_team_id")
    private Long homeTeamId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "home_team_id", insertable = false, updatable = false)
    private Team homeTeam;

    @Column(name = "away_team_id")
    private Long awayTeamId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "away_team_id", insertable = false, updatable = false)
    private Team awayTeam;

    @Column(name = "home_team_city")
    private String homeTeamCity;

    @Column(name = "home_team_name")
    private String homeTeamName;

    @Column(name = "away_team_city")
    private String awayTeamCity;

    @Column(name = "away_team_name")
    private String awayTeamName;

    @Column(name = "home_score")
    private Integer homeScore;

    @Column(name = "away_score")
    private Integer awayScore;

    @Column(name = "winner_team_id")
    private Long winnerTeamId;

    @Column(name = "game_type")
    private String gameType;

    @Column(name = "game_subtype")
    private String gameSubtype;

    @Column(name = "game_label")
    private String gameLabel;

    @Column(name = "game_sub_label")
    private String gameSubLabel;

    @Column(name = "series_game_number")
    private Integer seriesGameNumber;

    private Integer attendance;

    @Column(name = "arena_name")
    private String arenaName;

    @Column(name = "arena_city")
    private String arenaCity;

    @Column(name = "arena_state")
    private String arenaState;

    protected Game() {
    }

    public Long getId() {
        return id;
    }

    public Integer getSeasonStartYear() {
        return seasonStartYear;
    }

    public LocalDateTime getGameDateTimeEst() {
        return gameDateTimeEst;
    }

    public LocalDate getGameDate() {
        return gameDate;
    }

    public Long getHomeTeamId() {
        return homeTeamId;
    }

    public Team getHomeTeam() {
        return homeTeam;
    }

    public Long getAwayTeamId() {
        return awayTeamId;
    }

    public Team getAwayTeam() {
        return awayTeam;
    }

    public String getHomeTeamCity() {
        return homeTeamCity;
    }

    public String getHomeTeamName() {
        return homeTeamName;
    }

    public String getAwayTeamCity() {
        return awayTeamCity;
    }

    public String getAwayTeamName() {
        return awayTeamName;
    }

    public Integer getHomeScore() {
        return homeScore;
    }

    public Integer getAwayScore() {
        return awayScore;
    }

    public Long getWinnerTeamId() {
        return winnerTeamId;
    }

    public String getGameType() {
        return gameType;
    }

    public String getGameSubtype() {
        return gameSubtype;
    }

    public String getGameLabel() {
        return gameLabel;
    }

    public String getGameSubLabel() {
        return gameSubLabel;
    }

    public Integer getSeriesGameNumber() {
        return seriesGameNumber;
    }

    public Integer getAttendance() {
        return attendance;
    }

    public String getArenaName() {
        return arenaName;
    }

    public String getArenaCity() {
        return arenaCity;
    }

    public String getArenaState() {
        return arenaState;
    }
}
