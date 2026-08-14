package com.example.smartcanteen.security;

import com.example.smartcanteen.domain.CanteenScope;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ScopeAccess {

    private final boolean securityEnabled;

    public ScopeAccess(@Value("${smart-canteen.security.enabled:true}") boolean securityEnabled) {
        this.securityEnabled = securityEnabled;
    }

    public CanteenScope require(
            HttpServletRequest request, String schoolId, String canteenId) {
        CanteenScope scope = new CanteenScope(schoolId, canteenId);
        Object value = request.getAttribute(AuthPrincipal.class.getName());
        if (securityEnabled && value instanceof AuthPrincipal principal
                && !principal.canAccess(scope.schoolId(), scope.canteenId())) {
            throw new ForbiddenException("User is outside the requested school/canteen scope");
        }
        return scope;
    }
}
