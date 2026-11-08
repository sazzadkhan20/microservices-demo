package com.platform.messaging.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "platform.messaging")
public class MessagingProperties {

    /** Master switch — when false, Rabbit listeners/relay beans stay off. */
    private boolean enabled = true;

    private int outboxBatchSize = 50;
    private int outboxMaxAttempts = 20;
    private long outboxPollMs = 1000;
    private int finalizeWaitSeconds = 25;
    /** Guide §7: wait for broker publisher confirm before marking outbox PUBLISHED. */
    private long publishConfirmTimeoutMs = 5000;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getOutboxBatchSize() {
        return outboxBatchSize;
    }

    public void setOutboxBatchSize(int outboxBatchSize) {
        this.outboxBatchSize = outboxBatchSize;
    }

    public int getOutboxMaxAttempts() {
        return outboxMaxAttempts;
    }

    public void setOutboxMaxAttempts(int outboxMaxAttempts) {
        this.outboxMaxAttempts = outboxMaxAttempts;
    }

    public long getOutboxPollMs() {
        return outboxPollMs;
    }

    public void setOutboxPollMs(long outboxPollMs) {
        this.outboxPollMs = outboxPollMs;
    }

    public int getFinalizeWaitSeconds() {
        return finalizeWaitSeconds;
    }

    public void setFinalizeWaitSeconds(int finalizeWaitSeconds) {
        this.finalizeWaitSeconds = finalizeWaitSeconds;
    }

    public long getPublishConfirmTimeoutMs() {
        return publishConfirmTimeoutMs;
    }

    public void setPublishConfirmTimeoutMs(long publishConfirmTimeoutMs) {
        this.publishConfirmTimeoutMs = publishConfirmTimeoutMs;
    }
}
