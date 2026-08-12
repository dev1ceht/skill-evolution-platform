package com.example.smartcanteen.http;

import com.example.smartcanteen.application.port.AlertCenter;
import com.example.smartcanteen.domain.AlertDisposal;
import com.example.smartcanteen.domain.AlertQuery;
import com.example.smartcanteen.domain.AlertRecord;
import com.example.smartcanteen.domain.AlertReport;
import com.example.smartcanteen.domain.AlertSource;
import com.example.smartcanteen.domain.AlertStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Unified alert-center API plus the external paths specified by the design PDF. */
@RestController
public class AlertCenterController {

    private final AlertCenter alerts;

    public AlertCenterController(AlertCenter alerts) {
        this.alerts = alerts;
    }

    @PostMapping("/api/v1/alerts")
    public ApiResponse<AlertView> report(@Valid @RequestBody AlertReportRequest request) {
        AlertRecord record = alerts.report(request.toDomain());
        return ApiResponse.ok(AlertView.from(record));
    }

    @PostMapping("/alarmApi/warn/report")
    public ApiResponse<AlertView> reportExternal(
            @Valid @RequestBody ExternalAlertReportRequest request) {
        AlertRecord record = alerts.report(request.toDomain());
        return ApiResponse.ok(AlertView.from(record));
    }

    @PostMapping("/api/v1/alerts/{warnId}/disposal")
    public ApiResponse<AlertView> dispose(
            @PathVariable String warnId,
            @Valid @RequestBody AlertDisposalRequest request) {
        return ApiResponse.ok(AlertView.from(alerts.dispose(
                warnId, request.toDomain())));
    }

    @PostMapping("/alarmApi/warnResult/report")
    public ApiResponse<AlertView> disposeExternal(
            @Valid @RequestBody AlertDisposalRequest request) {
        String warnId = request.warnId;
        if (warnId == null || warnId.isBlank()) {
            if (request.thirdWarnId == null || request.thirdWarnId.isBlank()) {
                throw new IllegalArgumentException(
                        "warnId or thirdWarnId is required for external disposal");
            }
            AlertSource source = request.source == null
                    ? AlertSource.BRIGHT_KITCHEN
                    : AlertSource.from(request.source);
            warnId = source.name() + ":" + request.thirdWarnId.trim();
        }
        return ApiResponse.ok(AlertView.from(alerts.dispose(
                warnId.trim(), request.toDomain())));
    }

    @GetMapping({"/api/v1/alerts", "/alarmWarn/school/queryPage"})
    public ApiResponse<AlertPageView> query(
            @RequestParam(required = false) String schoolId,
            @RequestParam(required = false) String canteenId,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String status,
            @RequestParam(required = false, name = "warnStatus") String warnStatus,
            @RequestParam(required = false) String alarmEventId,
            @RequestParam(required = false) String deviceName,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        String statusValue = status == null ? warnStatus : status;
        AlertCenter.AlertPage page = alerts.query(new AlertQuery(
                schoolId,
                canteenId,
                source == null ? null : AlertSource.from(source),
                statusValue == null ? null : AlertStatus.from(statusValue),
                alarmEventId,
                deviceName,
                parseDateTime(startDate, false),
                parseDateTime(endDate, true),
                pageNum,
                pageSize));
        return ApiResponse.ok(AlertPageView.from(page));
    }

    private static Instant parseDateTime(String value, boolean endOfDay) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        try {
            if (normalized.length() == 10) {
                LocalDate date = LocalDate.parse(normalized);
                return date.atTime(endOfDay ? LocalTime.MAX : LocalTime.MIN)
                        .toInstant(ZoneOffset.UTC);
            }
            try {
                return Instant.parse(normalized);
            } catch (DateTimeParseException ignored) {
                return LocalDateTime.parse(
                                normalized,
                                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                        .toInstant(ZoneOffset.UTC);
            }
        } catch (java.time.DateTimeException exception) {
            throw new IllegalArgumentException(
                    "date must be ISO-8601 date or date-time: " + value);
        }
    }

