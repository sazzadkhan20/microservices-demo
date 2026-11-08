package com.platform.messaging.config;

import com.platform.common.messaging.PlatformQueues;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "platform.messaging", name = "enabled", havingValue = "true", matchIfMissing = true)
public class PlatformRabbitConfig {

    private static final Logger log = LoggerFactory.getLogger(PlatformRabbitConfig.class);

    @Bean
    public MessageConverter jacksonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory cf, MessageConverter converter) {
        if (cf instanceof CachingConnectionFactory caching) {
            // Correlated confirms required for OutboxRelay.awaitBrokerConfirm.
            caching.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.CORRELATED);
            caching.setPublisherReturns(true);
        }
        RabbitTemplate template = new RabbitTemplate(cf);
        template.setMessageConverter(converter);
        template.setMandatory(true);
        template.setConfirmCallback((CorrelationData correlation, boolean ack, String cause) -> {
            if (!ack) {
                log.warn("Broker nack correlationId={} cause={}",
                        correlation == null ? null : correlation.getId(), cause);
            }
        });
        template.setReturnsCallback(returned -> log.warn(
                "Unroutable message exchange={} rk={} reply={}:{}",
                returned.getExchange(),
                returned.getRoutingKey(),
                returned.getReplyCode(),
                returned.getReplyText()));
        return template;
    }

    @Bean
    public DirectExchange platformExchange() {
        return new DirectExchange(PlatformQueues.EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange platformDlx() {
        return new DirectExchange(PlatformQueues.DLX, true, false);
    }

    @Bean
    public Queue payrollLeaveCoverQueue() {
        return durableWithDlq(PlatformQueues.PAYROLL_LEAVE_COVER_Q, PlatformQueues.PAYROLL_LEAVE_COVER_DLQ);
    }

    @Bean
    public Queue payrollLeaveCoverDlq() {
        return QueueBuilder.durable(PlatformQueues.PAYROLL_LEAVE_COVER_DLQ).build();
    }

    @Bean
    public Queue leaveCoverResultQueue() {
        return durableWithDlq(PlatformQueues.LEAVE_COVER_RESULT_Q, PlatformQueues.LEAVE_COVER_RESULT_DLQ);
    }

    @Bean
    public Queue leaveCoverResultDlq() {
        return QueueBuilder.durable(PlatformQueues.LEAVE_COVER_RESULT_DLQ).build();
    }

    @Bean
    public Queue notificationCreateQueue() {
        return durableWithDlq(PlatformQueues.NOTIFICATION_CREATE_Q, PlatformQueues.NOTIFICATION_CREATE_DLQ);
    }

    @Bean
    public Queue notificationCreateDlq() {
        return QueueBuilder.durable(PlatformQueues.NOTIFICATION_CREATE_DLQ).build();
    }

    @Bean
    public Queue fileScanQueue() {
        return durableWithDlq(PlatformQueues.FILE_SCAN_Q, PlatformQueues.FILE_SCAN_DLQ);
    }

    @Bean
    public Queue fileScanDlq() {
        return QueueBuilder.durable(PlatformQueues.FILE_SCAN_DLQ).build();
    }

    @Bean
    public Queue reportExportQueue() {
        return durableWithDlq(PlatformQueues.REPORT_EXPORT_Q, PlatformQueues.REPORT_EXPORT_DLQ);
    }

    @Bean
    public Queue reportExportDlq() {
        return QueueBuilder.durable(PlatformQueues.REPORT_EXPORT_DLQ).build();
    }

    @Bean
    public Binding bindLeaveCoverCmd(Queue payrollLeaveCoverQueue, DirectExchange platformExchange) {
        return BindingBuilder.bind(payrollLeaveCoverQueue).to(platformExchange)
                .with(PlatformQueues.PAYROLL_LEAVE_COVER_CMD);
    }

    @Bean
    public Binding bindLeaveCoverDlq(Queue payrollLeaveCoverDlq, DirectExchange platformDlx) {
        return BindingBuilder.bind(payrollLeaveCoverDlq).to(platformDlx)
                .with(PlatformQueues.PAYROLL_LEAVE_COVER_Q);
    }

    @Bean
    public Binding bindLeaveResult(Queue leaveCoverResultQueue, DirectExchange platformExchange) {
        return BindingBuilder.bind(leaveCoverResultQueue).to(platformExchange)
                .with(PlatformQueues.LEAVE_COVER_RESULT);
    }

    @Bean
    public Binding bindLeaveResultDlq(Queue leaveCoverResultDlq, DirectExchange platformDlx) {
        return BindingBuilder.bind(leaveCoverResultDlq).to(platformDlx)
                .with(PlatformQueues.LEAVE_COVER_RESULT_Q);
    }

    @Bean
    public Binding bindNotification(Queue notificationCreateQueue, DirectExchange platformExchange) {
        return BindingBuilder.bind(notificationCreateQueue).to(platformExchange)
                .with(PlatformQueues.NOTIFICATION_CREATE);
    }

    @Bean
    public Binding bindNotificationDlq(Queue notificationCreateDlq, DirectExchange platformDlx) {
        return BindingBuilder.bind(notificationCreateDlq).to(platformDlx)
                .with(PlatformQueues.NOTIFICATION_CREATE_Q);
    }

    @Bean
    public Binding bindFileScan(Queue fileScanQueue, DirectExchange platformExchange) {
        return BindingBuilder.bind(fileScanQueue).to(platformExchange)
                .with(PlatformQueues.FILE_SCAN);
    }

    @Bean
    public Binding bindFileScanDlq(Queue fileScanDlq, DirectExchange platformDlx) {
        return BindingBuilder.bind(fileScanDlq).to(platformDlx)
                .with(PlatformQueues.FILE_SCAN_Q);
    }

    @Bean
    public Binding bindReportExport(Queue reportExportQueue, DirectExchange platformExchange) {
        return BindingBuilder.bind(reportExportQueue).to(platformExchange)
                .with(PlatformQueues.REPORT_EXPORT);
    }

    @Bean
    public Binding bindReportExportDlq(Queue reportExportDlq, DirectExchange platformDlx) {
        return BindingBuilder.bind(reportExportDlq).to(platformDlx)
                .with(PlatformQueues.REPORT_EXPORT_Q);
    }

    @Bean
    public Queue peopleEmployeeUpsertedLeaveQueue() {
        return durableWithDlq(
                PlatformQueues.PEOPLE_EMPLOYEE_UPSERTED_LEAVE_Q,
                PlatformQueues.PEOPLE_EMPLOYEE_UPSERTED_LEAVE_DLQ);
    }

    @Bean
    public Queue peopleEmployeeUpsertedLeaveDlq() {
        return QueueBuilder.durable(PlatformQueues.PEOPLE_EMPLOYEE_UPSERTED_LEAVE_DLQ).build();
    }

    @Bean
    public Queue peopleEmployeeUpsertedTimeworkQueue() {
        return durableWithDlq(
                PlatformQueues.PEOPLE_EMPLOYEE_UPSERTED_TIMEWORK_Q,
                PlatformQueues.PEOPLE_EMPLOYEE_UPSERTED_TIMEWORK_DLQ);
    }

    @Bean
    public Queue peopleEmployeeUpsertedTimeworkDlq() {
        return QueueBuilder.durable(PlatformQueues.PEOPLE_EMPLOYEE_UPSERTED_TIMEWORK_DLQ).build();
    }

    @Bean
    public Binding bindPeopleEmployeeLeave(
            Queue peopleEmployeeUpsertedLeaveQueue, DirectExchange platformExchange) {
        return BindingBuilder.bind(peopleEmployeeUpsertedLeaveQueue).to(platformExchange)
                .with(PlatformQueues.PEOPLE_EMPLOYEE_UPSERTED);
    }

    @Bean
    public Binding bindPeopleEmployeeLeaveDlq(
            Queue peopleEmployeeUpsertedLeaveDlq, DirectExchange platformDlx) {
        return BindingBuilder.bind(peopleEmployeeUpsertedLeaveDlq).to(platformDlx)
                .with(PlatformQueues.PEOPLE_EMPLOYEE_UPSERTED_LEAVE_Q);
    }

    @Bean
    public Binding bindPeopleEmployeeTimework(
            Queue peopleEmployeeUpsertedTimeworkQueue, DirectExchange platformExchange) {
        return BindingBuilder.bind(peopleEmployeeUpsertedTimeworkQueue).to(platformExchange)
                .with(PlatformQueues.PEOPLE_EMPLOYEE_UPSERTED);
    }

    @Bean
    public Binding bindPeopleEmployeeTimeworkDlq(
            Queue peopleEmployeeUpsertedTimeworkDlq, DirectExchange platformDlx) {
        return BindingBuilder.bind(peopleEmployeeUpsertedTimeworkDlq).to(platformDlx)
                .with(PlatformQueues.PEOPLE_EMPLOYEE_UPSERTED_TIMEWORK_Q);
    }

    private static Queue durableWithDlq(String name, String dlq) {
        return QueueBuilder.durable(name)
                .withArgument("x-dead-letter-exchange", PlatformQueues.DLX)
                .withArgument("x-dead-letter-routing-key", name)
                .build();
    }
}
