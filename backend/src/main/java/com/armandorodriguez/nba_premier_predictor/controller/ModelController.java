package com.armandorodriguez.nba_premier_predictor.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.armandorodriguez.nba_premier_predictor.service.MlPredictionClient;

@RestController
@RequestMapping("/api/model")
public class ModelController {

    private final MlPredictionClient mlPredictionClient;

    public ModelController(MlPredictionClient mlPredictionClient) {
        this.mlPredictionClient = mlPredictionClient;
    }

    @GetMapping("/metrics")
    Map<String, Object> metrics() {
        return mlPredictionClient.modelMetrics();
    }

    @GetMapping("/versions")
    Map<String, Object> versions() {
        return mlPredictionClient.modelVersions();
    }
}
