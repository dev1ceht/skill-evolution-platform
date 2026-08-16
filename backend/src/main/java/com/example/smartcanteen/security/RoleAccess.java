package com.example.smartcanteen.security;

import com.example.smartcanteen.application.BusinessAuthorizationPolicy;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/** Small request-boundary authorization policy for operational writes. */
@Component
public class RoleAccess {

    private final BusinessAuthorizationPolicy policy;

    public RoleAccess(BusinessAuthorizationPolicy policy) {
        this.policy = policy;
    }

    public void requireAny(HttpServletRequest request, Role... allowedRoles) {
        Object value = request.getAttribute(AuthPrincipal.class.getName());
        AuthPrincipal principal = value instanceof AuthPrincipal current ? current : null;
        policy.requireAnyRole(principal, allowedRoles);
    }

    public void requirePermission(HttpServletRequest request, String permissionCode) {
        Object value = request.getAttribute(AuthPrincipal.class.getName());
        AuthPrincipal principal = value instanceof AuthPrincipal current ? current : null;
        policy.requirePermission(principal, permissionCode);
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
