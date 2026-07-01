package com.armandorodriguez.nba_premier_predictor.aws;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Service
public class S3StorageService {

    private static final Logger log = LoggerFactory.getLogger(S3StorageService.class);

    private final AwsInfrastructureProperties properties;
    private final ObjectProvider<S3Client> s3Client;
    private final ObjectMapper objectMapper;

    public S3StorageService(
            AwsInfrastructureProperties properties,
            ObjectProvider<S3Client> s3Client,
            ObjectMapper objectMapper) {
        this.properties = properties;
        this.s3Client = s3Client;
        this.objectMapper = objectMapper;
    }

    public boolean putJson(String key, Object value) {
        if (!properties.enabled()) {
            return false;
        }
        S3Client client = s3Client.getIfAvailable();
        if (client == null) {
            log.warn("S3 export skipped because no S3 client is configured");
            return false;
        }
        try {
            byte[] body = objectMapper.writeValueAsBytes(value);
            client.putObject(PutObjectRequest.builder()
                            .bucket(properties.normalizedPredictionReportsBucket())
                            .key(key)
                            .contentType("application/json")
                            .contentLength((long) body.length)
                            .build(),
                    RequestBody.fromBytes(body));
            return true;
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not serialize S3 payload", ex);
        } catch (RuntimeException ex) {
            log.warn("S3 export failed for key {}: {}", key, ex.getMessage());
            return false;
        }
    }

    public String predictionReportKey(String predictionType, Long predictionId) {
        return "predictions/" + predictionType + "/" + predictionId + ".json";
    }

}
