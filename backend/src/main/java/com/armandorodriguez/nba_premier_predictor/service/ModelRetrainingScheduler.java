package com.armandorodriguez.nba_premier_predictor.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.armandorodriguez.nba_premier_predictor.dto.ModelRetrainRequest;

@Service
@ConditionalOnProperty(name = "app.model-retraining.enabled", havingValue = "true")
class ModelRetrainingScheduler {

    private final ModelMetadataService modelMetadataService;

    ModelRetrainingScheduler(ModelMetadataService modelMetadataService) {
        this.modelMetadataService = modelMetadataService;
    }

    @Scheduled(cron = "${app.model-retraining.cron:0 0 4 * * *}")
    void retrain() {
        modelMetadataService.retrain(new ModelRetrainRequest(null, null, null, null, null, "scheduled"));
    }
}
