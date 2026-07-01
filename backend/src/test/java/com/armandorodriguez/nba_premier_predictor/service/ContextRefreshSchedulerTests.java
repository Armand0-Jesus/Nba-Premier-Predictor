package com.armandorodriguez.nba_premier_predictor.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

class ContextRefreshSchedulerTests {

    @Test
    void scheduledJobUsesScheduledContextRefreshTrigger() {
        ContextRefreshService contextRefreshService = mock(ContextRefreshService.class);
        ContextRefreshScheduler scheduler = new ContextRefreshScheduler(contextRefreshService);

        scheduler.refresh();

        verify(contextRefreshService).refresh("scheduled_context_refresh");
    }

    @Test
    void schedulerBeanIsCreatedOnlyWhenContextRefreshIsEnabled() {
        ApplicationContextRunner contextRunner = new ApplicationContextRunner()
                .withBean(ContextRefreshService.class, () -> mock(ContextRefreshService.class))
                .withUserConfiguration(SchedulerConfig.class);

        contextRunner
                .withPropertyValues("app.context-refresh.enabled=true")
                .run(context -> assertThat(context).hasSingleBean(ContextRefreshScheduler.class));

        contextRunner
                .withPropertyValues("app.context-refresh.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(ContextRefreshScheduler.class));
    }

    @Configuration(proxyBeanMethods = false)
    @Import(ContextRefreshScheduler.class)
    static class SchedulerConfig {
    }
}
