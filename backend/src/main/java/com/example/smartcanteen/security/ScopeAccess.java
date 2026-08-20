package com.example.smartcanteen.security;

import com.example.smartcanteen.application.BusinessAuthorizationPolicy;
import com.example.smartcanteen.application.SingleCanteenContext;
import com.example.smartcanteen.domain.CanteenScope;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

@Component
public class ScopeAccess {

    private final BusinessAuthorizationPolicy policy;
    private final SingleCanteenContext canteen;

    public ScopeAccess(BusinessAuthorizationPolicy policy, SingleCanteenContext canteen) {
        this.policy = policy;
        this.canteen = canteen;
    }

    public CanteenScope require(
            HttpServletRequest request, String schoolId, String canteenId) {
        CanteenScope scope = canteen.resolve(schoolId, canteenId);
        Object value = request.getAttribute(AuthPrincipal.class.getName());
        AuthPrincipal principal = value instanceof AuthPrincipal current ? current : null;
        return policy.requireScope(
                principal,
                scope,
                !"GET".equalsIgnoreCase(request.getMethod()));
    }
}
