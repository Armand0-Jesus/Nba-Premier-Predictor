package com.armandorodriguez.nba_premier_predictor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableCaching
public class NbaPremierPredictorApplication {

	public static void main(String[] args) {
		SpringApplication.run(NbaPremierPredictorApplication.class, args);
	}

}
