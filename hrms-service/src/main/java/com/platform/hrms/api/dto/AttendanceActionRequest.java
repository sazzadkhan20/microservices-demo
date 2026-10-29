package com.platform.hrms.api.dto;

/**
 * Check-in / check-out payload (SRS FR-ATT-02).
 * Frontend may send browser geolocation + IANA time zone + device type.
 */
public record AttendanceActionRequest(
        String locationLabel,
        String note,
        Double latitude,
        Double longitude,
        String timeZone,
        String deviceType
) {
}
