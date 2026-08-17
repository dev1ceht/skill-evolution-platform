package com.example.smartcanteen.application;

import com.example.smartcanteen.application.port.AuditStore;
import com.example.smartcanteen.application.port.AuthorizationStore;
import com.example.smartcanteen.domain.AuditLog;
import com.example.smartcanteen.domain.ManagedUser;
import com.example.smartcanteen.domain.ScopeGrant;
import com.example.smartcanteen.security.PasswordHasher;
import com.example.smartcanteen.security.Role;
import com.example.smartcanteen.security.UserAccount;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserAdministrationService {

    private final AuthorizationStore store;
    private final AuditStore audits;
    private final PasswordHasher passwords;

    public UserAdministrationService(
            AuthorizationStore store, AuditStore audits, PasswordHasher passwords) {
        this.store = store;
        this.audits = audits;
        this.passwords = passwords;
    }

    public List<ManagedUser> listUsers(
            String schoolId, String canteenId, Set<String> allowedCanteenIds, Boolean active) {
        return store.listUsers(schoolId, canteenId, allowedCanteenIds, active);
    }

    public ManagedUser findUser(String userId) {
        return store.findUser(userId)
                .orElseThrow(() -> new IllegalArgumentException("user not found: " + userId));
    }

    @Transactional
    public ManagedUser createUser(
            String username,
            String password,
            String displayName,
            Role primaryRole,
            Set<Role> roles,
            String schoolId,
            String canteenId,
            boolean active,
            List<ScopeGrant> scopeGrants,
            String actorUserId) {
        String userId = "USER-" + UUID.randomUUID();
        Set<Role> assignedRoles = roles == null || roles.isEmpty() ? Set.of(primaryRole) : Set.copyOf(roles);
        if (!assignedRoles.contains(primaryRole)) {
            throw new IllegalArgumentException("primary role must be included in roles");
        }
        UserAccount account = new UserAccount(
                userId, username, passwords.hash(password), displayName, primaryRole,
                schoolId, canteenId, active, assignedRoles);
        store.createUser(account);
        store.replaceScopeGrants(userId, normalizeGrants(userId, scopeGrants));
        audit(actorUserId, "CREATE", "USER", userId, schoolId, canteenId,
                "created user scopes=" + scopeSummary(scopeGrants));
        return store.findUser(userId).orElseThrow();
    }

    @Transactional
    public ManagedUser updateUser(
            String userId,
            String displayName,
            Role primaryRole,
            Set<Role> roles,
            String schoolId,
            String canteenId,
            Boolean active,
            String password,
            String actorUserId) {
        return updateUser(
                userId, displayName, primaryRole, roles, schoolId, canteenId, active, password,
                actorUserId, null);
    }

    @Transactional
    public ManagedUser updateUser(
            String userId,
            String displayName,
            Role primaryRole,
            Set<Role> roles,
            String schoolId,
            String canteenId,
            Boolean active,
            String password,
            String actorUserId,
            List<ScopeGrant> scopeGrants) {
        ManagedUser existing = store.findUser(userId)
                .orElseThrow(() -> new IllegalArgumentException("user not found: " + userId));
        Set<Role> assignedRoles = roles == null || roles.isEmpty() ? existing.roles() : Set.copyOf(roles);
        Role resolvedPrimary = primaryRole == null ? existing.primaryRole() : primaryRole;
        if (!assignedRoles.contains(resolvedPrimary)) {
            throw new IllegalArgumentException("primary role must be included in roles");
        }
        store.updateUser(
                userId,
                displayName == null || displayName.isBlank() ? existing.displayName() : displayName,
                resolvedPrimary,
                schoolId == null ? existing.schoolId() : schoolId,
                canteenId == null ? existing.canteenId() : canteenId,
                active == null ? existing.active() : active,
                password == null || password.isBlank() ? null : passwords.hash(password));
        store.replaceRoles(userId, assignedRoles);
        if (scopeGrants != null) {
            store.replaceScopeGrants(userId, normalizeGrants(userId, scopeGrants));
        }
        ManagedUser updated = store.findUser(userId).orElseThrow();
        audit(actorUserId, "UPDATE", "USER", userId, updated.schoolId(), updated.canteenId(),
                "updated user scopes=" + scopeSummary(
                        scopeGrants == null ? existing.scopeGrants() : scopeGrants));
        return updated;
    }

    @Transactional
    public ManagedUser updateStatus(String userId, boolean active, String actorUserId) {
        ManagedUser existing = store.findUser(userId)
                .orElseThrow(() -> new IllegalArgumentException("user not found: " + userId));
        store.updateUser(
                userId, existing.displayName(), existing.primaryRole(),
                existing.schoolId(), existing.canteenId(), active, null);
        ManagedUser updated = store.findUser(userId).orElseThrow();
        audit(actorUserId, "STATUS", "USER", userId, updated.schoolId(), updated.canteenId(),
                active ? "enabled user" : "disabled user");
        return updated;
    }

    @Transactional
    public ManagedUser replaceRoles(String userId, Set<Role> roles, String actorUserId) {
        ManagedUser existing = store.findUser(userId)
                .orElseThrow(() -> new IllegalArgumentException("user not found: " + userId));
        if (roles == null || roles.isEmpty()) {
            throw new IllegalArgumentException("at least one role is required");
        }
        Role primary = roles.contains(existing.primaryRole())
                ? existing.primaryRole()
                : roles.iterator().next();
        store.replaceRoles(userId, roles);
        store.updateUser(
                userId, existing.displayName(), primary, existing.schoolId(), existing.canteenId(),
                existing.active(), null);
        ManagedUser updated = store.findUser(userId).orElseThrow();
        audit(actorUserId, "ROLES", "USER", userId, updated.schoolId(), updated.canteenId(), "replaced roles");
        return updated;
    }

    @Transactional
    public ManagedUser replaceScopes(String userId, List<ScopeGrant> grants, String actorUserId) {
        ManagedUser existing = store.findUser(userId)
                .orElseThrow(() -> new IllegalArgumentException("user not found: " + userId));
        if (grants != null) {
            for (ScopeGrant grant : grants) {
                if (!userId.equals(grant.userId())) {
                    throw new IllegalArgumentException("scope grant userId does not match target user");
                }
            }
        }
        store.replaceScopeGrants(userId, grants == null ? List.of() : grants);
        ManagedUser updated = store.findUser(userId).orElseThrow();
        audit(actorUserId, "SCOPES", "USER", userId, updated.schoolId(), updated.canteenId(),
                "replaced scope grants: " + scopeSummary(grants));
        return updated;
    }

    private void audit(
            String actorUserId,
            String action,
            String resourceType,
            String resourceId,
            String schoolId,
            String canteenId,
            String detail) {
        audits.append(new AuditLog(
                "AUDIT-" + UUID.randomUUID(), actorUserId, action, resourceType, resourceId,
                schoolId, canteenId, "SUCCESS", detail, null, Instant.now()));
    }

    private static List<ScopeGrant> normalizeGrants(String userId, List<ScopeGrant> grants) {
        if (grants == null) {
            return List.of();
        }
        return grants.stream()
                .map(grant -> new ScopeGrant(
                        grant.assignmentId(), userId, grant.type(), grant.regionCode(),
                        grant.schoolId(), grant.canteenId()))
                .toList();
    }

    private static String scopeSummary(List<ScopeGrant> grants) {
        return (grants == null ? List.<ScopeGrant>of() : grants).stream()
                .map(grant -> grant.type().name()
                        + "[region=" + grant.regionCode()
                        + ",school=" + grant.schoolId()
                        + ",canteen=" + grant.canteenId() + "]")
                .toList()
                .toString();
    }
}
