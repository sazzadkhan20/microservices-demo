package com.platform.registry.application;

import com.platform.common.tenant.TenantIds;
import com.platform.registry.api.dto.ProvisionRequest;
import com.platform.registry.api.dto.ProvisionResponse;
import com.platform.registry.api.dto.TenantRoutingView;
import com.platform.registry.config.PlatformProperties;
import com.platform.registry.domain.TenantRouting;
import com.platform.registry.infrastructure.persistence.TenantRoutingRepository;
import com.platform.registry.infrastructure.provision.ImpitBusinessSeeder;
import com.platform.registry.infrastructure.provision.MinioBucketProvisioner;
import com.platform.registry.infrastructure.provision.PostgresDatabaseProvisioner;
import com.platform.registry.infrastructure.provision.PracticeBusinessSeeder;
import com.platform.registry.infrastructure.provision.QiiBusinessSeeder;
import com.platform.registry.infrastructure.redis.RoutingCachePublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
public class ProvisioningService {

    private static final Logger log = LoggerFactory.getLogger(ProvisioningService.class);

    public static final String SERVICE_HRMS = "hrms";
    public static final String SERVICE_PAYROLL = "payroll";
    public static final String SERVICE_FILE = "file";
    public static final String SERVICE_NOTIFICATION = "notification";
    public static final String SERVICE_REPORT = "report";

    private final TenantRoutingRepository repository;
    private final PostgresDatabaseProvisioner provisioner;
    private final PracticeBusinessSeeder practiceBusinessSeeder;
    private final ImpitBusinessSeeder impitBusinessSeeder;
    private final QiiBusinessSeeder qiiBusinessSeeder;
    private final RoutingCachePublisher cachePublisher;
    private final PlatformProperties properties;
    private final MinioBucketProvisioner minioBucketProvisioner;

    public ProvisioningService(
            TenantRoutingRepository repository,
            PostgresDatabaseProvisioner provisioner,
            PracticeBusinessSeeder practiceBusinessSeeder,
            ImpitBusinessSeeder impitBusinessSeeder,
            QiiBusinessSeeder qiiBusinessSeeder,
            RoutingCachePublisher cachePublisher,
            PlatformProperties properties,
            MinioBucketProvisioner minioBucketProvisioner) {
        this.repository = repository;
        this.provisioner = provisioner;
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

        String hrmsDb = TenantIds.hrmsDbName(tenantId);
        String payrollDb = TenantIds.payrollDbName(tenantId);

        String hrmsJdbc = provisioner.createAndMigrate(hrmsDb, properties.getProvision().getHrms());
        String payrollJdbc = provisioner.createAndMigrate(payrollDb, properties.getProvision().getPayroll());
        // Files/notifications: shared Postgres DBs (tenant_id column) + MinIO bucket per tenant.
        // HRMS/Payroll stay database-per-tenant. Both patterns are reusable across businesses.
        String filesJdbc = provisioner.createAndMigrate("files_platform", properties.getProvision().getFiles());
        String notifJdbc = provisioner.createAndMigrate("notif_platform", properties.getProvision().getNotification());
        minioBucketProvisioner.ensureTenantBucket(tenantId);

        String pack = request.resolvedBusinessPack();
        String message = seedBusinessPack(pack, hrmsJdbc, payrollJdbc);

        TenantRouting hrmsRouting = saveRouting(SERVICE_HRMS, tenantId, hrmsJdbc,
                properties.getProvision().getHrms(), version, now);
        TenantRouting payrollRouting = saveRouting(SERVICE_PAYROLL, tenantId, payrollJdbc,
                properties.getProvision().getPayroll(), version, now);
        TenantRouting fileRouting = saveRouting(SERVICE_FILE, tenantId, filesJdbc,
                properties.getProvision().getFiles(), version, now);
        TenantRouting notifRouting = saveRouting(SERVICE_NOTIFICATION, tenantId, notifJdbc,
                properties.getProvision().getNotification(), version, now);
        TenantRouting reportRouting = saveRouting(SERVICE_REPORT, tenantId,
                "jdbc:platform:shared/reports", properties.getProvision().getHrms(), version, now);

        cachePublisher.publishRouting(hrmsRouting);
        cachePublisher.publishRouting(payrollRouting);
        cachePublisher.publishRouting(fileRouting);
        cachePublisher.publishRouting(notifRouting);
        cachePublisher.publishRouting(reportRouting);

        log.info("Provisioned tenant {} → {} , {} + files/notif platform DBs + MinIO (pack={})",
                tenantId, hrmsDb, payrollDb, pack);

        return new ProvisionResponse(
                tenantId,
                List.of(
                        TenantRoutingView.from(hrmsRouting),
                        TenantRoutingView.from(payrollRouting),
                        TenantRoutingView.from(fileRouting),
                        TenantRoutingView.from(notifRouting),
                        TenantRoutingView.from(reportRouting)
                ),
                message
        );
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
        TenantRouting hrms = repository.findByServiceNameAndTenantId(SERVICE_HRMS, normalized)
                .orElseThrow(() -> new IllegalArgumentException("HRMS routing not found for " + normalized));

        provisioner.migrateExisting(hrms.getJdbcUrl(), properties.getProvision().getHrms());

        String pack = businessPack;
        if (reseed) {
            if (pack == null || pack.isBlank()) {
                throw new IllegalArgumentException(
                        "businessPack is required when reseed=true (use techshop_retail, impit_limited, or qii_creative)");
            }
            seedBusinessPack(pack, hrms.getJdbcUrl(), null);
        }
        ensureSharedServiceRouting(normalized);
        cachePublisher.publishRouting(hrms);
        return Map.of(
                "tenantId", normalized,
                "jdbcUrl", hrms.getJdbcUrl(),
                "migrated", true,
                "reseed", reseed,
                "businessPack", pack == null ? "" : pack,
                "message", "HRMS schema upgraded; optional business pack reseed applied."
        );
    }

