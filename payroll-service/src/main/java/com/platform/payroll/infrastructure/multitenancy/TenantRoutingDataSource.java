package com.platform.payroll.infrastructure.multitenancy;

import com.platform.common.tenant.TenantContext;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

import javax.sql.DataSource;
import java.util.Collections;

/**
 * Spring {@link AbstractRoutingDataSource} keyed by {@link TenantContext}.
 *
 * <p>Actual pools are created lazily by {@link TenantDataSourceManager}
 * (not pre-registered in {@code targetDataSources}).
 */
public class TenantRoutingDataSource extends AbstractRoutingDataSource {

    private final TenantDataSourceManager manager;

    public TenantRoutingDataSource(TenantDataSourceManager manager) {
        this.manager = manager;
        // Required by AbstractRoutingDataSource lifecycle; real lookup is overridden below.
        setTargetDataSources(Collections.emptyMap());
        setLenientFallback(false);
        afterPropertiesSet();
    }

    @Override
    protected Object determineCurrentLookupKey() {
        return TenantContext.getTenantId();
    }

    @Override
    protected DataSource determineTargetDataSource() {
        Object key = determineCurrentLookupKey();
        if (key == null) {
            throw new IllegalStateException("No tenant bound in TenantContext — cannot open JDBC connection");
        }
        return manager.resolve(key.toString());
    }
}
