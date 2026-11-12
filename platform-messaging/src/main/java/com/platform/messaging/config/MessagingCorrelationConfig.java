package com.platform.messaging.config;

import com.platform.common.correlation.CorrelationIds;
import com.platform.messaging.correlation.CorrelationContext;
import org.springframework.amqp.rabbit.config.ContainerCustomizer;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Guide §9 — correlation ids cross the broker. The outbox writes the id into the envelope and the AMQP
 * {@code correlationId} property; every consumer restores it into {@link CorrelationContext} (and the log
 * MDC) before the listener runs, so a request and every downstream message share one id in the logs.
 * Uses {@code addAfterReceivePostProcessors} so the listener retry advice configured by Boot is untouched.
 */
@Configuration
public class MessagingCorrelationConfig {

    @Bean
    public ContainerCustomizer<SimpleMessageListenerContainer> correlationIdContainerCustomizer() {
        return container -> container.addAfterReceivePostProcessors(message -> {
            String incoming = message.getMessageProperties() == null ? null : message.getMessageProperties().getCorrelationId();
            CorrelationContext.set(CorrelationIds.sanitizeOrCreate(incoming));
            return message;
        });
    }
}
