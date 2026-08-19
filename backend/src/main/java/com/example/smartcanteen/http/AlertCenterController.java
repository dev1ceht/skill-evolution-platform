package com.example.smartcanteen.http;

import com.example.smartcanteen.application.port.AlertCenter;
import com.example.smartcanteen.application.AuthorizationService;
import com.example.smartcanteen.application.OrganizationService;
import com.example.smartcanteen.domain.AlertDisposal;
import com.example.smartcanteen.domain.AlertQuery;
import com.example.smartcanteen.domain.AlertRecord;
import com.example.smartcanteen.domain.AlertReport;
import com.example.smartcanteen.domain.AlertSource;
import com.example.smartcanteen.domain.AlertStatus;
import com.example.smartcanteen.security.AuthPrincipal;
import com.example.smartcanteen.security.ForbiddenException;
import com.example.smartcanteen.security.Role;
import com.example.smartcanteen.security.RoleAccess;
import com.example.smartcanteen.security.ScopeAccess;
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
import java.util.List;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Unified alert-center API. */
@RestController
public class AlertCenterController {

    private final AlertCenter alerts;
    private final ScopeAccess scopes;
    private final RoleAccess roles;
    private final AuthorizationService authorization;
    private final OrganizationService organization;

    public AlertCenterController(
            AlertCenter alerts, ScopeAccess scopes, RoleAccess roles,
            AuthorizationService authorization, OrganizationService organization) {
        this.alerts = alerts;
        this.scopes = scopes;
        this.roles = roles;
        this.authorization = authorization;
        this.organization = organization;
    }

    @PostMapping("/api/v1/alerts")
    public ApiResponse<AlertView> report(
            HttpServletRequest httpRequest,
            @Valid @RequestBody AlertReportRequest request) {
        // Internal callers must be both operationally privileged and inside the payload scope.
        roles.requireAny(
                httpRequest, Role.SYSTEM_ADMIN, Role.SCHOOL_ADMIN, Role.CANTEEN_STAFF);
        requirePayloadScope(httpRequest, request.schoolId(), request.canteenId());
        AlertRecord record = alerts.report(request.toDomain());
        return ApiResponse.ok(AlertView.from(record));
    }

    @PostMapping("/api/v1/alerts/{warnId}/disposal")
    public ApiResponse<AlertView> dispose(
            HttpServletRequest httpRequest,
            @PathVariable String warnId,
            @Valid @RequestBody AlertDisposalRequest request) {
        AlertRecord existing = alerts.find(warnId)
                .orElseThrow(() -> new IllegalArgumentException("Alert not found: " + warnId));
        requireAlertAccess(httpRequest, existing);
        return ApiResponse.ok(AlertView.from(alerts.dispose(
                warnId, request.toDomain())));
    }

    @GetMapping("/api/v1/alerts")
    public ApiResponse<AlertPageView> query(
            HttpServletRequest httpRequest,
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
        roles.requireReader(httpRequest);
        AuthPrincipal principal = principal(httpRequest);
        if (schoolId == null && canteenId == null && principal != null
                && !authorization.hasRole(principal, Role.SYSTEM_ADMIN)
                && !authorization.hasRole(principal, Role.REGULATOR)) {
            schoolId = principal.schoolId();
            canteenId = principal.canteenId();
        }
        if (schoolId == null && canteenId == null
                && authorization.hasRole(principal, Role.REGULATOR)) {
            throw new ForbiddenException("Regulator queries require an authorized school/canteen scope");
        }
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

    private void requirePayloadScope(
            HttpServletRequest httpRequest, String schoolId, String canteenId) {
        AuthPrincipal current = principal(httpRequest);
        if (current == null) {
            return;
        }
        if (canteenId == null || canteenId.isBlank()) {
            if (schoolId == null || schoolId.isBlank()) {
                throw new IllegalArgumentException("schoolId is required");
            }
            if (organization.findSchool(schoolId).map(school -> !school.active()).orElse(true)) {
                throw new ForbiddenException("The requested school scope is disabled or missing");
            }
            if (!authorization.hasRole(current, Role.SYSTEM_ADMIN)
                    && !authorization.canAccessSchool(current, schoolId)) {
                throw new ForbiddenException("User is outside the requested school scope");
            }
            return;
        }
        scopes.require(httpRequest, schoolId, canteenId);
    }

    private void requireAlertAccess(HttpServletRequest httpRequest, AlertRecord record) {
        roles.requireReader(httpRequest);
        AuthPrincipal current = principal(httpRequest);
        if (current == null) {
            return;
        }
        if (record.canteenId() == null || record.canteenId().isBlank()) {
            roles.requireAny(httpRequest, Role.SYSTEM_ADMIN, Role.REGULATOR);
            if (organization.findSchool(record.schoolId()).map(school -> !school.active()).orElse(true)) {
                throw new ForbiddenException("The alert school scope is disabled or missing");
            }
            if (!authorization.hasRole(current, Role.SYSTEM_ADMIN)
                    && !authorization.canAccessSchool(current, record.schoolId())) {
                throw new ForbiddenException("User is outside the alert school scope");
            }
            return;
        }
        scopes.require(httpRequest, record.schoolId(), record.canteenId());
    }

    private static AuthPrincipal principal(HttpServletRequest request) {
        Object value = request.getAttribute(AuthPrincipal.class.getName());
        return value instanceof AuthPrincipal authPrincipal ? authPrincipal : null;
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

    public record AlertDisposalRequest(
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
