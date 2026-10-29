package com.platform.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Notification service — stores and delivers in-app notifications per tenant.
 *
 * <p>Layers:
 * <ul>
 *   <li>{@code api}            — REST adapters + DTOs</li>
 *   <li>{@code application}    — use cases</li>
 *   <li>{@code domain}         — JPA entities</li>
 *   <li>{@code infrastructure} — persistence + tenant filter</li>
 *   <li>{@code config}         — OpenAPI + properties</li>
 * </ul>
 *
 * <p>Uses H2 file-mode DB by default; override {@code SPRING_DATASOURCE_URL} for PostgreSQL.
 */
@SpringBootApplication
public class NotificationApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationApplication.class, args);
    }
}
