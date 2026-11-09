package com.platform.messaging.outbox;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface MessageOutboxRepository extends JpaRepository<MessageOutbox, Long> {

    /** MSG-01 — skip rows still in exponential backoff (published_at used as next_attempt_at while PENDING). */
    @Query(value = """
            SELECT * FROM message_outbox
            WHERE status = 'PENDING'
              AND (published_at IS NULL OR published_at <= :now)
            ORDER BY created_at ASC
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<MessageOutbox> findPendingForUpdate(@Param("batchSize") int batchSize, @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE message_outbox
            SET status = 'PENDING', published_at = NULL
            WHERE status = 'PUBLISHING'
              AND published_at IS NOT NULL
              AND published_at < :cutoff
            """, nativeQuery = true)
    int reclaimStalePublishing(@Param("cutoff") Instant cutoff);

    /** Legacy helper kept for diagnostics / tests. Prefer {@link #findPendingForUpdate}. */
    @Query("select o from MessageOutbox o where o.status = 'PENDING' order by o.createdAt asc")
    List<MessageOutbox> findPending(Pageable pageable);
}
