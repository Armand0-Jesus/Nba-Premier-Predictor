package com.armandorodriguez.nba_premier_predictor;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.EnableScheduling;

class NbaPremierPredictorApplicationTests {

    @Test
    void applicationEnablesScheduledJobs() {
        assertThat(NbaPremierPredictorApplication.class.isAnnotationPresent(EnableScheduling.class)).isTrue();
    }
}
