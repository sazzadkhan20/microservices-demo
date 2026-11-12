package com.platform.people.application;

import jakarta.persistence.PostPersist;
import jakarta.persistence.PostRemove;
import jakarta.persistence.PostUpdate;

/**
 * JPA listener on RBAC entities and {@code Employee}: any insert / update / delete clears the
 * {@link RbacCache} (immediately and after commit).
 */
public class RbacCacheInvalidationListener {

    @PostPersist
    @PostUpdate
    @PostRemove
    void changed(Object entity) {
        RbacCache.invalidateAfterCommit();
    }
}
