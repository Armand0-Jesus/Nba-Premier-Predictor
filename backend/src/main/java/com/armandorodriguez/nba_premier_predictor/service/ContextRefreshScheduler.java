package com.armandorodriguez.nba_premier_predictor.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "app.context-refresh.enabled", havingValue = "true")
class ContextRefreshScheduler {

    private final ContextRefreshService contextRefreshService;

    ContextRefreshScheduler(ContextRefreshService contextRefreshService) {
        this.contextRefreshService = contextRefreshService;
    }

    @Scheduled(cron = "${app.context-refresh.cron:0 30 * * * *}")
    void refresh() {
        contextRefreshService.refresh("scheduled_context_refresh");
    }
}
