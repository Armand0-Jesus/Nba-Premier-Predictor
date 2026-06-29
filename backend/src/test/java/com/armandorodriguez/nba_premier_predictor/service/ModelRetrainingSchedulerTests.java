package com.armandorodriguez.nba_premier_predictor.service;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;

class ModelRetrainingSchedulerTests {

    @Test
    void scheduledJobUsesScheduledRetrainingTrigger() {
        ModelMetadataService modelMetadataService = mock(ModelMetadataService.class);
        ModelRetrainingScheduler scheduler = new ModelRetrainingScheduler(modelMetadataService);

        scheduler.retrain();

        verify(modelMetadataService).retrain(argThat(request ->
                request != null && "scheduled".equals(request.normalizedTriggeredBy())));
    }
}
