package com.platform.registry;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Tenant Registry — control plane.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Provision a new business tenant (CREATE DATABASE + Liquibase + Redis routing)</li>
 *   <li>Store routing metadata in {@code registry_db}</li>
 *   <li>Publish cache invalidation events</li>
 *   <li>Issue practice JWTs for Postman/Swagger testing</li>
 * </ul>
 *
 * <p>Not on the hot request path for HRMS/Payroll once caches are warm.
 */
@SpringBootApplication
public class RegistryApplication {

    public static void main(String[] args) {
        SpringApplication.run(RegistryApplication.class, args);
    }
}
