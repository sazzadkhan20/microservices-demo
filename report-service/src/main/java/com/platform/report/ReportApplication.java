package com.platform.report;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Report service — generates downloadable reports for tenant data.
 *
 * <p>Layers:
 * <ul>
 *   <li>{@code api}            — REST adapters + DTOs</li>
 *   <li>{@code application}    — report generation use cases</li>
 *   <li>{@code infrastructure} — tenant filter</li>
 *   <li>{@code config}         — OpenAPI + properties</li>
 * </ul>
 *
 * <p>v1: Attendance monthly report as CSV.
 * Set {@code HRMS_BASE_URL} to pull live attendance data from hrms-service;
 * otherwise a placeholder CSV with correct headers is returned.
 */
@SpringBootApplication
public class ReportApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReportApplication.class, args);
    }
}
