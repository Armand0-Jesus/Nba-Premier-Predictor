package com.armandorodriguez.nba_premier_predictor.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

class ModelRetrainingSchedulerTests {

    @Test
    void scheduledJobUsesScheduledRetrainingTrigger() {
        ModelMetadataService modelMetadataService = mock(ModelMetadataService.class);
        ModelRetrainingScheduler scheduler = new ModelRetrainingScheduler(modelMetadataService);

        scheduler.retrain();

        verify(modelMetadataService).retrain(argThat(request ->
                request != null && "scheduled".equals(request.normalizedTriggeredBy())));
    }

    @Test
    void schedulerBeanIsCreatedOnlyWhenRetrainingIsEnabled() {
        ApplicationContextRunner contextRunner = new ApplicationContextRunner()
                .withBean(ModelMetadataService.class, () -> mock(ModelMetadataService.class))
                .withUserConfiguration(SchedulerConfig.class);

        contextRunner
                .withPropertyValues("app.model-retraining.enabled=true")
                .run(context -> assertThat(context).hasSingleBean(ModelRetrainingScheduler.class));

        contextRunner
                .withPropertyValues("app.model-retraining.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(ModelRetrainingScheduler.class));
    }

    @Configuration(proxyBeanMethods = false)
    @Import(ModelRetrainingScheduler.class)
    static class SchedulerConfig {
    }
}
