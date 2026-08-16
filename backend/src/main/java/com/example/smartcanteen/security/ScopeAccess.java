package com.example.smartcanteen.security;

import com.example.smartcanteen.application.BusinessAuthorizationPolicy;
import com.example.smartcanteen.domain.CanteenScope;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

@Component
public class ScopeAccess {

    private final BusinessAuthorizationPolicy policy;

    public ScopeAccess(BusinessAuthorizationPolicy policy) {
        this.policy = policy;
    }

    public CanteenScope require(
            HttpServletRequest request, String schoolId, String canteenId) {
        CanteenScope scope = new CanteenScope(schoolId, canteenId);
        Object value = request.getAttribute(AuthPrincipal.class.getName());
        AuthPrincipal principal = value instanceof AuthPrincipal current ? current : null;
        return policy.requireScope(
                principal,
                scope,
                !"GET".equalsIgnoreCase(request.getMethod()));
    }
}
