package com.platform.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {"com.platform.notification", "com.platform.messaging"})
@EntityScan(basePackages = {"com.platform.notification", "com.platform.messaging.outbox"})
@EnableJpaRepositories(basePackages = {"com.platform.notification", "com.platform.messaging.outbox"})
@EnableScheduling
public class NotificationApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationApplication.class, args);
    }
}
