package com.platform.report.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.report.infrastructure.multitenancy.TenantDataSourceManager;
import com.platform.report.infrastructure.multitenancy.TenantRoutingDataSource;
import com.platform.report.infrastructure.redis.RoutingInvalidateListener;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.web.client.RestClient;

import javax.sql.DataSource;

@Configuration
@EnableConfigurationProperties(ReportProperties.class)
public class ReportConfig {

    @Bean
    @Primary
    public DataSource dataSource(TenantDataSourceManager manager) {
        return new TenantRoutingDataSource(manager);
    }

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory factory) {
        return new StringRedisTemplate(factory);
    }

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper().findAndRegisterModules();
    }

    @Bean
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory factory,
            RoutingInvalidateListener listener) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(factory);
        container.addMessageListener(listener, new ChannelTopic(listener.channel()));
        return container;
    }
}
