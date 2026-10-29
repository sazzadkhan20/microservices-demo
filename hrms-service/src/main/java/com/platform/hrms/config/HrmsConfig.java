package com.platform.hrms.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.platform.hrms.infrastructure.multitenancy.TenantDataSourceManager;
import com.platform.hrms.infrastructure.multitenancy.TenantRoutingDataSource;
import com.platform.hrms.infrastructure.redis.RoutingInvalidateListener;
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
@EnableConfigurationProperties(HrmsProperties.class)
public class HrmsConfig {

    @Bean
    @Primary
    public DataSource dataSource(TenantDataSourceManager manager) {
        // Routing DS: every JDBC call uses TenantContext → correct tenant DB
        return new TenantRoutingDataSource(manager);
    }

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory factory) {
        return new StringRedisTemplate(factory);
    }

    /**
     * ISO-8601 dates for FE (not Jackson default LocalDate arrays like [2026,4,14]).
     * Must stay primary so MVC + Redis JSON share the same contract.
     */
    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.findAndRegisterModules();
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
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
