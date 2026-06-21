package com.armandorodriguez.nba_premier_predictor.domain;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "players")
public class Player {

    @Id
    @Column(name = "player_id")
    private Long id;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name")
    private String lastName;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    private String school;
    private String country;

    @Column(name = "height_inches")
    private Integer heightInches;

    @Column(name = "body_weight_lbs")
    private Integer bodyWeightLbs;

    private String jersey;

    @Column(name = "is_guard")
    private boolean guard;

    @Column(name = "is_forward")
    private boolean forward;

    @Column(name = "is_center")
    private boolean center;

    @Column(name = "dleague_flag")
    private boolean dleagueFlag;

    @Column(name = "nba_flag")
    private boolean nbaFlag;

    @Column(name = "games_played_flag")
    private boolean gamesPlayedFlag;

    @Column(name = "draft_year")
    private Integer draftYear;

    @Column(name = "draft_round")
    private Integer draftRound;

    @Column(name = "draft_number")
    private Integer draftNumber;

    @Column(name = "from_year")
    private Integer fromYear;

    @Column(name = "to_year")
    private Integer toYear;

    protected Player() {
    }

    public Long getId() {
        return id;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getFullName() {
        return String.join(" ", firstName == null ? "" : firstName, lastName == null ? "" : lastName).trim();
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public String getSchool() {
        return school;
    }

    public String getCountry() {
        return country;
    }

    public Integer getHeightInches() {
        return heightInches;
    }

    public Integer getBodyWeightLbs() {
        return bodyWeightLbs;
    }

    public String getJersey() {
        return jersey;
    }

    public boolean isGuard() {
        return guard;
    }

    public boolean isForward() {
        return forward;
    }

    public boolean isCenter() {
        return center;
    }

    public boolean isNbaFlag() {
        return nbaFlag;
    }

    public Integer getDraftYear() {
        return draftYear;
    }

    public Integer getDraftRound() {
        return draftRound;
    }

    public Integer getDraftNumber() {
        return draftNumber;
    }

    public Integer getFromYear() {
        return fromYear;
    }

    public Integer getToYear() {
        return toYear;
    }

    public String getPosition() {
        String position = "";
        if (guard) {
            position += "G";
        }
        if (forward) {
            position += position.isEmpty() ? "F" : "/F";
        }
        if (center) {
            position += position.isEmpty() ? "C" : "/C";
        }
        return position.isEmpty() ? null : position;
    }
}
