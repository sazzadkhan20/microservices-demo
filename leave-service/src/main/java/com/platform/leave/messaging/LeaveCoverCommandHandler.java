package com.platform.leave.messaging;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * LV-02 — Spring proxy so {@code @Transactional} applies (listener must not call {@code this.handle}).
 */
@Service
public class LeaveCoverCommandHandler {

    private final LeaveCoverCommandListener listener;

    public LeaveCoverCommandHandler(@Lazy LeaveCoverCommandListener listener) {
        this.listener = listener;
    }

    @Transactional
    public void handle(Map<String, Object> envelope) {
        listener.handleInTransaction(envelope);
    }
}
