package com.example.smartcanteen.http;

import com.example.smartcanteen.application.AuthorizationService;
import com.example.smartcanteen.application.OrganizationService;
import com.example.smartcanteen.application.SingleCanteenContext;
import com.example.smartcanteen.application.UserAdministrationService;
import com.example.smartcanteen.application.port.AuditStore;
import com.example.smartcanteen.domain.Canteen;
import com.example.smartcanteen.domain.CanteenScope;
import com.example.smartcanteen.domain.ManagedUser;
import com.example.smartcanteen.domain.PermissionDefinition;
import com.example.smartcanteen.domain.RoleDefinition;
import com.example.smartcanteen.domain.ScopeGrant;
import com.example.smartcanteen.domain.ScopeGrantType;
import com.example.smartcanteen.domain.School;
import com.example.smartcanteen.security.AuthPrincipal;
import com.example.smartcanteen.security.ForbiddenException;
import com.example.smartcanteen.security.Role;
import com.example.smartcanteen.security.RoleAccess;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
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
public class PlatformFoundationController {

    private final OrganizationService organization;
    private final UserAdministrationService users;
    private final AuthorizationService authorization;
    private final RoleAccess roles;
    private final AuditStore audits;
    private final SingleCanteenContext canteen;

    public PlatformFoundationController(
            OrganizationService organization,
            UserAdministrationService users,
            AuthorizationService authorization,
            RoleAccess roles,
            AuditStore audits,
            SingleCanteenContext canteen) {
        this.organization = organization;
        this.users = users;
        this.authorization = authorization;
        this.roles = roles;
        this.audits = audits;
        this.canteen = canteen;
    }

