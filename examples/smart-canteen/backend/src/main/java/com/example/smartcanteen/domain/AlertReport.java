package com.example.smartcanteen.domain;

import java.time.Instant;
import java.util.Objects;

/** Normalized alert input shared by device and platform adapters. */
public record AlertReport(
        AlertSource source,
        String thirdWarnId,
        String schoolId,
        String schoolName,
        String areaCode,
        String deviceId,
        String deviceName,
        Instant warnHappenTime,
        String alarmEventId,
        String warnFullPic,
        String warnContent,
        String canteenId) {

    public AlertReport {
        source = Objects.requireNonNull(source, "source is required");
        thirdWarnId = required("thirdWarnId", thirdWarnId, 128);
        schoolId = required("schoolId", schoolId, 64);
        schoolName = optional("schoolName", schoolName, 200);
        areaCode = optional("areaCode", areaCode, 32);
        deviceId = optional("deviceId", deviceId, 64);
        deviceName = optional("deviceName", deviceName, 200);
        warnHappenTime = Objects.requireNonNull(warnHappenTime, "warnHappenTime is required");
        alarmEventId = required("alarmEventId", alarmEventId, 64);
        warnFullPic = optional("warnFullPic", warnFullPic, 500);
        warnContent = required("warnContent", warnContent, 2000);
        canteenId = optional("canteenId", canteenId, 64);
    }

    public String warnId() {
        return source.name() + ":" + thirdWarnId;
    }

    private static String required(String name, String value, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(name + " exceeds " + maxLength + " characters");
        }
        return normalized;
    }

    private static String optional(String name, String value, int maxLength) {
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