    public record AlertReportRequest(
            String source,
            @NotBlank String thirdWarnId,
            @NotBlank String schoolId,
            String schoolName,
            String areaCode,
            String deviceId,
            String deviceName,
            String canteenId,
            @NotNull Instant warnHappenTime,
            @NotBlank String alarmEventId,
            String warnFullPic,
            @NotBlank String warnContent) {

        AlertReport toDomain() {
            return new AlertReport(
                    source == null ? AlertSource.BRIGHT_KITCHEN : AlertSource.from(source),
                    thirdWarnId,
                    schoolId,
                    schoolName,
                    areaCode,
                    deviceId,
                    deviceName,
                    warnHappenTime,
                    alarmEventId,
                    warnFullPic,
                    warnContent,
                    canteenId);
        }
    }

    public record ExternalAlertReportRequest(
            String source,
            String thirdWarnId,
            @NotBlank String schoolId,
            String schoolName,
            String areaCode,
            String deviceId,
            String deviceName,
            String canteenId,
            @NotBlank String warnHappenTime,
            @NotBlank String alarmEventId,
            String warnFullPic,
            String warnContent) {

        AlertReport toDomain() {
            AlertSource resolvedSource = source == null || source.isBlank()
                    ? (hasDeviceFields() ? AlertSource.BRIGHT_KITCHEN
                            : AlertSource.DISTRICT_PLATFORM)
                    : AlertSource.from(source);
            String content = warnContent == null || warnContent.isBlank()
                    ? alarmEventId
                    : warnContent;
            String resolvedThirdWarnId = thirdWarnId == null || thirdWarnId.isBlank()
                    ? stableExternalId(resolvedSource, schoolId, alarmEventId,
                            warnHappenTime, content)
                    : thirdWarnId;
            return new AlertReport(
                    resolvedSource,
                    resolvedThirdWarnId,
                    schoolId,
                    schoolName,
                    areaCode,
                    deviceId,
                    deviceName,
                    parseDateTime(warnHappenTime, false),
                    alarmEventId,
                    warnFullPic,
                    content,
                    canteenId);
        }

        private boolean hasDeviceFields() {
            return (deviceId != null && !deviceId.isBlank())
                    || (deviceName != null && !deviceName.isBlank())
                    || (warnFullPic != null && !warnFullPic.isBlank());
        }
    }

    public record AlertDisposalRequest(
            String source,
            String thirdWarnId,
            String warnId,
            @NotNull @DecimalMin(value = "0") @DecimalMax(value = "1") Integer processStatus,
            String processTime,
            String processUser,
            String processContent,
            String processFile) {

        AlertDisposal toDomain() {
            return new AlertDisposal(
                    processStatus,
                    parseDateTime(processTime, false),
                    processUser,
                    processContent,
                    processFile);
        }
    }

    private static String stableExternalId(
            AlertSource source,
            String schoolId,
            String alarmEventId,
            String warnHappenTime,
            String warnContent) {
        String input = String.join("|", source.name(), schoolId, alarmEventId,
                warnHappenTime, warnContent);
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

    public record AlertView(
            String warnId,
            String source,
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
            String status,
            int processStatus,
            Instant createdAt,
            Instant processTime,
            String processUser,
            String processContent,
            String processFile) {

        static AlertView from(AlertRecord record) {
            return new AlertView(
                    record.warnId(),
                    record.source().name(),
                    record.thirdWarnId(),
                    record.schoolId(),
                    record.schoolName(),
                    record.areaCode(),
                    record.deviceId(),
                    record.deviceName(),
                    record.canteenId(),
                    record.warnHappenTime(),
                    record.alarmEventId(),
                    record.warnFullPic(),
                    record.warnContent(),
                    record.status().name(),
                    record.status() == AlertStatus.PROCESSED ? 1 : 0,
                    record.createdAt(),
                    record.processTime(),
                    record.processUser(),
                    record.processContent(),
                    record.processFile());
        }
    }

    public record AlertPageView(
            List<AlertView> records,
            int pageNum,
            int pageSize,
            long total) {

        static AlertPageView from(AlertCenter.AlertPage page) {
            return new AlertPageView(
                    page.records().stream().map(AlertView::from).toList(),
                    page.pageNum(),
                    page.pageSize(),
                    page.total());
        }
    }
}
