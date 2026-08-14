package com.example.smartcanteen.security;

import com.example.smartcanteen.application.AuthorizationService;
import com.example.smartcanteen.application.OrganizationService;
import com.example.smartcanteen.domain.CanteenScope;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ScopeAccess {

    private final boolean securityEnabled;
    private final AuthorizationService authorization;
    private final OrganizationService organization;

    public ScopeAccess(
            @Value("${smart-canteen.security.enabled:true}") boolean securityEnabled,
            AuthorizationService authorization,
            OrganizationService organization) {
        this.securityEnabled = securityEnabled;
        this.authorization = authorization;
        this.organization = organization;
    }

    public CanteenScope require(
            HttpServletRequest request, String schoolId, String canteenId) {
        CanteenScope scope = new CanteenScope(schoolId, canteenId);
        Object value = request.getAttribute(AuthPrincipal.class.getName());
        if (securityEnabled && value instanceof AuthPrincipal principal) {
            if (!organization.isKnownScope(scope)) {
                throw new ForbiddenException("The requested school/canteen scope is not registered");
            }
            if (!authorization.canAccess(principal, scope)) {
                throw new ForbiddenException("User is outside the requested school/canteen scope");
            }
            if (!"GET".equalsIgnoreCase(request.getMethod())
                    && !organization.isActiveScope(scope)) {
                throw new ForbiddenException("The requested school/canteen scope is disabled");
            }
        }
        return scope;
    }
}
