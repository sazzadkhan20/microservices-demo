package com.platform.people.config;

import com.platform.people.application.RbacService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * On boot, grant any new matrix-visible catalog permissions to ADMIN at ALL
 * (Appendix B). SUPERADMIN is covered by {@code grants_wildcard}.
 */
@Component
public class PrivilegedRolePermissionBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PrivilegedRolePermissionBootstrap.class);

    private final RbacService rbacService;

    public PrivilegedRolePermissionBootstrap(RbacService rbacService) {
        this.rbacService = rbacService;
    }

    @Override
    public void run(ApplicationArguments args) {
        int added = rbacService.ensurePrivilegedRolesHaveCatalogPermissions();
        if (added > 0) {
            log.info("Granted {} new catalog permission(s) to ADMIN (ALL scope)", added);
        }
    }
}
