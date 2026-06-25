package com.armandorodriguez.nba_premier_predictor.config;

import java.time.Duration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;

@Configuration
@ConditionalOnClass(RedisCacheConfiguration.class)
class CacheConfig {

    @Bean
    RedisCacheConfiguration redisCacheConfiguration() {
        BasicPolymorphicTypeValidator typeValidator = BasicPolymorphicTypeValidator.builder()
                .allowIfSubType("com.armandorodriguez.nba_premier_predictor.")
                .allowIfSubType(Boolean.class)
                .allowIfSubType(Number.class)
                .allowIfSubType(String.class)
                .allowIfSubType("java.math.")
                .allowIfSubType("java.time.")
                .allowIfSubType("java.util.")
                .allowIfSubTypeIsArray()
                .build();

        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10))
                .disableCachingNullValues()
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(GenericJacksonJsonRedisSerializer.builder()
                                .enableDefaultTyping(typeValidator)
                                .build()));
    }
}
