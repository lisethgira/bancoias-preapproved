package com.bancoias.preapproved.infrastructure.config;

import com.bancoias.preapproved.domain.service.UsagePolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.ReactiveTransactionManager;
import org.springframework.transaction.reactive.TransactionalOperator;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;


@Configuration
@EnableScheduling
public class ApplicationConfig {

    @Bean
    public UsagePolicy usagePolicy() {
        return new UsagePolicy();
    }

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public TransactionalOperator transactionalOperator(ReactiveTransactionManager transactionManager) {
        return TransactionalOperator.create(transactionManager);
    }
}