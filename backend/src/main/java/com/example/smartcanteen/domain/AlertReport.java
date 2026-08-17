package com.example.smartcanteen.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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

    /** Creates a normalized report for a compatibility payload without a vendor ID. */
    public static AlertReport fromExternal(
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
        Objects.requireNonNull(source, "source is required");
        Objects.requireNonNull(warnHappenTime, "warnHappenTime is required");
        String normalizedContent = warnContent == null || warnContent.isBlank()
                ? alarmEventId
                : warnContent;
        String normalizedThirdWarnId = thirdWarnId == null || thirdWarnId.isBlank()
                ? stableExternalId(
                        source, schoolId, canteenId, deviceId, deviceName,
                        alarmEventId, warnHappenTime)
                : thirdWarnId;
        return new AlertReport(
                source,
                normalizedThirdWarnId,
                schoolId,
                schoolName,
                areaCode,
                deviceId,
                deviceName,
                warnHappenTime,
                alarmEventId,
                warnFullPic,
                normalizedContent,
                canteenId);
    }

    private static String stableExternalId(
            AlertSource source,
            String schoolId,
            String canteenId,
            String deviceId,
            String deviceName,
            String alarmEventId,
            Instant occurredAt) {
        String deviceIdentity = deviceId == null || deviceId.isBlank()
                ? deviceName
                : deviceId;
        String input = String.join(
                "|",
                source.name(),
                canonicalPart(schoolId),
                canonicalPart(canteenId),
                canonicalPart(deviceIdentity),
                canonicalPart(alarmEventId),
                occurredAt.toString());
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder("generated-");
            for (byte value : digest) {
                result.append(String.format("%02x", value));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static String canonicalPart(String value) {
        return value == null ? "" : value.trim();
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
