package com.bancoias.preapproved.infrastructure.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitTopologyConfig {

    public static final String EXCHANGE = "preapproved.events";
    public static final String ROUTING_KEY = "preapproved.usage.authorized";
    public static final String AUDIT_QUEUE = "preapproved.usage.authorized.audit";
    public static final String DEAD_LETTER_EXCHANGE = "preapproved.events.dlx";
    public static final String AUDIT_DLQ = "preapproved.usage.authorized.audit.dlq";

    /** Exchange topic: otros sistemas pueden suscribirse creando su propia cola, sin cambiar este servicio. */
    @Bean
    TopicExchange eventsExchange() {
        return ExchangeBuilder.topicExchange(EXCHANGE).durable(true).build();
    }

    @Bean
    DirectExchange deadLetterExchange() {
        return ExchangeBuilder.directExchange(DEAD_LETTER_EXCHANGE).durable(true).build();
    }

    @Bean
    Queue auditQueue() {
        return QueueBuilder.durable(AUDIT_QUEUE)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(AUDIT_DLQ)
                .build();
    }

    @Bean
    Queue auditDeadLetterQueue() {
        return QueueBuilder.durable(AUDIT_DLQ).build();
    }

    @Bean
    Binding auditBinding() {
        return BindingBuilder.bind(auditQueue()).to(eventsExchange()).with(ROUTING_KEY);
    }

    @Bean
    Binding auditDeadLetterBinding() {
        return BindingBuilder.bind(auditDeadLetterQueue()).to(deadLetterExchange()).with(AUDIT_DLQ);
    }
}