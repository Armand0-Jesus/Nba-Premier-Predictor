package com.armandorodriguez.nba_premier_predictor;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class DockerComposeInfrastructureTests {

    @Test
    void dockerComposeIncludesLocalStackBucketAndQueueSetup() throws IOException {
        String compose = Files.readString(Path.of("..", "docker-compose.yml"));
        String initScript = Files.readString(Path.of("..", "localstack", "init-localstack.sh"));

        assertThat(compose).contains("localstack/localstack");
        assertThat(compose).contains("SERVICES: s3,sqs,secretsmanager,ssm,logs");
        assertThat(compose).contains("./localstack/init-localstack.sh:/init/init-localstack.sh:ro");
        assertThat(initScript).contains("s3 mb s3://nba-premier-prediction-reports");
        assertThat(initScript).contains("sqs create-queue --queue-name nba-premier-async-jobs");
        assertThat(compose).contains("NBA_AWS_ENABLED: \"true\"");
        assertThat(compose).contains("NBA_AWS_ENDPOINT: http://localstack:4566");
    }
}
