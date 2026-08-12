package com.example.smartcanteen.domain;

import java.time.Instant;

/** Filter and page contract for the unified alert center. */
public record AlertQuery(
        String schoolId,
        String canteenId,
        AlertSource source,
        AlertStatus status,
        String alarmEventId,
        String deviceName,
        Instant startDate,
        Instant endDate,
        int pageNum,
        int pageSize) {

    public AlertQuery {
        schoolId = optionalIdentifier("schoolId", schoolId, 64);
        canteenId = optionalIdentifier("canteenId", canteenId, 64);
        alarmEventId = optionalIdentifier("alarmEventId", alarmEventId, 64);
        deviceName = optionalIdentifier("deviceName", deviceName, 200);
        if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("endDate must not be before startDate");
        }
        if (pageNum < 1 || pageNum > 1_000_000) {
            throw new IllegalArgumentException("pageNum must be between 1 and 1000000");
        }
        if (pageSize < 1 || pageSize > 100) {
            throw new IllegalArgumentException("pageSize must be between 1 and 100");
        }
    }

    public boolean matches(AlertRecord record) {
        return (schoolId == null || schoolId.equals(record.schoolId()))
                && (canteenId == null || canteenId.equals(record.canteenId()))
                && (source == null || source == record.source())
                && (status == null || status == record.status())
                && (alarmEventId == null || alarmEventId.equals(record.alarmEventId()))
                && (deviceName == null || deviceName.equals(record.deviceName()))
                && (startDate == null || !record.warnHappenTime().isBefore(startDate))
                && (endDate == null || !record.warnHappenTime().isAfter(endDate));
    }

    private static String optionalIdentifier(String name, String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(name + " exceeds " + maxLength + " characters");
        }
        return normalized;
    }
}
