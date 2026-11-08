package com.platform.messaging.outbox;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MessageInboxRepository extends JpaRepository<MessageInbox, Long> {
    Optional<MessageInbox> findByEventId(String eventId);

    boolean existsByEventId(String eventId);
}
