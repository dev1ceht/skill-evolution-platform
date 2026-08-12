package com.example.smartcanteen.domain;

import java.time.Instant;
import java.util.Objects;

/** Durable normalized warning and its latest disposal state. */
public record AlertRecord(
        String warnId,
        AlertSource source,
        String thirdWarnId,
        String schoolId,
        String schoolName,
        String areaCode,
        String deviceId,
        String deviceName,
        String canteenId,
        Instant warnHappenTime,
        String alarmEventId,
        String warnFullPic,
        String warnContent,
        AlertStatus status,
        Instant createdAt,
        Instant processTime,
        String processUser,
        String processContent,
        String processFile) {

    public AlertRecord {
        warnId = Objects.requireNonNull(warnId, "warnId");
        source = Objects.requireNonNull(source, "source");
        thirdWarnId = Objects.requireNonNull(thirdWarnId, "thirdWarnId");
        schoolId = Objects.requireNonNull(schoolId, "schoolId");
        warnHappenTime = Objects.requireNonNull(warnHappenTime, "warnHappenTime");
        alarmEventId = Objects.requireNonNull(alarmEventId, "alarmEventId");
        warnContent = Objects.requireNonNull(warnContent, "warnContent");
        status = Objects.requireNonNull(status, "status");
        createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    public static AlertRecord reported(String warnId, AlertReport report, Instant createdAt) {
        return new AlertRecord(
                warnId,
                report.source(),
                report.thirdWarnId(),
                report.schoolId(),
                report.schoolName(),
                report.areaCode(),
                report.deviceId(),
                report.deviceName(),
                report.canteenId(),
                report.warnHappenTime(),
                report.alarmEventId(),
                report.warnFullPic(),
                report.warnContent(),
                AlertStatus.UNPROCESSED,
                createdAt,
                null,
                null,
                null,
                null);
    }

    public AlertRecord withDisposal(AlertDisposal disposal) {
        return new AlertRecord(
                warnId, source, thirdWarnId, schoolId, schoolName, areaCode, deviceId,
                deviceName, canteenId, warnHappenTime, alarmEventId, warnFullPic,
                warnContent,
                disposal.processStatus() == 1 ? AlertStatus.PROCESSED : AlertStatus.UNPROCESSED,
                createdAt,
                disposal.processTime(),
                disposal.processUser(),
                disposal.processContent(),
                disposal.processFile());
    }

    public boolean matches(AlertReport report) {
        return source == report.source()
                && thirdWarnId.equals(report.thirdWarnId())
                && Objects.equals(schoolId, report.schoolId())
                && Objects.equals(schoolName, report.schoolName())
                && Objects.equals(areaCode, report.areaCode())
                && Objects.equals(deviceId, report.deviceId())
                && Objects.equals(deviceName, report.deviceName())
                && Objects.equals(canteenId, report.canteenId())
                && warnHappenTime.equals(report.warnHappenTime())
                && alarmEventId.equals(report.alarmEventId())
                && Objects.equals(warnFullPic, report.warnFullPic())
                && warnContent.equals(report.warnContent());
    }

    public boolean hasSameDisposal(AlertDisposal disposal) {
        return (disposal.processStatus() == 1) == (status == AlertStatus.PROCESSED)
                && Objects.equals(processTime, disposal.processTime())
                && Objects.equals(processUser, disposal.processUser())
                && Objects.equals(processContent, disposal.processContent())
                && Objects.equals(processFile, disposal.processFile());
    }
}