    @GetMapping("/schools")
    public ApiResponse<List<School>> schools(
            HttpServletRequest request,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "false") boolean includeInactive) {
        roles.requirePermission(request, "ORG_READ");
        Set<String> allowedSchoolIds = authorization.allowedSchoolIds(principal(request));
        if (canteen.isSingleCanteenMode()) {
            allowedSchoolIds = Set.of(canteen.scope().schoolId());
        }
        return ApiResponse.ok(organization.listSchools(
                allowedSchoolIds, keyword, includeInactive));
    }

    @PostMapping("/schools")
    public ApiResponse<School> createSchool(
            HttpServletRequest request, @Valid @RequestBody SchoolRequest body) {
        roles.requireAny(request, Role.SYSTEM_ADMIN);
        School school = new School(
                body.id() == null || body.id().isBlank() ? "SCHOOL-" + UUID.randomUUID() : body.id(),
                body.name(), body.regionCode(), body.active() == null || body.active());
        ensureSingleSchool(school.id());
        return ApiResponse.ok(organization.createSchool(school, principal(request).userId()));
    }

    @PutMapping("/schools/{schoolId}")
    public ApiResponse<School> updateSchool(
            HttpServletRequest request,
            @PathVariable String schoolId,
            @Valid @RequestBody SchoolRequest body) {
        roles.requirePermission(request, "ORG_WRITE");
        ensureSingleSchool(schoolId);
        requireSchoolAccess(request, schoolId);
        School current = organization.findSchool(schoolId)
                .orElseThrow(() -> new IllegalArgumentException("school not found: " + schoolId));
        School school = new School(
                schoolId,
                body.name() == null || body.name().isBlank() ? current.name() : body.name(),
                body.regionCode() == null || body.regionCode().isBlank()
                        ? current.regionCode() : body.regionCode(),
                body.active() == null ? current.active() : body.active());
        return ApiResponse.ok(organization.updateSchool(school, principal(request).userId()));
    }

    @PostMapping("/schools/{schoolId}/status")
    public ApiResponse<School> updateSchoolStatus(
            HttpServletRequest request,
            @PathVariable String schoolId,
            @Valid @RequestBody StatusRequest body) {
        roles.requirePermission(request, "ORG_WRITE");
        ensureSingleSchool(schoolId);
        requireSchoolAccess(request, schoolId);
        return ApiResponse.ok(organization.updateSchoolStatus(
                schoolId, body.active(), principal(request).userId()));
    }

    @GetMapping("/canteens")
    public ApiResponse<List<Canteen>> canteens(
            HttpServletRequest request,
            @RequestParam(required = false) String schoolId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "false") boolean includeInactive) {
        roles.requirePermission(request, "ORG_READ");
        if (canteen.isSingleCanteenMode()) {
            CanteenScope active = canteen.resolve(schoolId, null);
            schoolId = active.schoolId();
        }
        if (schoolId != null && !schoolId.isBlank()) {
            requireSchoolAccess(request, schoolId);
        }
        Set<String> allowedSchoolIds = authorization.allowedSchoolIds(principal(request));
        Set<String> allowedCanteenIds = authorization.allowedCanteenIds(principal(request), schoolId);
        if (canteen.isSingleCanteenMode()) {
            allowedSchoolIds = Set.of(canteen.scope().schoolId());
            allowedCanteenIds = Set.of(canteen.scope().canteenId());
        }
        return ApiResponse.ok(organization.listCanteens(
                allowedSchoolIds,
                allowedCanteenIds,
                schoolId, keyword, includeInactive));
    }

    @PostMapping("/canteens")
    public ApiResponse<Canteen> createCanteen(
            HttpServletRequest request, @Valid @RequestBody CanteenRequest body) {
        roles.requirePermission(request, "ORG_WRITE");
        ensureSingleSchool(body.schoolId());
        String canteenId = body.id() == null || body.id().isBlank()
                ? "CANTEEN-" + UUID.randomUUID() : body.id();
        ensureSingleCanteen(body.schoolId(), canteenId);
        requireSchoolAccess(request, body.schoolId());
        School school = organization.findSchool(body.schoolId())
                .orElseThrow(() -> new IllegalArgumentException("school not found: " + body.schoolId()));
        Canteen canteen = new Canteen(
                canteenId,
                body.schoolId(), body.name(), body.address(), school.regionCode(),
                body.active() == null || body.active());
        return ApiResponse.ok(organization.createCanteen(canteen, principal(request).userId()));
    }

    @PutMapping("/canteens/{canteenId}")
    public ApiResponse<Canteen> updateCanteen(
            HttpServletRequest request,
            @PathVariable String canteenId,
            @Valid @RequestBody CanteenRequest body) {
        roles.requirePermission(request, "ORG_WRITE");
        ensureSingleCanteen(null, canteenId);
        Canteen current = organization.findCanteen(canteenId)
                .orElseThrow(() -> new IllegalArgumentException("canteen not found: " + canteenId));
        requireSchoolAccess(request, current.schoolId());
        String schoolId = body.schoolId() == null || body.schoolId().isBlank()
                ? current.schoolId() : body.schoolId();
        ensureSingleSchool(schoolId);
        if (!schoolId.equals(current.schoolId())) {
            throw new IllegalArgumentException("canteen cannot move between schools");
        }
        Canteen canteen = new Canteen(
                canteenId,
                schoolId,
                body.name() == null || body.name().isBlank() ? current.name() : body.name(),
                body.address() == null ? current.address() : body.address(),
                current.regionCode(),
                body.active() == null ? current.active() : body.active());
        return ApiResponse.ok(organization.updateCanteen(canteen, principal(request).userId()));
    }

    @PostMapping("/canteens/{canteenId}/status")
    public ApiResponse<Canteen> updateCanteenStatus(
            HttpServletRequest request,
            @PathVariable String canteenId,
            @Valid @RequestBody StatusRequest body) {
        roles.requirePermission(request, "ORG_WRITE");
        ensureSingleCanteen(null, canteenId);
        Canteen current = organization.findCanteen(canteenId)
                .orElseThrow(() -> new IllegalArgumentException("canteen not found: " + canteenId));
        requireSchoolAccess(request, current.schoolId());
        return ApiResponse.ok(organization.updateCanteenStatus(
                canteenId, body.active(), principal(request).userId()));
    }

    @GetMapping("/roles")
    public ApiResponse<List<RoleDefinition>> roles(HttpServletRequest request) {
        roles.requirePermission(request, "ROLE_READ");
        return ApiResponse.ok(authorization.listRoles());
    }

    @GetMapping("/permissions")
    public ApiResponse<List<PermissionDefinition>> permissions(HttpServletRequest request) {
        roles.requirePermission(request, "ROLE_READ");
        return ApiResponse.ok(authorization.listPermissions());
    }

    @PutMapping("/roles/{roleCode}/permissions")
    public ApiResponse<RoleDefinition> replaceRolePermissions(
            HttpServletRequest request,
            @PathVariable String roleCode,
            @Valid @RequestBody RolePermissionRequest body) {
        roles.requireAny(request, Role.SYSTEM_ADMIN);
        Role role = parseRole(roleCode);
        return ApiResponse.ok(authorization.replaceRolePermissions(
                role, body.permissionCodes(), principal(request).userId()));
    }

    @GetMapping("/users")
    public ApiResponse<List<ManagedUserView>> users(
            HttpServletRequest request,
            @RequestParam(required = false) String schoolId,
            @RequestParam(required = false) String canteenId,
            @RequestParam(required = false) Boolean active) {
        roles.requirePermission(request, "USER_READ");
        if (canteen.isSingleCanteenMode()) {
            CanteenScope activeScope = canteen.resolve(schoolId, canteenId);
            schoolId = activeScope.schoolId();
            canteenId = activeScope.canteenId();
        }
        if (schoolId != null && !schoolId.isBlank()) {
            requireSchoolAccess(request, schoolId);
        }
        if (schoolId != null && canteenId != null) {
            requireCanteenAccess(request, schoolId, canteenId);
        }
        Set<String> allowed = authorization.allowedSchoolIds(principal(request));
        Set<String> allowedCanteens = authorization.allowedCanteenIds(principal(request), schoolId);
        List<ManagedUser> result = new ArrayList<>();
        if (allowed == null || schoolId != null) {
            result.addAll(users.listUsers(schoolId, canteenId, allowedCanteens, active));
        } else {
            for (String allowedSchoolId : allowed) {
                result.addAll(users.listUsers(allowedSchoolId, canteenId, allowedCanteens, active));
            }
        }
        return ApiResponse.ok(result.stream().map(ManagedUserView::from).toList());
    }

    @PostMapping("/users")
    public ApiResponse<ManagedUserView> createUser(
            HttpServletRequest request, @Valid @RequestBody CreateUserRequest body) {
        roles.requirePermission(request, "USER_WRITE");
        Set<Role> assignedRoles = parseRoles(body.roles());
        Role primaryRole = parseRole(body.primaryRole());
        if (assignedRoles.isEmpty()) {
            assignedRoles = Set.of(primaryRole);
        }
        if (assignedRoles.contains(Role.SYSTEM_ADMIN)
                && !authorization.hasRole(principal(request), Role.SYSTEM_ADMIN)) {
            throw new ForbiddenException("Only system administrators can assign SYSTEM_ADMIN");
        }
        requireOptionalSchoolAccess(request, body.schoolId());
        ensureSingleScope(body.schoolId(), body.canteenId());
        requireActiveSchool(body.schoolId());
        requireActiveCanteen(body.schoolId(), body.canteenId());
        List<ScopeGrant> grants = toGrants(principal(request).userId(), body.scopeGrants());
        ensureGrantAccess(request, grants);
        ManagedUser created = users.createUser(
                body.username(), body.password(), body.displayName(), primaryRole, assignedRoles,
                body.schoolId(), body.canteenId(), body.active() == null || body.active(), grants,
                principal(request).userId());
        return ApiResponse.ok(ManagedUserView.from(created));
    }

    @PostMapping("/users/{userId}/status")
    public ApiResponse<ManagedUserView> updateUserStatus(
            HttpServletRequest request,
            @PathVariable String userId,
            @Valid @RequestBody StatusRequest body) {
        roles.requirePermission(request, "USER_WRITE");
        requireTargetUserAccess(request, users.findUser(userId));
        ManagedUserView result = ManagedUserView.from(
                users.updateStatus(userId, body.active(), principal(request).userId()));
        return ApiResponse.ok(result);
    }

    @PutMapping("/users/{userId}/roles")
    public ApiResponse<ManagedUserView> replaceUserRoles(
            HttpServletRequest request,
            @PathVariable String userId,
            @Valid @RequestBody RoleAssignmentRequest body) {
        roles.requirePermission(request, "USER_WRITE");
        requireTargetUserAccess(request, users.findUser(userId));
        Set<Role> assignedRoles = parseRoles(body.roles());
        if (assignedRoles.contains(Role.SYSTEM_ADMIN)
                && !authorization.hasRole(principal(request), Role.SYSTEM_ADMIN)) {
            throw new ForbiddenException("Only system administrators can assign SYSTEM_ADMIN");
        }
        return ApiResponse.ok(ManagedUserView.from(users.replaceRoles(
                userId, assignedRoles, principal(request).userId())));
    }

    @PutMapping("/users/{userId}/scopes")
    public ApiResponse<ManagedUserView> replaceUserScopes(
            HttpServletRequest request,
            @PathVariable String userId,
            @Valid @RequestBody ScopeAssignmentRequest body) {
        roles.requirePermission(request, "USER_WRITE");
        requireTargetUserAccess(request, users.findUser(userId));
        List<ScopeGrant> grants = toGrants(userId, body.scopeGrants());
        ensureGrantAccess(request, grants);
        return ApiResponse.ok(ManagedUserView.from(users.replaceScopes(
                userId, grants, principal(request).userId())));
    }

    @PutMapping("/users/{userId}")
    public ApiResponse<ManagedUserView> updateUser(
            HttpServletRequest request,
            @PathVariable String userId,
            @Valid @RequestBody UpdateUserRequest body) {
        roles.requirePermission(request, "USER_WRITE");
        ManagedUser existing = users.findUser(userId);
        requireTargetUserAccess(request, existing);
        requireOptionalSchoolAccess(request, body.schoolId() == null ? existing.schoolId() : body.schoolId());
        String targetSchoolId = body.schoolId() == null ? existing.schoolId() : body.schoolId();
        if (body.schoolId() != null && !body.schoolId().isBlank()) {
            requireActiveSchool(body.schoolId());
        }
        String targetCanteenId = body.canteenId() == null ? existing.canteenId() : body.canteenId();
        if (body.schoolId() != null || body.canteenId() != null) {
            requireActiveCanteen(targetSchoolId, targetCanteenId);
            requireCanteenAccess(request, targetSchoolId, targetCanteenId);
        }
        Set<Role> assignedRoles = body.roles() == null
                ? null : parseRoles(body.roles());
        if (assignedRoles != null && assignedRoles.contains(Role.SYSTEM_ADMIN)
                && !authorization.hasRole(principal(request), Role.SYSTEM_ADMIN)) {
            throw new ForbiddenException("Only system administrators can assign SYSTEM_ADMIN");
        }
        Role primaryRole = body.primaryRole() == null
                ? null : parseRole(body.primaryRole());
        if (primaryRole == Role.SYSTEM_ADMIN
                && !authorization.hasRole(principal(request), Role.SYSTEM_ADMIN)) {
            throw new ForbiddenException("Only system administrators can assign SYSTEM_ADMIN");
        }
        List<ScopeGrant> grants = body.scopeGrants() == null
                ? null : toGrants(userId, body.scopeGrants());
        if (grants != null) {
            ensureGrantAccess(request, grants);
        }
        ManagedUserView result = ManagedUserView.from(users.updateUser(
                userId, body.displayName(), primaryRole, assignedRoles,
                body.schoolId(), body.canteenId(), body.active(), body.password(),
                principal(request).userId(), grants));
        return ApiResponse.ok(result);
    }

    @GetMapping("/audit-logs")
    public ApiResponse<List<com.example.smartcanteen.domain.AuditLog>> auditLogs(
            HttpServletRequest request,
            @RequestParam(defaultValue = "50") int limit) {
        roles.requirePermission(request, "AUDIT_READ");
        if (!authorization.hasRole(principal(request), Role.SYSTEM_ADMIN)) {
            throw new ForbiddenException("Only system administrators can query global audit logs");
        }
        return ApiResponse.ok(audits.listRecent(limit));
    }

    private void requireSchoolAccess(HttpServletRequest request, String schoolId) {
        if (!authorization.canAccessSchool(principal(request), schoolId)) {
            throw new ForbiddenException("User is outside the requested school scope");
        }
    }

    private void requireOptionalSchoolAccess(HttpServletRequest request, String schoolId) {
        if (schoolId != null && !schoolId.isBlank()) {
            ensureSingleSchool(schoolId);
            requireSchoolAccess(request, schoolId);
        } else if (!authorization.hasRole(principal(request), Role.SYSTEM_ADMIN)) {
            throw new IllegalArgumentException("schoolId is required for non-system users");
        }
    }

    private void requireTargetUserAccess(HttpServletRequest request, ManagedUser target) {
        ensureSingleScope(target.schoolId(), target.canteenId());
        if (authorization.hasRole(principal(request), Role.SYSTEM_ADMIN)) {
            return;
        }
        if (target.schoolId() == null
                || !authorization.canAccessSchool(principal(request), target.schoolId())) {
            throw new ForbiddenException("User is outside the target user's school scope");
        }
        if (target.canteenId() != null) {
            requireCanteenAccess(request, target.schoolId(), target.canteenId());
        }
    }

    private void requireActiveSchool(String schoolId) {
        if (schoolId == null || schoolId.isBlank()) {
            return;
        }
        ensureSingleSchool(schoolId);
        School school = organization.findSchool(schoolId)
                .orElseThrow(() -> new IllegalArgumentException("school not found: " + schoolId));
        if (!school.active()) {
            throw new IllegalArgumentException("school is disabled: " + schoolId);
        }
    }

    private void requireActiveCanteen(String schoolId, String canteenId) {
        if (canteenId == null || canteenId.isBlank()) {
            return;
        }
        ensureSingleScope(schoolId, canteenId);
        if (schoolId == null || schoolId.isBlank()) {
            throw new IllegalArgumentException("schoolId is required when canteenId is provided");
        }
        requireActiveSchool(schoolId);
        Canteen canteen = organization.findCanteen(canteenId)
                .orElseThrow(() -> new IllegalArgumentException("canteen not found: " + canteenId));
        if (!schoolId.equals(canteen.schoolId())) {
            throw new IllegalArgumentException("canteen does not belong to school: " + schoolId);
        }
        if (!canteen.active()) {
            throw new IllegalArgumentException("canteen is disabled: " + canteenId);
        }
    }

    private void requireCanteenAccess(HttpServletRequest request, String schoolId, String canteenId) {
        CanteenScope scope = canteen.resolve(schoolId, canteenId);
        if (!authorization.canAccess(principal(request), scope)) {
            throw new ForbiddenException("User is outside the requested canteen scope");
        }
    }

    private void ensureGrantAccess(HttpServletRequest request, List<ScopeGrant> grants) {
        boolean systemAdmin = authorization.hasRole(principal(request), Role.SYSTEM_ADMIN);
        for (ScopeGrant grant : grants) {
            if (grant.type() == ScopeGrantType.REGION) {
                if (canteen.isSingleCanteenMode()) {
                    throw new ForbiddenException("Region scope is not available in a single-canteen deployment");
                }
                if (!systemAdmin) {
                    throw new ForbiddenException("Only system administrators can assign region scope");
                }
                continue;
            }
            ensureSingleScope(grant.schoolId(), grant.canteenId());
            requireActiveSchool(grant.schoolId());
            if (!systemAdmin && (grant.schoolId() == null
                    || !authorization.canAccessSchool(principal(request), grant.schoolId()))) {
                throw new ForbiddenException("Cannot assign a scope outside your school");
            }
            if (grant.type() == ScopeGrantType.CANTEEN) {
                requireActiveCanteen(grant.schoolId(), grant.canteenId());
                requireCanteenAccess(request, grant.schoolId(), grant.canteenId());
            }
        }
    }

    private void ensureSingleSchool(String schoolId) {
        if (canteen.isSingleCanteenMode()) {
            canteen.resolve(schoolId, null);
        }
    }

    private void ensureSingleCanteen(String schoolId, String canteenId) {
        if (canteen.isSingleCanteenMode()) {
            canteen.resolve(schoolId, canteenId);
        }
    }

    private void ensureSingleScope(String schoolId, String canteenId) {
        ensureSingleCanteen(schoolId, canteenId);
    }

    private static AuthPrincipal principal(HttpServletRequest request) {
        Object value = request.getAttribute(AuthPrincipal.class.getName());
        if (!(value instanceof AuthPrincipal principal)) {
            throw new IllegalArgumentException("Authentication is required");
        }
        return principal;
    }

    private static Role parseRole(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("role is required");
        }
        try {
            return Role.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("unknown role: " + value, exception);
        }
    }

    private static Set<Role> parseRoles(List<String> values) {
        if (values == null) {
            return Set.of();
        }
        return values.stream().map(PlatformFoundationController::parseRole)
                .collect(Collectors.toCollection(() -> new LinkedHashSet<>()));
    }

    private static List<ScopeGrant> toGrants(String userId, List<ScopeGrantRequest> requests) {
        if (requests == null) {
            return List.of();
        }
        return requests.stream().map(value -> value.toDomain(userId)).toList();
    }

    public record SchoolRequest(
            String id,
            @NotBlank String name,
            @NotBlank String regionCode,
            Boolean active) {
    }

    public record CanteenRequest(
            String id,
            String schoolId,
            @NotBlank String name,
            String address,
            Boolean active) {
    }

    public record StatusRequest(@jakarta.validation.constraints.NotNull Boolean active) {
    }

    public record RolePermissionRequest(Set<String> permissionCodes) {
    }

    public record CreateUserRequest(
            @NotBlank String username,
            @NotBlank String password,
            @NotBlank String displayName,
            @NotBlank String primaryRole,
            List<String> roles,
            String schoolId,
            String canteenId,
            Boolean active,
            List<@Valid ScopeGrantRequest> scopeGrants) {
    }

    public record RoleAssignmentRequest(List<@NotBlank String> roles) {
    }

    public record UpdateUserRequest(
            String displayName,
            String primaryRole,
            List<String> roles,
            String schoolId,
            String canteenId,
            Boolean active,
            String password,
            List<@Valid ScopeGrantRequest> scopeGrants) {
    }

    public record ScopeAssignmentRequest(
            @jakarta.validation.constraints.NotNull List<@Valid ScopeGrantRequest> scopeGrants) {
    }

    public record ScopeGrantRequest(
            String assignmentId,
            @NotBlank String type,
            String regionCode,
            String schoolId,
            String canteenId) {

        ScopeGrant toDomain(String userId) {
            ScopeGrantType grantType;
            try {
                grantType = ScopeGrantType.valueOf(type.trim().toUpperCase());
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("unknown scope type: " + type, exception);
            }
            return new ScopeGrant(
                    assignmentId == null || assignmentId.isBlank()
                            ? "SCOPE-" + UUID.randomUUID() : assignmentId,
                    userId,
                    grantType,
                    regionCode,
                    schoolId,
                    canteenId);
        }
    }

    public record ManagedUserView(
            String userId,
            String username,
            String displayName,
            String primaryRole,
            Set<String> roles,
            String schoolId,
            String canteenId,
            boolean active,
            List<ScopeGrantView> scopeGrants) {

        static ManagedUserView from(ManagedUser user) {
            return new ManagedUserView(
                    user.userId(), user.username(), user.displayName(), user.primaryRole().name(),
                    user.roles().stream().map(Enum::name).collect(Collectors.toUnmodifiableSet()),
                    user.schoolId(), user.canteenId(), user.active(),
                    user.scopeGrants().stream().map(ScopeGrantView::from).toList());
        }
    }

    public record ScopeGrantView(
            String assignmentId,
            String userId,
            String type,
            String regionCode,
            String schoolId,
            String canteenId) {

        static ScopeGrantView from(ScopeGrant grant) {
            return new ScopeGrantView(
                    grant.assignmentId(), grant.userId(), grant.type().name(), grant.regionCode(),
                    grant.schoolId(), grant.canteenId());
        }
    }
}
