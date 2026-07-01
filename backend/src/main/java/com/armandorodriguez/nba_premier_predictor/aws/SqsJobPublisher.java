package com.armandorodriguez.nba_premier_predictor.aws;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

@Service
public class SqsJobPublisher {

    private static final Logger log = LoggerFactory.getLogger(SqsJobPublisher.class);

    private final AwsInfrastructureProperties properties;
    private final ObjectProvider<SqsClient> sqsClient;
    private final ObjectMapper objectMapper;

    public SqsJobPublisher(
            AwsInfrastructureProperties properties,
            ObjectProvider<SqsClient> sqsClient,
            ObjectMapper objectMapper) {
        this.properties = properties;
        this.sqsClient = sqsClient;
        this.objectMapper = objectMapper;
    }

    public boolean publish(String jobType, Object payload) {
        if (!properties.enabled()) {
            return false;
        }
        SqsClient client = sqsClient.getIfAvailable();
        if (client == null) {
            log.warn("SQS publish skipped because no SQS client is configured");
            return false;
        }
        try {
            client.sendMessage(SendMessageRequest.builder()
                    .queueUrl(properties.normalizedAsyncJobsQueueUrl())
                    .messageBody(jobBody(jobType, payload))
                    .build());
            return true;
        } catch (RuntimeException ex) {
            log.warn("SQS publish failed for job type {}: {}", jobType, ex.getMessage());
            return false;
        }
    }

    private String jobBody(String jobType, Object payload) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("jobType", jobType);
        body.put("requestedAt", Instant.now().toString());
        body.put("payload", payload == null ? Map.of() : payload);
        try {
            return objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not serialize SQS job payload", ex);
        }
    }
}
