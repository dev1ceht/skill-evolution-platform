package com.example.smartcanteen.application;

import com.example.smartcanteen.application.port.AuthorizationStore;
import com.example.smartcanteen.application.port.AuditStore;
import com.example.smartcanteen.domain.CanteenScope;
import com.example.smartcanteen.domain.AuditLog;
import com.example.smartcanteen.domain.PermissionDefinition;
import com.example.smartcanteen.domain.RoleDefinition;
import com.example.smartcanteen.security.AuthPrincipal;
import com.example.smartcanteen.security.Role;
import java.util.Set;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthorizationService {

    private final AuthorizationStore store;
    private final AuditStore audits;

    public AuthorizationService(AuthorizationStore store, AuditStore audits) {
        this.store = store;
        this.audits = audits;
    }

    public List<RoleDefinition> listRoles() {
        return store.listRoles();
    }

    public List<PermissionDefinition> listPermissions() {
        return store.listPermissions();
    }

    @Transactional
    public RoleDefinition replaceRolePermissions(
            Role role, Set<String> permissionCodes, String actorUserId) {
        if (role == null) {
            throw new IllegalArgumentException("role is required");
        }
        store.replaceRolePermissions(role, permissionCodes == null ? Set.of() : permissionCodes);
        audits.append(new AuditLog(
                "AUDIT-" + UUID.randomUUID(), actorUserId, "PERMISSIONS", "ROLE", role.name(),
                null, null, "SUCCESS", "replaced role permissions", null, Instant.now()));
        return store.listRoles().stream()
                .filter(value -> value.code() == role)
                .findFirst()
                .orElseThrow();
    }

    public boolean canAccess(AuthPrincipal principal, CanteenScope scope) {
        if (principal == null || scope == null) {
            return false;
        }
        if (hasRole(principal, Role.SYSTEM_ADMIN)) {
            return true;
        }
        if (store.canAccessScope(principal.userId(), scope)) {
            return true;
        }
        if (hasRole(principal, Role.REGULATOR)) {
            return false;
        }
        if (store.scopeManagementEnabled(principal.userId())) {
            return false;
        }
        return scope.schoolId().equals(principal.schoolId())
                && (scope.canteenId().equals(principal.canteenId())
                        || (hasRole(principal, Role.SCHOOL_ADMIN)
                                && principal.canteenId() == null));
    }

    public boolean canAccessSchool(AuthPrincipal principal, String schoolId) {
        if (principal == null || schoolId == null || schoolId.isBlank()) {
            return false;
        }
        if (hasRole(principal, Role.SYSTEM_ADMIN)) {
            return true;
        }
        Set<String> allowed = store.allowedSchoolIds(principal.userId());
        if (allowed.contains(schoolId)) {
            return true;
        }
        if (store.scopeManagementEnabled(principal.userId())) {
            return false;
        }
        return !hasRole(principal, Role.REGULATOR)
                && schoolId.equals(principal.schoolId());
    }

    public boolean hasPermission(AuthPrincipal principal, String permissionCode) {
        if (principal == null || permissionCode == null || permissionCode.isBlank()) {
            return false;
        }
        if (hasRole(principal, Role.SYSTEM_ADMIN)) {
            return true;
        }
        return store.permissionsForRoles(rolesFor(principal)).contains(permissionCode);
    }

    public boolean hasRole(AuthPrincipal principal, Role role) {
        return principal != null && role != null && rolesFor(principal).contains(role);
    }

    public Set<Role> rolesFor(AuthPrincipal principal) {
        if (principal == null) {
            return Set.of();
        }
        Set<Role> persisted = store.rolesForUser(principal.userId());
        if (!persisted.isEmpty()) {
            return Set.copyOf(persisted);
        }
        return Set.copyOf(principal.roles());
    }

    public Set<String> permissionsFor(AuthPrincipal principal) {
        return store.permissionsForRoles(rolesFor(principal));
    }

    public Set<String> allowedSchoolIds(AuthPrincipal principal) {
        if (principal == null) {
            return Set.of();
        }
        if (hasRole(principal, Role.SYSTEM_ADMIN)) {
            return null;
        }
        Set<String> allowed = store.allowedSchoolIds(principal.userId());
        if (!allowed.isEmpty()) {
            return allowed;
        }
        if (store.scopeManagementEnabled(principal.userId())) {
            return Set.of();
        }
        if (!hasRole(principal, Role.REGULATOR) && principal.schoolId() != null) {
            return Set.of(principal.schoolId());
        }
        return Set.of();
    }

    public Set<String> allowedCanteenIds(AuthPrincipal principal, String schoolId) {
        if (principal == null) {
            return Set.of();
        }
        if (hasRole(principal, Role.SYSTEM_ADMIN)) {
            return null;
        }
        if (hasRole(principal, Role.SCHOOL_ADMIN)
                && principal.canteenId() == null
                && !store.scopeManagementEnabled(principal.userId())
                && (schoolId == null || schoolId.equals(principal.schoolId()))) {
            return null;
        }
        Set<String> allowed = store.allowedCanteenIds(principal.userId(), schoolId);
        if (!allowed.isEmpty()) {
            return allowed;
        }
        if (store.scopeManagementEnabled(principal.userId())) {
            return Set.of();
        }
        if (!hasRole(principal, Role.REGULATOR)
                && principal.canteenId() != null
                && (schoolId == null || schoolId.equals(principal.schoolId()))) {
            return Set.of(principal.canteenId());
        }
        return Set.of();
    }
}
