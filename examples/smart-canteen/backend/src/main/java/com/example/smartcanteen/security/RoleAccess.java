package com.example.smartcanteen.security;

import com.example.smartcanteen.application.AuthorizationService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Small request-boundary authorization policy for operational writes. */
@Component
public class RoleAccess {

    private final boolean securityEnabled;
    private final AuthorizationService authorization;

    public RoleAccess(
            @Value("${smart-canteen.security.enabled:true}") boolean securityEnabled,
            AuthorizationService authorization) {
        this.securityEnabled = securityEnabled;
        this.authorization = authorization;
    }

    public void requireAny(HttpServletRequest request, Role... allowedRoles) {
        if (!securityEnabled) {
            return;
        }
        Object value = request.getAttribute(AuthPrincipal.class.getName());
        if (!(value instanceof AuthPrincipal principal)) {
            throw new ForbiddenException("Authentication is required");
        }
        Set<Role> allowed = EnumSet.copyOf(Arrays.asList(allowedRoles));
        if (principal == null || authorization.rolesFor(principal).stream().noneMatch(allowed::contains)) {
            throw new ForbiddenException("User role is not allowed for this operation");
        }
    }

    public void requirePermission(HttpServletRequest request, String permissionCode) {
        if (!securityEnabled) {
            return;
        }
        Object value = request.getAttribute(AuthPrincipal.class.getName());
        if (!(value instanceof AuthPrincipal principal)) {
            throw new ForbiddenException("Authentication is required");
        }
        if (!authorization.hasPermission(principal, permissionCode)) {
            throw new ForbiddenException("User permission is not allowed for this operation");
        }
    }

    public void requireReader(HttpServletRequest request) {
        requireAny(
                request,
                Role.SYSTEM_ADMIN,
                Role.SCHOOL_ADMIN,
                Role.CANTEEN_STAFF,
                Role.REGULATOR);
    }
}
