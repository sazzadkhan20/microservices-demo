package com.platform.payroll.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "platform")
public class PayrollProperties {

    private String serviceName = "payroll";
    private Registry registry = new Registry();
    private DatasourcePool datasource = new DatasourcePool();
    private Cache cache = new Cache();
    private Tenancy tenancy = new Tenancy();

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public Registry getRegistry() {
        return registry;
    }

    public void setRegistry(Registry registry) {
        this.registry = registry;
    }

    public Tenancy getTenancy() {
        return tenancy;
    }

    public void setTenancy(Tenancy tenancy) {
        this.tenancy = tenancy;
    }

    public DatasourcePool getDatasource() {
        return datasource;
    }

    public void setDatasource(DatasourcePool datasource) {
        this.datasource = datasource;
    }

    public Cache getCache() {
        return cache;
    }

    public void setCache(Cache cache) {
        this.cache = cache;
    }

    public static class Registry {
        private String baseUrl;
        private String serviceKey;

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getServiceKey() {
            return serviceKey;
        }

        public void setServiceKey(String serviceKey) {
            this.serviceKey = serviceKey;
        }
    }

    public static class DatasourcePool {
        private int maximumPoolSize = 5;
        private int minimumIdle = 1;
        private long maxLifetimeMs = 1_800_000L;

        public int getMaximumPoolSize() {
            return maximumPoolSize;
        }

        public void setMaximumPoolSize(int maximumPoolSize) {
            this.maximumPoolSize = maximumPoolSize;
        }

        public int getMinimumIdle() {
            return minimumIdle;
        }

        public void setMinimumIdle(int minimumIdle) {
            this.minimumIdle = minimumIdle;
        }

        public long getMaxLifetimeMs() {
            return maxLifetimeMs;
        }

        public void setMaxLifetimeMs(long maxLifetimeMs) {
            this.maxLifetimeMs = maxLifetimeMs;
        }
    }

    public static class Cache {
        private long l1MaxSize = 50;
        private long l1ExpireAfterAccessMinutes = 5;

        public long getL1MaxSize() {
            return l1MaxSize;
        }

        public void setL1MaxSize(long l1MaxSize) {
            this.l1MaxSize = l1MaxSize;
        }

        public long getL1ExpireAfterAccessMinutes() {
            return l1ExpireAfterAccessMinutes;
        }

        public void setL1ExpireAfterAccessMinutes(long l1ExpireAfterAccessMinutes) {
            this.l1ExpireAfterAccessMinutes = l1ExpireAfterAccessMinutes;
        }
    }

    /** DYNAMIC (default SaaS routing) or FIXED single-company JDBC. */
    public static class Tenancy {
        private String mode = "DYNAMIC";
        private String fixedTenantId;
        private String fixedJdbcUrl;
        private String fixedUsername;
        private String fixedPassword;

        public String getMode() {
            return mode;
        }

        public void setMode(String mode) {
            this.mode = mode;
        }

        public String getFixedTenantId() {
            return fixedTenantId;
        }

        public void setFixedTenantId(String fixedTenantId) {
            this.fixedTenantId = fixedTenantId;
        }

        public String getFixedJdbcUrl() {
            return fixedJdbcUrl;
        }

        public void setFixedJdbcUrl(String fixedJdbcUrl) {
            this.fixedJdbcUrl = fixedJdbcUrl;
        }

        public String getFixedUsername() {
            return fixedUsername;
        }

        public void setFixedUsername(String fixedUsername) {
            this.fixedUsername = fixedUsername;
        }

        public String getFixedPassword() {
            return fixedPassword;
        }

        public void setFixedPassword(String fixedPassword) {
            this.fixedPassword = fixedPassword;
        }
    }
}
