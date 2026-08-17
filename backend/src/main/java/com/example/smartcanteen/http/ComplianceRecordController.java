package com.example.smartcanteen.http;

import com.example.smartcanteen.application.ComplianceRecordService;
import com.example.smartcanteen.domain.CanteenScope;
import com.example.smartcanteen.domain.ComplianceCategory;
import com.example.smartcanteen.domain.ComplianceRecord;
import com.example.smartcanteen.domain.ComplianceRecordHistory;
import com.example.smartcanteen.domain.ComplianceRecordStatus;
import com.example.smartcanteen.security.AuthPrincipal;
import com.example.smartcanteen.security.ScopeAccess;
import com.example.smartcanteen.security.Role;
import com.example.smartcanteen.security.RoleAccess;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/compliance-records")
public class ComplianceRecordController {

    private final ComplianceRecordService service;
    private final ScopeAccess scopes;
    private final RoleAccess roles;

    public ComplianceRecordController(
            ComplianceRecordService service,
            ScopeAccess scopes,
            RoleAccess roles) {
        this.service = service;
        this.scopes = scopes;
        this.roles = roles;
    }

    @GetMapping
    public ApiResponse<OperationalController.PageView<ComplianceRecord>> list(
            HttpServletRequest request,
            @RequestParam String schoolId,
            @RequestParam String canteenId,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer expiringWithinDays,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        roles.requireReader(request);
        return ApiResponse.ok(OperationalController.PageView.from(service.list(
                scopes.require(request, schoolId, canteenId),
                category,
                status,
                expiringWithinDays,
                page,
                size)));
    }

    @GetMapping("/{recordId}")
    public ApiResponse<ComplianceRecord> find(
            HttpServletRequest request,
            @PathVariable String recordId,
            @RequestParam String schoolId,
            @RequestParam String canteenId) {
        roles.requireReader(request);
        return ApiResponse.ok(service.find(scopes.require(request, schoolId, canteenId), recordId)
                .orElseThrow(() -> new IllegalArgumentException("Compliance record not found: " + recordId)));
    }

    @PostMapping
    public ApiResponse<ComplianceRecord> create(
            HttpServletRequest request,
            @RequestParam String schoolId,
            @RequestParam String canteenId,
            @Valid @RequestBody ComplianceRequest body) {
        roles.requireAny(request, Role.SYSTEM_ADMIN, Role.SCHOOL_ADMIN, Role.CANTEEN_STAFF);
        CanteenScope scope = scopes.require(request, schoolId, canteenId);
        return ApiResponse.ok(service.create(
                scope,
                body.toDomain(body.recordId(), 0, ComplianceRecordStatus.DRAFT),
                actor(request)));
    }

    @PutMapping("/{recordId}")
    public ApiResponse<ComplianceRecord> update(
            HttpServletRequest request,
            @PathVariable String recordId,
            @RequestParam String schoolId,
            @RequestParam String canteenId,
            @Valid @RequestBody ComplianceRequest body) {
        roles.requireAny(request, Role.SYSTEM_ADMIN, Role.SCHOOL_ADMIN, Role.CANTEEN_STAFF);
        return ApiResponse.ok(service.update(
                scopes.require(request, schoolId, canteenId),
                body.toDomain(recordId, body.version(), body.status()),
                actor(request)));
    }

    @PostMapping("/{recordId}/submit")
    public ApiResponse<ComplianceRecord> submit(
            HttpServletRequest request,
            @PathVariable String recordId,
            @RequestParam String schoolId,
            @RequestParam String canteenId,
            @Valid @RequestBody VersionRequest body) {
        roles.requireAny(request, Role.SYSTEM_ADMIN, Role.SCHOOL_ADMIN, Role.CANTEEN_STAFF);
        return ApiResponse.ok(service.submit(
                scopes.require(request, schoolId, canteenId), recordId, body.version(), actor(request)));
    }

    @PostMapping("/{recordId}/review")
    public ApiResponse<ComplianceRecord> review(
            HttpServletRequest request,
            @PathVariable String recordId,
            @RequestParam String schoolId,
            @RequestParam String canteenId,
            @Valid @RequestBody ReviewRequest body) {
        roles.requireAny(request, Role.SYSTEM_ADMIN, Role.SCHOOL_ADMIN);
        return ApiResponse.ok(service.review(
                scopes.require(request, schoolId, canteenId),
                recordId,
                body.version(),
                body.status(),
                body.reviewRemark(),
                actor(request)));
    }

    @GetMapping("/{recordId}/history")
    public ApiResponse<List<ComplianceRecordHistory>> history(
            HttpServletRequest request,
            @PathVariable String recordId,
            @RequestParam String schoolId,
            @RequestParam String canteenId) {
        roles.requireReader(request);
        return ApiResponse.ok(service.history(
                scopes.require(request, schoolId, canteenId), recordId));
    }

    @PostMapping("/expiry-scan")
    public ApiResponse<List<com.example.smartcanteen.domain.AlertRecord>> expiryScan(
            HttpServletRequest request,
            @RequestParam String schoolId,
            @RequestParam String canteenId,
            @Valid @RequestBody(required = false) ExpiryScanRequest body) {
        roles.requireAny(request, Role.SYSTEM_ADMIN, Role.SCHOOL_ADMIN, Role.CANTEEN_STAFF);
        ExpiryScanRequest scan = body == null ? new ExpiryScanRequest(null, 30) : body;
        return ApiResponse.ok(service.scanExpiry(
                scopes.require(request, schoolId, canteenId),
                scan.asOf(),
                scan.windowDays()));
    }

    private static String actor(HttpServletRequest request) {
        Object value = request.getAttribute(AuthPrincipal.class.getName());
        return value instanceof AuthPrincipal principal ? principal.userId() : "SYSTEM";
    }

    public record ComplianceRequest(
            String recordId,
            @NotNull ComplianceCategory category,
            @NotBlank String subjectType,
            @NotBlank String subjectId,
            @NotBlank String subjectName,
            @NotBlank String title,
            String credentialNo,
            @NotNull LocalDate validFrom,
            @NotNull LocalDate validTo,
            List<String> attachmentRefs,
            ComplianceRecordStatus status,
            String reviewRemark,
            long version) {

        ComplianceRecord toDomain(String id, long expectedVersion, ComplianceRecordStatus fallbackStatus) {
            return new ComplianceRecord(
                    id == null || id.isBlank() ? "COMPLIANCE-" + java.util.UUID.randomUUID() : id,
                    category,
                    subjectType,
                    subjectId,
                    subjectName,
                    title,
                    credentialNo,
                    validFrom,
                    validTo,
                    attachmentRefs,
                    status == null ? fallbackStatus : status,
                    reviewRemark,
                    expectedVersion,
                    Instant.EPOCH,
                    Instant.EPOCH,
                    null,
                    null,
                    null);
        }
    }

    public record VersionRequest(long version) {
    }

    public record ReviewRequest(
            long version,
            @NotNull ComplianceRecordStatus status,
            @NotBlank String reviewRemark) {
    }

    public record ExpiryScanRequest(LocalDate asOf, @Min(0) Integer windowDays) {
        public ExpiryScanRequest {
            windowDays = windowDays == null ? 30 : windowDays;
        }
    }
}
