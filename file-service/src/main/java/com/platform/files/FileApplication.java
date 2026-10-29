package com.platform.files;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

/**
 * File service — multipart upload, metadata storage, and streaming download.
 *
 * <p>DataSource auto-config is excluded because we supply a tenant-routing DataSource
 * (database-per-tenant {@code files_*}, same pattern as HRMS/payroll).
 */
@SpringBootApplication(exclude = DataSourceAutoConfiguration.class)
public class FileApplication {

    public static void main(String[] args) {
        SpringApplication.run(FileApplication.class, args);
    }
}
