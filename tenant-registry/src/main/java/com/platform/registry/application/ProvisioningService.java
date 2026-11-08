package com.platform.registry.application;

import com.platform.common.tenant.TenantIds;
import com.platform.registry.api.dto.ProvisionRequest;
import com.platform.registry.api.dto.ProvisionResponse;
import com.platform.registry.api.dto.TenantRoutingView;
import com.platform.registry.config.PlatformProperties;
import com.platform.registry.domain.TenantRouting;
import com.platform.registry.domain.TenantRoutingStatuses;
import com.platform.registry.infrastructure.persistence.TenantRoutingRepository;
import com.platform.registry.infrastructure.provision.DatabaseCloner;
import com.platform.registry.infrastructure.provision.ImpitBusinessSeeder;
import com.platform.registry.infrastructure.provision.MinioBucketProvisioner;
import com.platform.registry.infrastructure.provision.PostgresDatabaseProvisioner;
import com.platform.registry.infrastructure.provision.PracticeBusinessSeeder;
import com.platform.registry.infrastructure.provision.QiiBusinessSeeder;
import com.platform.registry.infrastructure.provision.AuditDataPartitioner;
import com.platform.registry.infrastructure.provision.SettingsDistributor;
import com.platform.registry.infrastructure.provision.TenantDataMigrator;
import com.platform.registry.infrastructure.redis.RoutingCachePublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class ProvisioningService {

    private static final Logger log = LoggerFactory.getLogger(ProvisioningService.class);

    /** @deprecated use {@link #SERVICE_PEOPLE} — kept for legacy registry rows / fleet cutover */
    @Deprecated
    public static final String SERVICE_HRMS = "hrms";
    public static final String SERVICE_PEOPLE = "people";
    public static final String SERVICE_LEAVE = "leave";
    public static final String SERVICE_TIME_WORK = "time-work";
    public static final String SERVICE_PAYROLL = "payroll";
    public static final String SERVICE_FILE = "file";
    public static final String SERVICE_NOTIFICATION = "notification";
    public static final String SERVICE_REPORT = "report";

    private final TenantRoutingRepository repository;
    private final PostgresDatabaseProvisioner provisioner;
    private final DatabaseCloner databaseCloner;
    private final SettingsDistributor settingsDistributor;
    private final AuditDataPartitioner auditDataPartitioner;
    private final PracticeBusinessSeeder practiceBusinessSeeder;
    private final ImpitBusinessSeeder impitBusinessSeeder;
    private final QiiBusinessSeeder qiiBusinessSeeder;
    private final RoutingCachePublisher cachePublisher;
    private final PlatformProperties properties;
    private final MinioBucketProvisioner minioBucketProvisioner;

    public ProvisioningService(
            TenantRoutingRepository repository,
            PostgresDatabaseProvisioner provisioner,
            DatabaseCloner databaseCloner,
            SettingsDistributor settingsDistributor,
            AuditDataPartitioner auditDataPartitioner,
            PracticeBusinessSeeder practiceBusinessSeeder,
            ImpitBusinessSeeder impitBusinessSeeder,
            QiiBusinessSeeder qiiBusinessSeeder,
            RoutingCachePublisher cachePublisher,
            PlatformProperties properties,
            MinioBucketProvisioner minioBucketProvisioner) {
        this.repository = repository;
        this.provisioner = provisioner;
        this.databaseCloner = databaseCloner;
        this.settingsDistributor = settingsDistributor;
        this.auditDataPartitioner = auditDataPartitioner;
        this.practiceBusinessSeeder = practiceBusinessSeeder;
        this.impitBusinessSeeder = impitBusinessSeeder;
        this.qiiBusinessSeeder = qiiBusinessSeeder;
        this.cachePublisher = cachePublisher;
        this.properties = properties;
        this.minioBucketProvisioner = minioBucketProvisioner;
    }

    @Transactional
    public ProvisionResponse provision(ProvisionRequest request) {
        String tenantId = TenantIds.normalize(request.tenantId());

        if (repository.existsByTenantId(tenantId)) {
            throw new IllegalStateException("Tenant already provisioned: " + tenantId);
        }

        long version = System.currentTimeMillis();
        Instant now = Instant.now();

        String peopleDb = TenantIds.peopleDbName(tenantId);
        String leaveDb = TenantIds.leaveDbName(tenantId);
        String timeWorkDb = TenantIds.timeWorkDbName(tenantId);
        String payrollDb = TenantIds.payrollDbName(tenantId);
        String filesDb = TenantIds.filesDbName(tenantId);
        String notifDb = TenantIds.notificationDbName(tenantId);
        String reportsDb = TenantIds.reportsDbName(tenantId);

        // Guide §3 — People / Leave / Time&Work each own a tenant database (no shared hrms_*).
        String peopleJdbc = provisioner.createAndMigrate(peopleDb, properties.getProvision().getPeople());
        String leaveJdbc = provisioner.createAndMigrate(leaveDb, properties.getProvision().getLeave());
        String timeWorkJdbc = provisioner.createAndMigrate(timeWorkDb, properties.getProvision().getTimeWork());
        String payrollJdbc = provisioner.createAndMigrate(payrollDb, properties.getProvision().getPayroll());
        String filesJdbc = provisioner.createAndMigrate(filesDb, properties.getProvision().getFiles());
        String notifJdbc = provisioner.createAndMigrate(notifDb, properties.getProvision().getNotification());
        String reportsJdbc = provisioner.createAndMigrate(reportsDb, properties.getProvision().getReport());
        minioBucketProvisioner.ensureTenantBucket(tenantId);

        String pack = request.resolvedBusinessPack();
        // Fresh People migrate still has Leave/Time&Work shells for pack seeders; reseed restores them if purged.
        ensurePeopleBootstrapShells(peopleJdbc, leaveJdbc, timeWorkJdbc);
        String message = seedBusinessPack(pack, peopleJdbc, payrollJdbc);
        distributeDomainData(peopleJdbc, leaveJdbc, timeWorkJdbc);
        distributeSettingsOwnership(peopleJdbc, leaveJdbc, timeWorkJdbc);
        partitionAuditOwnership(peopleJdbc, leaveJdbc, timeWorkJdbc, payrollJdbc, filesJdbc, notifJdbc, reportsJdbc);
        purgeForeignTablesFromPeople(peopleJdbc);

        TenantRouting peopleRouting = saveRouting(SERVICE_PEOPLE, tenantId, peopleJdbc,
                properties.getProvision().getPeople(), version, now);
        TenantRouting leaveRouting = saveRouting(SERVICE_LEAVE, tenantId, leaveJdbc,
                properties.getProvision().getLeave(), version, now);
        TenantRouting timeWorkRouting = saveRouting(SERVICE_TIME_WORK, tenantId, timeWorkJdbc,
                properties.getProvision().getTimeWork(), version, now);
        // Legacy alias so older clients resolving "hrms" still hit the People DB.
        TenantRouting hrmsAlias = saveRouting(SERVICE_HRMS, tenantId, peopleJdbc,
                properties.getProvision().getPeople(), version, now);
        TenantRouting payrollRouting = saveRouting(SERVICE_PAYROLL, tenantId, payrollJdbc,
                properties.getProvision().getPayroll(), version, now);
        TenantRouting fileRouting = saveRouting(SERVICE_FILE, tenantId, filesJdbc,
                properties.getProvision().getFiles(), version, now);
        TenantRouting notifRouting = saveRouting(SERVICE_NOTIFICATION, tenantId, notifJdbc,
                properties.getProvision().getNotification(), version, now);
        TenantRouting reportRouting = saveRouting(SERVICE_REPORT, tenantId, reportsJdbc,
                properties.getProvision().getReport(), version, now);

        List<TenantRouting> routings = List.of(
                peopleRouting, leaveRouting, timeWorkRouting, hrmsAlias,
                payrollRouting, fileRouting, notifRouting, reportRouting);
        for (TenantRouting row : routings) {
            cachePublisher.publishRouting(row);
        }

        log.info("Provisioned tenant {} → {}, {}, {}, {}, {}, {}, {} + MinIO (pack={})",
                tenantId, peopleDb, leaveDb, timeWorkDb, payrollDb, filesDb, notifDb, reportsDb, pack);

        List<TenantRoutingView> views = new ArrayList<>();
        for (TenantRouting row : routings) {
            views.add(TenantRoutingView.from(row));
        }
        return new ProvisionResponse(tenantId, views, message);
    }

    /**
     * Restore empty Leave/Time&amp;Work table shells on People for pack seeders (guide §3 bootstrap).
     */
    private void ensurePeopleBootstrapShells(String peopleJdbc, String leaveJdbc, String timeWorkJdbc) {
        PlatformProperties.DbTarget people = properties.getProvision().getPeople();
        String user = people.getUsername();
        String pass = people.getPassword();
        databaseCloner.ensureEmptyTablesExist(leaveJdbc, peopleJdbc, user, pass, DatabaseCloner.LEAVE_SEED_TABLES);
        databaseCloner.ensureEmptyTablesExist(timeWorkJdbc, peopleJdbc, user, pass, DatabaseCloner.TIMEWORK_SEED_TABLES);
    }

    /**
     * Copy each domain's owned tables into the correct service DB.
     * Runtime: services never open another service's JDBC — only their registry routing.
     */
    private void distributeDomainData(String peopleJdbc, String leaveJdbc, String timeWorkJdbc) {
        PlatformProperties.DbTarget people = properties.getProvision().getPeople();
        String user = people.getUsername();
        String pass = people.getPassword();
        databaseCloner.cloneTables(peopleJdbc, leaveJdbc, user, pass, DatabaseCloner.LEAVE_SEED_TABLES);
        databaseCloner.cloneTables(peopleJdbc, timeWorkJdbc, user, pass, DatabaseCloner.TIMEWORK_SEED_TABLES);
    }

    /** Split monolith settings JSON into People / Leave / Time&amp;Work owned tables. */
    private void distributeSettingsOwnership(String peopleJdbc, String leaveJdbc, String timeWorkJdbc) {
        PlatformProperties.DbTarget people = properties.getProvision().getPeople();
        settingsDistributor.distributeOwnedSlices(
                peopleJdbc, leaveJdbc, timeWorkJdbc, people.getUsername(), people.getPassword());
    }

    /** Split cloned audit rows into each service's owned *_audit_log table. */
    private void partitionAuditOwnership(
            String peopleJdbc,
            String leaveJdbc,
            String timeWorkJdbc,
            String payrollJdbc,
            String filesJdbc,
            String notifJdbc,
            String reportsJdbc) {
        PlatformProperties.DbTarget people = properties.getProvision().getPeople();
        auditDataPartitioner.partitionFromPeople(
                peopleJdbc, leaveJdbc, timeWorkJdbc, payrollJdbc, filesJdbc, notifJdbc, reportsJdbc,
                people.getUsername(), people.getPassword());
    }

    private void purgeForeignTablesFromPeople(String peopleJdbc) {
        PlatformProperties.DbTarget people = properties.getProvision().getPeople();
        try (java.sql.Connection conn = java.sql.DriverManager.getConnection(
                peopleJdbc, people.getUsername(), people.getPassword());
             java.sql.Statement st = conn.createStatement()) {
            for (String sql : DatabaseCloner.PEOPLE_PURGE_FOREIGN_SQL) {
                try {
                    st.execute(sql);
                } catch (Exception tableMissing) {
                    log.debug("Purge skip {}: {}", sql, tableMissing.getMessage());
                }
            }
            log.info("Cleared Leave/Time&Work SoT rows from People DB {}", peopleJdbc);
        } catch (Exception e) {
            throw new IllegalStateException("Failed purging foreign tables from People DB: " + e.getMessage(), e);
        }
    }

    /** @deprecated use {@link #distributeDomainData} */
    @Deprecated
    private void replicatePeopleToSatellites(String peopleJdbc, String leaveJdbc, String timeWorkJdbc) {
        distributeDomainData(peopleJdbc, leaveJdbc, timeWorkJdbc);
    }

    @Transactional(readOnly = true)
    public List<TenantRoutingView> listAll() {
        return repository.findAllByOrderByTenantIdAscServiceNameAsc().stream()
                .map(TenantRoutingView::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public TenantRoutingView getRouting(String service, String tenantId) {
        String normalized = TenantIds.normalize(tenantId);
        return repository.findByServiceNameAndTenantId(service, normalized)
                .map(TenantRoutingView::from)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No routing for service=" + service + " tenant=" + normalized));
    }

    @Transactional
    public Map<String, Object> upgradeHrmsSchema(String tenantId, boolean reseed, String businessPack) {
        String normalized = TenantIds.normalize(tenantId);
        Map<String, Object> workforce = ensureWorkforceServiceRouting(normalized);
        TenantRouting people = requirePeopleRouting(normalized);

        provisioner.migrateExisting(people.getJdbcUrl(), properties.getProvision().getPeople());
        markRouting(people, TenantRoutingStatuses.ACTIVE);

        String pack = businessPack;
        if (reseed) {
            if (pack == null || pack.isBlank()) {
                throw new IllegalArgumentException(
                        "businessPack is required when reseed=true (use techshop_retail, impit_limited, or qii_creative)");
            }
            TenantRouting leave = repository.findByServiceNameAndTenantId(SERVICE_LEAVE, normalized).orElseThrow();
            TenantRouting timeWork = repository.findByServiceNameAndTenantId(SERVICE_TIME_WORK, normalized).orElseThrow();
            ensurePeopleBootstrapShells(people.getJdbcUrl(), leave.getJdbcUrl(), timeWork.getJdbcUrl());
            seedBusinessPack(pack, people.getJdbcUrl(), null);
            distributeDomainData(people.getJdbcUrl(), leave.getJdbcUrl(), timeWork.getJdbcUrl());
            distributeSettingsOwnership(people.getJdbcUrl(), leave.getJdbcUrl(), timeWork.getJdbcUrl());
            purgeForeignTablesFromPeople(people.getJdbcUrl());
        }
        ensureSatelliteServiceRouting(normalized);
        workforce.put("tenantId", normalized);
        workforce.put("jdbcUrl", people.getJdbcUrl());
        workforce.put("migrated", true);
        workforce.put("reseed", reseed);
        workforce.put("businessPack", pack == null ? "" : pack);
        workforce.put("message", "People/Leave/Time&Work schemas upgraded; optional business pack reseed + clone applied.");
        return workforce;
    }

    private TenantRouting requirePeopleRouting(String tenantId) {
        return repository.findByServiceNameAndTenantId(SERVICE_PEOPLE, tenantId)
                .or(() -> repository.findByServiceNameAndTenantId(SERVICE_HRMS, tenantId))
                .orElseThrow(() -> new IllegalArgumentException(
                        "People (or legacy hrms) routing not found for " + tenantId));
    }

    /**
     * Re-applies config-driven system role permission matrix without wiping tenant data.
     * Pack {@code qii_creative} (default when tenant looks like Qii) updates SUPERADMIN…EMPLOYEE links.
     */
    @Transactional
    public Map<String, Object> upgradeRbacMatrix(String tenantId, String businessPack) {
        String normalized = TenantIds.normalize(tenantId);
        ensureWorkforceServiceRouting(normalized);
        TenantRouting people = requirePeopleRouting(normalized);
        String pack = (businessPack == null || businessPack.isBlank()) ? "qii_creative" : businessPack.trim();
        Map<String, Object> result = switch (pack) {
            case "qii_creative", "qii" -> {
                qiiBusinessSeeder.reseedRbacMatrix(people.getJdbcUrl(), properties.getProvision().getPeople());
                cachePublisher.publishRouting(people);
                yield new java.util.LinkedHashMap<>(Map.of(
                        "tenantId", normalized,
                        "businessPack", pack,
                        "message", "System RBAC matrix re-applied (SUPERADMIN/ADMIN/HR/MANAGER/EMPLOYEE)."
                ));
            }
            case "impit_limited", "impit" -> {
                impitBusinessSeeder.reseedRbacMatrix(people.getJdbcUrl(), properties.getProvision().getPeople());
                cachePublisher.publishRouting(people);
                yield new java.util.LinkedHashMap<>(Map.of(
                        "tenantId", normalized,
                        "businessPack", pack,
                        "message", "Impit RBAC matrix re-applied (HR/SUPERADMIN/MANAGER/ADMIN/EMPLOYEE bindings)."
                ));
            }
            case "techshop_retail", "techshop", "retail" -> {
                try (java.sql.Connection conn = java.sql.DriverManager.getConnection(
                        people.getJdbcUrl(),
                        properties.getProvision().getPeople().getUsername(),
                        properties.getProvision().getPeople().getPassword());
                     java.sql.Statement st = conn.createStatement()) {
                    PracticeBusinessSeeder.ensureRetailRbacRoles(st);
                } catch (Exception e) {
                    throw new IllegalStateException("TechShop RBAC repair failed: " + e.getMessage(), e);
                }
                cachePublisher.publishRouting(people);
                yield new java.util.LinkedHashMap<>(Map.of(
                        "tenantId", normalized,
                        "businessPack", pack,
                        "message", "TechShop system RBAC roles/permissions ensured (shared product matrix)."
                ));
            }
            default -> throw new IllegalArgumentException(
                    "RBAC matrix upgrade supported for qii_creative, impit_limited, techshop_retail (got businessPack="
                            + pack + ")");
        };
        // Sync RBAC/employee projections into Leave + Time&Work (not full DB clone).
        repository.findByServiceNameAndTenantId(SERVICE_LEAVE, normalized).ifPresent(leave ->
                repository.findByServiceNameAndTenantId(SERVICE_TIME_WORK, normalized).ifPresent(tw -> {
                    PlatformProperties.DbTarget p = properties.getProvision().getPeople();
                    databaseCloner.cloneTables(
                            people.getJdbcUrl(), leave.getJdbcUrl(), p.getUsername(), p.getPassword(),
                            List.of("employee", "department", "designation", "location", "team",
                                    "tenant_settings", "app_role", "app_permission",
                                    "app_role_permission", "app_user_role"));
                    databaseCloner.cloneTables(
                            people.getJdbcUrl(), tw.getJdbcUrl(), p.getUsername(), p.getPassword(),
                            List.of("employee", "tenant_settings", "app_role", "app_permission",
                                    "app_role_permission", "app_user_role"));
                }));
        return result;
    }

    @Transactional
    public Map<String, Object> upgradePayrollSchema(String tenantId) {
        String normalized = TenantIds.normalize(tenantId);
        TenantRouting payroll = repository.findByServiceNameAndTenantId(SERVICE_PAYROLL, normalized)
                .orElseThrow(() -> new IllegalArgumentException("Payroll routing not found for " + normalized));
        provisioner.migrateExisting(payroll.getJdbcUrl(), properties.getProvision().getPayroll());
        markRouting(payroll, TenantRoutingStatuses.ACTIVE);
        return Map.of(
                "tenantId", normalized,
                "jdbcUrl", payroll.getJdbcUrl(),
                "migrated", true,
                "message", "Payroll schema upgraded (salary_history and config tables)."
        );
    }

    /**
     * Creates/migrates per-tenant files/notif/reports DBs, copies rows from legacy shared
     * {@code *_platform} DBs when present, and rewrites registry routing.
     */
    @Transactional
    public Map<String, Object> upgradeSatelliteSchemas(String tenantId) {
        String normalized = TenantIds.normalize(tenantId);
        Map<String, Object> result = ensureSatelliteServiceRouting(normalized);
        result.put("tenantId", normalized);
        result.put("message", "Satellite services upgraded to database-per-tenant (files/notif/reports).");
        return result;
    }

    /**
     * Fleet migrate every service for every tenant (startup default).
     */
    public Map<String, Object> migrateAllTenants() {
        return migrateAllTenants(null);
    }

    /**
     * Fleet migrate: apply shared Liquibase changelogs to tenant DBs (no reseed).
     *
     * @param serviceFilter when non-blank (e.g. {@code hrms}), only that service's
     *                      tenant DBs are migrated — other services are untouched.
     *                      When blank/null, all services migrate (startup / full fleet).
     */
    public Map<String, Object> migrateAllTenants(String serviceFilter) {
        String filter = (serviceFilter == null || serviceFilter.isBlank())
                ? null
                : serviceFilter.trim().toLowerCase();
        if (filter != null && resolveDbTarget(filter) == null) {
            throw new IllegalArgumentException(
                    "Unknown service filter=" + filter
                            + " (use people, leave, time-work, hrms, payroll, file, notification, report)");
        }

        java.util.List<Map<String, Object>> results = new java.util.ArrayList<>();
        int ok = 0;
        int failed = 0;
        int skipped = 0;
        java.util.Set<String> tenants = new java.util.LinkedHashSet<>();

        for (TenantRouting row : repository.findAllByOrderByTenantIdAscServiceNameAsc()) {
            tenants.add(row.getTenantId());
        }

        // Full fleet (or workforce services): ensure People/Leave/Time&Work DBs exist for old tenants.
        boolean touchWorkforce = filter == null
                || SERVICE_PEOPLE.equals(filter)
                || SERVICE_LEAVE.equals(filter)
                || SERVICE_TIME_WORK.equals(filter)
                || SERVICE_HRMS.equals(filter);
        if (touchWorkforce) {
            for (String tenantId : tenants) {
                try {
                    ensureWorkforceServiceRouting(tenantId);
                } catch (Exception e) {
                    log.warn("Fleet workforce ensure failed for tenant={}: {}", tenantId, e.getMessage());
                }
            }
        }

        // Full fleet (or satellite services): ensure old tenants get files/notif/reports DBs.
        boolean touchSatellites = filter == null
                || SERVICE_FILE.equals(filter)
                || SERVICE_NOTIFICATION.equals(filter)
                || SERVICE_REPORT.equals(filter);
        if (touchSatellites) {
            for (String tenantId : tenants) {
                try {
                    ensureSatelliteServiceRouting(tenantId);
                } catch (Exception e) {
                    log.warn("Fleet satellite ensure failed for tenant={}: {}", tenantId, e.getMessage());
                }
            }
        }

        for (TenantRouting row : repository.findAllByOrderByTenantIdAscServiceNameAsc()) {
            if (filter != null && !filter.equalsIgnoreCase(row.getServiceName())) {
                skipped++;
                continue;
            }
            Map<String, Object> item = new java.util.LinkedHashMap<>();
            item.put("tenantId", row.getTenantId());
            item.put("service", row.getServiceName());
            try {
                PlatformProperties.DbTarget target = resolveDbTarget(row.getServiceName());
                if (target == null) {
                    item.put("status", "SKIPPED");
                    item.put("reason", "no DbTarget for service");
                    skipped++;
                } else {
                    provisioner.migrateExisting(row.getJdbcUrl(), target);
                    markRouting(row, TenantRoutingStatuses.ACTIVE);
                    item.put("status", "OK");
                    item.put("routingStatus", TenantRoutingStatuses.ACTIVE);
                    ok++;
                }
            } catch (Exception e) {
                failed++;
                item.put("status", "FAILED");
                item.put("error", e.getMessage());
                try {
                    markRouting(row, TenantRoutingStatuses.SCHEMA_OUTDATED);
                    item.put("routingStatus", TenantRoutingStatuses.SCHEMA_OUTDATED);
                } catch (Exception markEx) {
                    log.warn("Could not mark SCHEMA_OUTDATED tenant={} service={}: {}",
                            row.getTenantId(), row.getServiceName(), markEx.getMessage());
                }
                log.warn("migrate-all failed tenant={} service={}: {}",
                        row.getTenantId(), row.getServiceName(), e.getMessage());
            }
            results.add(item);
        }
        java.util.Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("tenants", tenants.size());
        out.put("serviceFilter", filter == null ? "ALL" : filter);
        out.put("ok", ok);
        out.put("failed", failed);
        out.put("skipped", skipped);
        out.put("results", results);
        out.put("message", filter == null
                ? "Fleet schema migrate complete (all services; no reseed). Failed rows marked SCHEMA_OUTDATED."
                : "Fleet schema migrate complete for service=" + filter
                        + " only (other services untouched; no reseed).");
        return out;
    }

    private void markRouting(TenantRouting row, String status) {
        row.setStatus(status);
        row.setVersion(System.currentTimeMillis());
        row.setUpdatedAt(Instant.now());
        repository.save(row);
        cachePublisher.publishRouting(row);
    }

    private PlatformProperties.DbTarget resolveDbTarget(String serviceName) {
        if (serviceName == null) {
            return null;
        }
        return switch (serviceName) {
            case SERVICE_PEOPLE, SERVICE_HRMS -> properties.getProvision().getPeople();
            case SERVICE_LEAVE -> properties.getProvision().getLeave();
            case SERVICE_TIME_WORK -> properties.getProvision().getTimeWork();
            case SERVICE_PAYROLL -> properties.getProvision().getPayroll();
            case SERVICE_FILE -> properties.getProvision().getFiles();
            case SERVICE_NOTIFICATION -> properties.getProvision().getNotification();
            case SERVICE_REPORT -> properties.getProvision().getReport();
            default -> null;
        };
    }

    /**
     * Split every known tenant from legacy {@code hrms_*} into {@code people_*} / {@code leave_*} /
     * {@code timework_*} databases and rewrite registry routing. Idempotent.
     */
    public Map<String, Object> splitWorkforceAllTenants() {
        java.util.Set<String> tenants = new java.util.LinkedHashSet<>();
        for (TenantRouting row : repository.findAllByOrderByTenantIdAscServiceNameAsc()) {
            tenants.add(row.getTenantId());
        }
        java.util.List<Map<String, Object>> results = new java.util.ArrayList<>();
        int ok = 0;
        int failed = 0;
        for (String tenantId : tenants) {
            Map<String, Object> item = new java.util.LinkedHashMap<>();
            item.put("tenantId", tenantId);
            try {
                item.putAll(ensureWorkforceServiceRouting(tenantId));
                item.put("status", "OK");
                ok++;
            } catch (Exception e) {
                failed++;
                item.put("status", "FAILED");
                item.put("error", e.getMessage());
                log.warn("Workforce split failed for tenant={}: {}", tenantId, e.getMessage(), e);
            }
            results.add(item);
        }
        return new java.util.LinkedHashMap<>(Map.of(
                "tenants", tenants.size(),
                "ok", ok,
                "failed", failed,
                "results", results,
                "message", "Workforce DB split complete — people_/leave_/timework_ created; hrms routing aliased to people."
        ));
    }

    /**
     * Idempotent cutover: create people_/leave_/timework_ DBs, clone from legacy hrms_* when needed,
     * and register service routings so the three workforce services no longer share one DB.
     */
    public Map<String, Object> ensureWorkforceServiceRouting(String tenantId) {
        long version = System.currentTimeMillis();
        Instant now = Instant.now();
        String peopleDb = TenantIds.peopleDbName(tenantId);
        String leaveDb = TenantIds.leaveDbName(tenantId);
        String timeWorkDb = TenantIds.timeWorkDbName(tenantId);

        String peopleJdbc = provisioner.createAndMigrate(peopleDb, properties.getProvision().getPeople());
        String leaveJdbc = provisioner.createAndMigrate(leaveDb, properties.getProvision().getLeave());
        String timeWorkJdbc = provisioner.createAndMigrate(timeWorkDb, properties.getProvision().getTimeWork());

        // Source of truth for cutover: legacy hrms_* DB if still present with data.
        String legacyJdbc = resolveLegacyHrmsJdbc(tenantId, peopleJdbc);
        boolean clonedFromLegacy = false;
        if (legacyJdbc != null && isEmployeeTableEmpty(peopleJdbc) && !isEmployeeTableEmpty(legacyJdbc)) {
            PlatformProperties.DbTarget people = properties.getProvision().getPeople();
            databaseCloner.cloneAllTables(
                    legacyJdbc, peopleJdbc, people.getUsername(), people.getPassword());
            clonedFromLegacy = true;
            log.info("Copied legacy {} → {}", legacyJdbc, peopleJdbc);
        }

        // Seed leave/timework only while People still holds domain rows (post-clone, pre-purge).
        // Never re-seed from a purged People DB — that would wipe satellite SoT.
        boolean leaveNeedsSeed = isTableEmpty(leaveJdbc, "leave_type")
                && !isTableEmpty(peopleJdbc, "leave_type");
        boolean timeWorkNeedsSeed = isTableEmpty(timeWorkJdbc, "attendance_record")
                && !isTableEmpty(peopleJdbc, "attendance_record");
        // Fresh tenants may have leave_type catalog but empty attendance — also seed when
        // people still has leave_application (or we just cloned the monolith dump).
        boolean peopleStillHoldsLeaveSoT = !isTableEmpty(peopleJdbc, "leave_application")
                || !isTableEmpty(peopleJdbc, "leave_type");
        boolean peopleStillHoldsTimeSoT = !isTableEmpty(peopleJdbc, "attendance_record")
                || !isTableEmpty(peopleJdbc, "work_schedule");
        if (clonedFromLegacy
                || (leaveNeedsSeed && peopleStillHoldsLeaveSoT)
                || (timeWorkNeedsSeed && peopleStillHoldsTimeSoT)) {
            if (!isEmployeeTableEmpty(peopleJdbc)) {
                distributeDomainData(peopleJdbc, leaveJdbc, timeWorkJdbc);
            }
        }
        // Always split settings + audit ownership + strip Leave/Time&Work SoT from People.
        distributeSettingsOwnership(peopleJdbc, leaveJdbc, timeWorkJdbc);
        TenantRouting payrollRoutingRow = repository.findByServiceNameAndTenantId(SERVICE_PAYROLL, tenantId).orElse(null);
        TenantRouting filesRoutingRow = repository.findByServiceNameAndTenantId(SERVICE_FILE, tenantId).orElse(null);
        TenantRouting notifRoutingRow = repository.findByServiceNameAndTenantId(SERVICE_NOTIFICATION, tenantId).orElse(null);
        TenantRouting reportRoutingRow = repository.findByServiceNameAndTenantId(SERVICE_REPORT, tenantId).orElse(null);
        partitionAuditOwnership(
                peopleJdbc, leaveJdbc, timeWorkJdbc,
                payrollRoutingRow == null ? null : payrollRoutingRow.getJdbcUrl(),
                filesRoutingRow == null ? null : filesRoutingRow.getJdbcUrl(),
                notifRoutingRow == null ? null : notifRoutingRow.getJdbcUrl(),
                reportRoutingRow == null ? null : reportRoutingRow.getJdbcUrl());
        purgeForeignTablesFromPeople(peopleJdbc);

        ensureOneRouting(SERVICE_PEOPLE, tenantId, peopleJdbc, properties.getProvision().getPeople(), version, now);
        ensureOneRouting(SERVICE_LEAVE, tenantId, leaveJdbc, properties.getProvision().getLeave(), version, now);
        ensureOneRouting(SERVICE_TIME_WORK, tenantId, timeWorkJdbc, properties.getProvision().getTimeWork(), version, now);
        // Point legacy "hrms" alias at People DB (never leave shared hrms_* as live SoT).
        ensureOneRouting(SERVICE_HRMS, tenantId, peopleJdbc, properties.getProvision().getPeople(), version, now);

        Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("peopleDb", peopleDb);
        out.put("leaveDb", leaveDb);
        out.put("timeWorkDb", timeWorkDb);
        out.put("peopleJdbc", peopleJdbc);
        out.put("leaveJdbc", leaveJdbc);
        out.put("timeWorkJdbc", timeWorkJdbc);
        out.put("clonedFromLegacyHrms", clonedFromLegacy);
        out.put("legacySourceJdbc", legacyJdbc == null ? "" : legacyJdbc);
        return out;
    }

    /**
     * Prefer explicit hrms routing when it still points at {@code hrms_*}; otherwise synthesize
     * jdbc from TenantIds on the people Postgres host.
     * <p>Always rewrite host/port to the current provision target so Docker registry can reach
     * DBs that were previously registered with {@code 127.0.0.1:5433}.
     */
    private String resolveLegacyHrmsJdbc(String tenantId, String peopleJdbc) {
        PlatformProperties.DbTarget people = properties.getProvision().getPeople();
        String dbName = TenantIds.hrmsDbName(tenantId);
        var legacyHrms = repository.findByServiceNameAndTenantId(SERVICE_HRMS, tenantId);
        if (legacyHrms.isPresent()) {
            String jdbc = legacyHrms.get().getJdbcUrl();
            if (jdbc != null && jdbc.contains("/hrms_")) {
                dbName = jdbc.substring(jdbc.lastIndexOf('/') + 1);
                String rewritten = rewriteJdbcToProvisionHost(jdbc, people);
                if (!isEmployeeTableEmpty(rewritten)) {
                    return rewritten;
                }
            }
        }
        String candidate = "jdbc:postgresql://" + people.getHost() + ":" + people.getPort() + "/" + dbName;
        if (!isEmployeeTableEmpty(candidate)) {
            return candidate;
        }
        return null;
    }

    /** Map any jdbc URL onto the people provision host/port, keeping the database name. */
    private static String rewriteJdbcToProvisionHost(String jdbcUrl, PlatformProperties.DbTarget target) {
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            return jdbcUrl;
        }
        int slash = jdbcUrl.lastIndexOf('/');
        String dbName = slash >= 0 ? jdbcUrl.substring(slash + 1) : jdbcUrl;
        return "jdbc:postgresql://" + target.getHost() + ":" + target.getPort() + "/" + dbName;
    }

    private boolean isEmployeeTableEmpty(String jdbcUrl) {
        return isTableEmpty(jdbcUrl, "employee");
    }

    /** True when table is missing or has zero rows. */
    private boolean isTableEmpty(String jdbcUrl, String table) {
        PlatformProperties.DbTarget people = properties.getProvision().getPeople();
        try (java.sql.Connection conn = java.sql.DriverManager.getConnection(
                jdbcUrl, people.getUsername(), people.getPassword());
             java.sql.Statement st = conn.createStatement();
             java.sql.ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM \"" + table + "\"")) {
            return rs.next() && rs.getLong(1) == 0L;
        } catch (Exception e) {
            // Table missing / not ready → treat as empty so clone can populate.
            return true;
        }
    }

    /**
     * Idempotent: database-per-tenant for file / notification / report + MinIO bucket + registry rows.
     * Migrates data out of legacy shared {@code files_platform} / {@code notif_platform} when those exist.
     */
    private Map<String, Object> ensureSatelliteServiceRouting(String tenantId) {
        long version = System.currentTimeMillis();
        Instant now = Instant.now();
        String filesDb = TenantIds.filesDbName(tenantId);
        String notifDb = TenantIds.notificationDbName(tenantId);
        String reportsDb = TenantIds.reportsDbName(tenantId);

        String filesJdbc = provisioner.createAndMigrate(filesDb, properties.getProvision().getFiles());
        String notifJdbc = provisioner.createAndMigrate(notifDb, properties.getProvision().getNotification());
        String reportsJdbc = provisioner.createAndMigrate(reportsDb, properties.getProvision().getReport());
        minioBucketProvisioner.ensureTenantBucket(tenantId);

        int filesCopied = TenantDataMigrator.copyFileObjects(
                properties.getProvision().getFiles(), "files_platform", filesJdbc, tenantId);
        int notifCopied = TenantDataMigrator.copyNotifications(
                properties.getProvision().getNotification(), "notif_platform", notifJdbc, tenantId);

        ensureOneRouting(SERVICE_FILE, tenantId, filesJdbc, properties.getProvision().getFiles(), version, now);
        ensureOneRouting(SERVICE_NOTIFICATION, tenantId, notifJdbc, properties.getProvision().getNotification(), version, now);
        ensureOneRouting(SERVICE_REPORT, tenantId, reportsJdbc, properties.getProvision().getReport(), version, now);

        return new java.util.LinkedHashMap<>(Map.of(
                "filesDb", filesDb,
                "notifDb", notifDb,
                "reportsDb", reportsDb,
                "filesCopied", filesCopied,
                "notificationsCopied", notifCopied,
                "filesJdbc", filesJdbc,
                "notifJdbc", notifJdbc,
                "reportsJdbc", reportsJdbc
        ));
    }

    private void ensureOneRouting(
            String service,
            String tenantId,
            String jdbc,
            PlatformProperties.DbTarget target,
            long version,
            Instant now) {
        var existing = repository.findByServiceNameAndTenantId(service, tenantId);
        if (existing.isPresent()) {
            TenantRouting row = existing.get();
            String current = row.getJdbcUrl() == null ? "" : row.getJdbcUrl();
            // Replace legacy shared *_platform / placeholder URLs, or any mismatch vs provisioned DB
            boolean needsUpdate = jdbc != null && (
                    !jdbc.equals(current)
                            || current.contains("_platform")
                            || current.contains("placeholder"));
            if (needsUpdate) {
                row.setJdbcUrl(jdbc);
                row.setUsername(target.getUsername());
                row.setPassword(target.getPassword());
                row.setStatus(TenantRoutingStatuses.ACTIVE);
                row.setVersion(version);
                row.setUpdatedAt(now);
                repository.save(row);
                cachePublisher.publishRouting(row);
            } else {
                // Ensure L2 Redis stays warm after schema migrate even when JDBC unchanged
                cachePublisher.publishRouting(row);
            }
            return;
        }
        TenantRouting row = saveRouting(service, tenantId, jdbc, target, version, now);
        cachePublisher.publishRouting(row);
    }

    private String seedBusinessPack(String pack, String hrmsJdbc, String payrollJdbc) {
        if (pack == null) {
            return "Tenant provisioned with platform-default schemas only (no business pack).";
        }
        return switch (pack) {
            case "techshop_retail", "retail", "techshop" -> {
                if (payrollJdbc != null) {
                    practiceBusinessSeeder.seedRetailPractice(
                            hrmsJdbc, payrollJdbc,
                            properties.getProvision().getHrms(),
                            properties.getProvision().getPayroll());
                } else {
                    practiceBusinessSeeder.reseedHrmsRetailLeavePack(
                            hrmsJdbc, properties.getProvision().getHrms());
                }
                yield "Seeded business pack: techshop_retail (Sat–Thu, store coverage, ANNUAL leave type).";
            }
            case "impit_limited", "impit" -> {
                if (payrollJdbc != null) {
                    impitBusinessSeeder.seedImpitPractice(
                            hrmsJdbc, payrollJdbc,
                            properties.getProvision().getHrms(),
                            properties.getProvision().getPayroll());
                } else {
                    impitBusinessSeeder.reseedHrms(hrmsJdbc, properties.getProvision().getHrms());
                }
                yield "Seeded business pack: impit_limited (Mon–Fri, SICK/CASUAL/ANNUAL/UNPAID, office coverage).";
            }
            case "qii_creative", "qii" -> {
                if (payrollJdbc != null) {
                    qiiBusinessSeeder.seedQiiCreative(
                            hrmsJdbc, payrollJdbc,
                            properties.getProvision().getHrms(),
                            properties.getProvision().getPayroll());
                } else {
                    qiiBusinessSeeder.reseedHrms(hrmsJdbc, properties.getProvision().getHrms());
                }
                yield "Seeded business pack: qii_creative (Asman Super Admin, BD+AU, RBAC, Sun–Thu schedules).";
            }
            default -> throw new IllegalArgumentException(
                    "Unknown businessPack=" + pack + " (use techshop_retail, impit_limited, or qii_creative)");
        };
    }

    private TenantRouting saveRouting(
            String service,
            String tenantId,
            String jdbcUrl,
            PlatformProperties.DbTarget target,
            long version,
            Instant now) {
        TenantRouting row = new TenantRouting();
        row.setServiceName(service);
        row.setTenantId(tenantId);
        row.setJdbcUrl(jdbcUrl);
        row.setUsername(target.getUsername());
        row.setPassword(target.getPassword());
        row.setStatus(TenantRoutingStatuses.ACTIVE);
        row.setVersion(version);
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        return repository.save(row);
    }
}
