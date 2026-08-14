package com.example.smartcanteen.security;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

public record AuthPrincipal(
        String userId,
        String username,
        String displayName,
        Role role,
        String schoolId,
        String canteenId,
        Set<Role> roles) {

    public AuthPrincipal(
            String userId,
            String username,
            String displayName,
            Role role,
            String schoolId,
            String canteenId) {
        this(userId, username, displayName, role, schoolId, canteenId, Set.of(role));
    }

    public AuthPrincipal {
        EnumSet<Role> normalized = EnumSet.noneOf(Role.class);
        if (role != null) {
            normalized.add(role);
        }
        if (roles != null) {
            normalized.addAll(roles);
        }
        roles = Collections.unmodifiableSet(normalized);
    }

    public boolean canAccess(String requestedSchoolId, String requestedCanteenId) {
        if (hasRole(Role.SYSTEM_ADMIN)) {
            return true;
        }
        if (hasRole(Role.REGULATOR)) {
            return false;
        }
        return equalsNullable(schoolId, requestedSchoolId)
                && equalsNullable(canteenId, requestedCanteenId);
    }

    public boolean hasRole(Role candidate) {
        return candidate != null && roles.contains(candidate);
    }

    public boolean hasAnyRole(Role... candidates) {
        return Arrays.stream(candidates).anyMatch(this::hasRole);
    }

    private static boolean equalsNullable(String left, String right) {
        return left == null ? right == null : left.equals(right);
    }
}