    @Transactional
    public Map<String, Object> upgradePayrollSchema(String tenantId) {
        String normalized = TenantIds.normalize(tenantId);
        TenantRouting payroll = repository.findByServiceNameAndTenantId(SERVICE_PAYROLL, normalized)
                .orElseThrow(() -> new IllegalArgumentException("Payroll routing not found for " + normalized));
        provisioner.migrateExisting(payroll.getJdbcUrl(), properties.getProvision().getPayroll());
        cachePublisher.publishRouting(payroll);
        return Map.of(
                "tenantId", normalized,
                "jdbcUrl", payroll.getJdbcUrl(),
                "migrated", true,
                "message", "Payroll schema upgraded (salary_history and config tables)."
        );
    }

    /** Idempotent: ensure shared files/notif Postgres + MinIO bucket + registry rows exist. */
    private void ensureSharedServiceRouting(String tenantId) {
        long version = System.currentTimeMillis();
        Instant now = Instant.now();
        String filesJdbc = provisioner.createAndMigrate("files_platform", properties.getProvision().getFiles());
        String notifJdbc = provisioner.createAndMigrate("notif_platform", properties.getProvision().getNotification());
        minioBucketProvisioner.ensureTenantBucket(tenantId);
        ensureOneRouting(SERVICE_FILE, tenantId, filesJdbc, properties.getProvision().getFiles(), version, now);
        ensureOneRouting(SERVICE_NOTIFICATION, tenantId, notifJdbc, properties.getProvision().getNotification(), version, now);
        ensureOneRouting(SERVICE_REPORT, tenantId, "jdbc:platform:shared/reports",
                properties.getProvision().getHrms(), version, now);
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
            if (jdbc != null && !jdbc.equals(row.getJdbcUrl()) && !jdbc.startsWith("jdbc:platform:shared/")) {
                row.setJdbcUrl(jdbc);
                row.setUsername(target.getUsername());
                row.setPassword(target.getPassword());
                row.setVersion(version);
                row.setUpdatedAt(now);
                repository.save(row);
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
        row.setStatus("ACTIVE");
        row.setVersion(version);
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        return repository.save(row);
    }
}
