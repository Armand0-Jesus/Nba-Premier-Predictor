package com.armandorodriguez.nba_premier_predictor.domain;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "team_game_stats")
public class TeamGameStats {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "game_id")
    private Long gameId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "game_id", insertable = false, updatable = false)
    private Game game;

    @Column(name = "team_id")
    private Long teamId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id", insertable = false, updatable = false)
    private Team team;

    @Column(name = "opponent_team_id")
    private Long opponentTeamId;

    private Boolean home;
    private Boolean win;

    @Column(name = "team_score")
    private Integer teamScore;

    @Column(name = "opponent_score")
    private Integer opponentScore;

    private Integer assists;
    private Integer blocks;
    private Integer steals;

    @Column(name = "rebounds_total")
    private Integer reboundsTotal;

    private Integer turnovers;

    @Column(name = "num_minutes", precision = 6, scale = 2)
    private BigDecimal numMinutes;

    protected TeamGameStats() {
    }

    public Long getId() {
        return id;
    }

    public Long getGameId() {
        return gameId;
    }

    public Game getGame() {
        return game;
    }

    public Long getTeamId() {
        return teamId;
    }

    public Team getTeam() {
        return team;
    }

    public Long getOpponentTeamId() {
        return opponentTeamId;
    }

    public Boolean getHome() {
        return home;
    }

    public Boolean getWin() {
        return win;
    }

    public Integer getTeamScore() {
        return teamScore;
    }

    public Integer getOpponentScore() {
        return opponentScore;
    }

    public Integer getAssists() {
        return assists;
    }

    public Integer getBlocks() {
        return blocks;
    }

    public Integer getSteals() {
        return steals;
    }

    public Integer getReboundsTotal() {
        return reboundsTotal;
    }

    public Integer getTurnovers() {
        return turnovers;
    }

    public BigDecimal getNumMinutes() {
        return numMinutes;
    }
}
