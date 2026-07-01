package com.armandorodriguez.nba_premier_predictor.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.armandorodriguez.nba_premier_predictor.dto.ModelRetrainRequest;
import com.armandorodriguez.nba_premier_predictor.service.AsyncJobService;

class JobControllerTests {

    private AsyncJobService asyncJobService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        asyncJobService = mock(AsyncJobService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new JobController(asyncJobService)).build();
    }

    @Test
    void modelRetrainingEndpointPublishesJobWithoutRunningRetraining() throws Exception {
        when(asyncJobService.publishModelRetraining(any())).thenReturn(true);

        mockMvc.perform(post("/api/jobs/model-retraining")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "startSeason": 2014,
                                  "endSeason": 2025,
                                  "limit": 200000,
                                  "triggeredBy": "admin_job"
                                }
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobType").value("model_retraining"))
                .andExpect(jsonPath("$.published").value(true));

        ArgumentCaptor<ModelRetrainRequest> request = ArgumentCaptor.forClass(ModelRetrainRequest.class);
        verify(asyncJobService).publishModelRetraining(request.capture());
        assertThat(request.getValue().startSeason()).isEqualTo(2014);
        assertThat(request.getValue().endSeason()).isEqualTo(2025);
        assertThat(request.getValue().limit()).isEqualTo(200000);
        assertThat(request.getValue().triggeredBy()).isEqualTo("admin_job");
    }

    @Test
    void contextRefreshEndpointPublishesJobWithTriggerName() throws Exception {
        when(asyncJobService.publishContextRefresh("trusted_news")).thenReturn(true);

        mockMvc.perform(post("/api/jobs/context-refresh")
                        .param("triggeredBy", "trusted_news"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobType").value("context_refresh"))
                .andExpect(jsonPath("$.published").value(true));

        verify(asyncJobService).publishContextRefresh("trusted_news");
    }

    @Test
    void modelEvaluationAndPredictionErrorRefreshPublishJobs() throws Exception {
        when(asyncJobService.publishModelEvaluation()).thenReturn(true);
        when(asyncJobService.publishPredictionErrorRefresh()).thenReturn(true);

        mockMvc.perform(post("/api/jobs/model-evaluation"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobType").value("model_evaluation"))
                .andExpect(jsonPath("$.published").value(true));

        mockMvc.perform(post("/api/jobs/prediction-error-refresh"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobType").value("prediction_error_refresh"))
                .andExpect(jsonPath("$.published").value(true));

        verify(asyncJobService).publishModelEvaluation();
        verify(asyncJobService).publishPredictionErrorRefresh();
    }
}
