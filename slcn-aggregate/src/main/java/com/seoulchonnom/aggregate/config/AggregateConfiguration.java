package com.seoulchonnom.aggregate.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@EnableJpaRepositories(basePackages = "com.seoulchonnom.aggregate")
@EntityScan(basePackages = "com.seoulchonnom.aggregate")
public class AggregateConfiguration {
}
