package com.example.smartcanteen.http;

import com.example.smartcanteen.application.ConfigurableLedgerService;
import com.example.smartcanteen.domain.CanteenScope;
import com.example.smartcanteen.domain.ConfiguredLedgerCycle;
import com.example.smartcanteen.domain.LedgerConfiguration;
import com.example.smartcanteen.domain.LedgerConfigurationStatus;
import com.example.smartcanteen.domain.LedgerFrequency;
import com.example.smartcanteen.domain.OperationalLedgerRecord;
import com.example.smartcanteen.security.ScopeAccess;
import com.example.smartcanteen.security.Role;
import com.example.smartcanteen.security.RoleAccess;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class LedgerConfigurationController {

    private final ConfigurableLedgerService service;
    private final ScopeAccess scopes;
    private final RoleAccess roles;

    public LedgerConfigurationController(
            ConfigurableLedgerService service,
            ScopeAccess scopes,
            RoleAccess roles) {
        this.service = service;
        this.scopes = scopes;
        this.roles = roles;
    }

    @GetMapping("/ledger-configurations")
    public ApiResponse<List<LedgerConfiguration>> list(
            HttpServletRequest request,
            @RequestParam String schoolId,
            @RequestParam String canteenId,
            @RequestParam(defaultValue = "false") boolean includeDisabled) {
        roles.requireReader(request);
        return ApiResponse.ok(service.list(
                scopes.require(request, schoolId, canteenId), includeDisabled));
    }

    @PostMapping("/ledger-configurations")
    public ApiResponse<LedgerConfiguration> create(
            HttpServletRequest request,
            @RequestParam String schoolId,
            @RequestParam String canteenId,
            @Valid @RequestBody ConfigurationRequest body) {
        roles.requireAny(request, Role.SYSTEM_ADMIN, Role.SCHOOL_ADMIN, Role.CANTEEN_STAFF);
        CanteenScope scope = scopes.require(request, schoolId, canteenId);
        String id = body.configurationId() == null || body.configurationId().isBlank()
                ? "LEDGER-CONFIG-" + UUID.randomUUID()
                : body.configurationId();
        return ApiResponse.ok(service.create(scope, body.toDomain(id, 0)));
    }

    @PutMapping("/ledger-configurations/{configurationId}")
    public ApiResponse<LedgerConfiguration> update(
            HttpServletRequest request,
            @PathVariable String configurationId,
            @RequestParam String schoolId,
            @RequestParam String canteenId,
            @Valid @RequestBody ConfigurationRequest body) {
        roles.requireAny(request, Role.SYSTEM_ADMIN, Role.SCHOOL_ADMIN, Role.CANTEEN_STAFF);
        return ApiResponse.ok(service.update(
                scopes.require(request, schoolId, canteenId), body.toDomain(configurationId, body.version())));
    }

    @PostMapping("/ledger-cycles/configured/current")
    public ApiResponse<List<ConfiguredLedgerCycle>> ensureCurrent(
            HttpServletRequest request,
            @RequestParam String schoolId,
            @RequestParam String canteenId,
            @RequestParam(required = false) LocalDate asOf) {
        roles.requireAny(request, Role.SYSTEM_ADMIN, Role.SCHOOL_ADMIN, Role.CANTEEN_STAFF);
        return ApiResponse.ok(service.ensureCurrent(
                scopes.require(request, schoolId, canteenId), asOf));
    }

    @PostMapping("/ledger-cycles/configured/{cycleId}/records")
    public ApiResponse<OperationalController.LedgerRecordView> complete(
            HttpServletRequest request,
            @PathVariable String cycleId,
            @RequestParam String schoolId,
            @RequestParam String canteenId,
            @Valid @RequestBody LedgerRecordRequest body) {
        roles.requireAny(request, Role.SYSTEM_ADMIN, Role.SCHOOL_ADMIN, Role.CANTEEN_STAFF);
        OperationalLedgerRecord record = service.complete(
                scopes.require(request, schoolId, canteenId),
                cycleId,
                body.ledgerCode(),
                body.recordId(),
                body.recordTime(),
                body.recorderId(),
                body.content(),
                body.photos(),
                body.remark());
        return ApiResponse.ok(OperationalController.LedgerRecordView.from(record));
    }

    public record ConfigurationRequest(
            String configurationId,
            @NotBlank String code,
            @NotBlank String name,
            @NotNull LedgerFrequency frequency,
            @Min(1) Integer periodDays,
            @NotEmpty List<@NotBlank String> requiredFields,
            Map<String, Object> template,
            String responsibleRole,
            @Min(0) Integer reminderDays,
            LedgerConfigurationStatus status,
            long version) {

        LedgerConfiguration toDomain(String id, long expectedVersion) {
            return new LedgerConfiguration(
                    id,
                    code,
                    name,
                    frequency,
                    periodDays,
                    requiredFields,
                    template,
                    responsibleRole,
                    reminderDays == null ? 0 : reminderDays,
                    status == null ? LedgerConfigurationStatus.ACTIVE : status,
                    expectedVersion,
                    Instant.EPOCH,
                    Instant.EPOCH);
        }
    }

    public record LedgerRecordRequest(
            String recordId,
            @NotBlank String ledgerCode,
            Instant recordTime,
            String recorderId,
            Map<String, Object> content,
            List<String> photos,
            String remark) {
    }
}
