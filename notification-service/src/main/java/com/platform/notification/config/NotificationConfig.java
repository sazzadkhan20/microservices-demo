package com.platform.notification.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestClient;


@Configuration
public class NotificationConfig {

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper().findAndRegisterModules();
    }

    /** Guide §7 deadlines: connect/read timeouts on every S2S client; prototype so clients never share a builder. */
    @Bean
    @org.springframework.context.annotation.Scope("prototype")
    public RestClient.Builder restClientBuilder() {
        return com.platform.common.http.ResilientHttp.restClientBuilder();
    }
}
