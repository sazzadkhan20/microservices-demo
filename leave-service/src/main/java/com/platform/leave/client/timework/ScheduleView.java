package com.platform.leave.client.timework;

/**
 * Schedule view for Leave working-day math — loaded via Time&amp;Work HTTP (not a local table).
 */
public class ScheduleView {

    private String mode = "FIXED";

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }
}
