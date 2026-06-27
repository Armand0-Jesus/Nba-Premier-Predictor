package com.armandorodriguez.nba_premier_predictor.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.armandorodriguez.nba_premier_predictor.dto.SeasonResponse;
import com.armandorodriguez.nba_premier_predictor.service.SeasonService;

@RestController
@RequestMapping("/api/seasons")
public class SeasonController {

    private final SeasonService seasonService;

    public SeasonController(SeasonService seasonService) {
        this.seasonService = seasonService;
    }

    @GetMapping
    List<SeasonResponse> allSeasons() {
        return seasonService.allSeasons();
    }
}
