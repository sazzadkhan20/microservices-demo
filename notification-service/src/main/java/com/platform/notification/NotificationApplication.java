package com.platform.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

/**
 * Notification service — stores and delivers in-app notifications per tenant.
 *
 * <p>DataSource auto-config is excluded because we supply a tenant-routing DataSource
 * (database-per-tenant {@code notif_*}, same pattern as HRMS/payroll).
 */
@SpringBootApplication(exclude = DataSourceAutoConfiguration.class)
public class NotificationApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationApplication.class, args);
    }
}
