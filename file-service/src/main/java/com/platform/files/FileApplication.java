package com.platform.files;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {"com.platform.files", "com.platform.messaging"})
@EntityScan(basePackages = {"com.platform.files", "com.platform.messaging.outbox"})
@EnableJpaRepositories(basePackages = {"com.platform.files", "com.platform.messaging.outbox"})
@EnableScheduling
public class FileApplication {

    public static void main(String[] args) {
        SpringApplication.run(FileApplication.class, args);
    }
}
