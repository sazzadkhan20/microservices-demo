package com.platform.files.infrastructure.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.constants.RedisKeys;
import com.platform.common.redis.InvalidateEvent;
import com.platform.files.config.FileProperties;
import com.platform.files.infrastructure.multitenancy.TenantDataSourceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

/**
 * Subscribes to Redis pub/sub and busts Caffeine L1 when Registry publishes changes.
 * This is the production-preferred path (faster than polling).
 */
@Component
public class RoutingInvalidateListener implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(RoutingInvalidateListener.class);

    private final ObjectMapper objectMapper;
    private final TenantDataSourceManager dataSourceManager;
    private final FileProperties properties;

    public RoutingInvalidateListener(
            ObjectMapper objectMapper,
            TenantDataSourceManager dataSourceManager,
            FileProperties properties) {
        this.objectMapper = objectMapper;
        this.dataSourceManager = dataSourceManager;
        this.properties = properties;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            InvalidateEvent event = objectMapper.readValue(message.getBody(), InvalidateEvent.class);
            if (!properties.getServiceName().equals(event.service())) {
                return; // event for file (or other) — ignore
            }
            if (event.tenantId() == null || event.tenantId().isBlank()) {
                dataSourceManager.invalidateAll();
            } else {
                dataSourceManager.invalidate(event.tenantId());
            }
            log.info("Handled invalidate event for tenant {} v{}", event.tenantId(), event.version());
        } catch (Exception e) {
            log.error("Failed to handle invalidate event: {}", e.getMessage());
        }
    }

    public String channel() {
        return RedisKeys.INVALIDATE_CHANNEL;
    }
}
