package com.armandorodriguez.nba_premier_predictor.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.armandorodriguez.nba_premier_predictor.dto.ModelRetrainRequest;
import com.armandorodriguez.nba_premier_predictor.service.ModelMetadataService;

@RestController
@RequestMapping("/api/model")
public class ModelController {

    private final ModelMetadataService modelMetadataService;

    public ModelController(ModelMetadataService modelMetadataService) {
        this.modelMetadataService = modelMetadataService;
    }

    @GetMapping("/metrics")
    public Map<String, Object> metrics() {
        return modelMetadataService.metrics();
    }

    @GetMapping("/versions")
    public Map<String, Object> versions() {
        return modelMetadataService.versions();
    }

    @GetMapping("/versions/active")
    public Object activeVersions() {
        return modelMetadataService.activeVersions();
    }

    @GetMapping("/training-runs")
    public Object trainingRuns() {
        return modelMetadataService.trainingRuns();
    }

    @GetMapping("/promotion-history")
    public Object promotionHistory() {
        return modelMetadataService.promotionHistory();
    }

    @PostMapping("/evaluate")
    public Map<String, Object> evaluate() {
        return modelMetadataService.evaluate();
    }

    @PostMapping("/retrain")
    public Map<String, Object> retrain(@RequestBody(required = false) ModelRetrainRequest request) {
        return modelMetadataService.retrain(request);
    }

    @PostMapping("/promote/{modelVersionId}")
    public Map<String, Object> promote(@PathVariable Long modelVersionId) {
        return modelMetadataService.promote(modelVersionId);
    }

    @PostMapping("/rollback/{modelVersionId}")
    public Map<String, Object> rollback(@PathVariable Long modelVersionId) {
        return modelMetadataService.rollback(modelVersionId);
    }
}
