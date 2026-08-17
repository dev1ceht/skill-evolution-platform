package com.example.smartcanteen.http;

import com.example.smartcanteen.agent.application.AgentMetricsService;
import com.example.smartcanteen.agent.domain.AgentMetrics;
import com.example.smartcanteen.agent.domain.ExecutionContext;
import com.example.smartcanteen.application.BusinessAuthorizationPolicy;
import com.example.smartcanteen.domain.CanteenScope;
import com.example.smartcanteen.security.AuthPrincipal;
import com.example.smartcanteen.security.ForbiddenException;
import com.example.smartcanteen.security.Role;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Scope-protected operational view over durable Agent Runtime evidence. */
@RestController
@RequestMapping("/api/v1/agent")
public class AgentMetricsController {

    private final AgentMetricsService metrics;
    private final BusinessAuthorizationPolicy policy;

    public AgentMetricsController(AgentMetricsService metrics, BusinessAuthorizationPolicy policy) {
        this.metrics = metrics;
        this.policy = policy;
    }

    @GetMapping("/metrics")
    public ApiResponse<AgentMetrics> metrics(
            HttpServletRequest request,
            @RequestParam String schoolId,
            @RequestParam String canteenId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        AuthPrincipal principal = principal(request);
        CanteenScope scope = new CanteenScope(schoolId, canteenId);
        String resolvedRequestId = resolvedRequestId(request, requestId);
        ExecutionContext context = policy.establishContext(
                principal, resolvedRequestId, scope, false);
        policy.requireAnyRole(principal, Role.SYSTEM_ADMIN, Role.SCHOOL_ADMIN, Role.REGULATOR);
        return ApiResponse.ok(metrics.collect(context.scope(), from, to));
    }

    private static AuthPrincipal principal(HttpServletRequest request) {
        Object value = request.getAttribute(AuthPrincipal.class.getName());
        if (value instanceof AuthPrincipal current) {
            return current;
        }
        throw new ForbiddenException("Authentication is required");
    }

    private static String resolvedRequestId(HttpServletRequest request, String requestId) {
        Object resolved = request.getAttribute(ExecutionContext.REQUEST_ID_ATTRIBUTE);
        if (resolved instanceof String value && !value.isBlank()) {
            return value;
        }
        String value = requestId;
        if (value == null || value.isBlank()) {
            value = UUID.randomUUID().toString();
        }
        request.setAttribute(ExecutionContext.REQUEST_ID_ATTRIBUTE, value);
        return value;
    }

}
