package com.platform.report;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

/**
 * Report service — generates downloadable reports and archives export metadata.
 *
 * <p>DataSource auto-config is excluded because we supply a tenant-routing DataSource
 * (database-per-tenant {@code reports_*}, same pattern as HRMS/payroll).
 *
 * <p>v1: Attendance monthly report as CSV (live data via {@code HRMS_BASE_URL}).
 */
@SpringBootApplication(exclude = DataSourceAutoConfiguration.class)
public class ReportApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReportApplication.class, args);
    }
}
