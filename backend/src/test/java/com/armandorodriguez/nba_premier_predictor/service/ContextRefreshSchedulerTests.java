package com.armandorodriguez.nba_premier_predictor.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;

class ContextRefreshSchedulerTests {

    @Test
    void scheduledJobUsesScheduledContextRefreshTrigger() {
        ContextRefreshService contextRefreshService = mock(ContextRefreshService.class);
        ContextRefreshScheduler scheduler = new ContextRefreshScheduler(contextRefreshService);

        scheduler.refresh();

        verify(contextRefreshService).refresh("scheduled_context_refresh");
    }
}
