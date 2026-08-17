package com.example.smartcanteen.agent.infrastructure;

import com.example.smartcanteen.agent.application.AgentMetricsRecorder;
import com.example.smartcanteen.agent.domain.ExecutionContext;
import com.example.smartcanteen.application.port.AuditStore;
import com.example.smartcanteen.domain.AuditLog;
import com.example.smartcanteen.security.AuthPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Records bounded Agent authorization-denial evidence without storing request bodies. */
@Component
public class AgentMetricsFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AgentMetricsFilter.class);
    private final AgentMetricsRecorder metrics;
    private final AuditStore audits;

    public AgentMetricsFilter(AgentMetricsRecorder metrics, AuditStore audits) {
        this.metrics = metrics;
        this.audits = audits;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        request.setAttribute(ExecutionContext.REQUEST_ID_ATTRIBUTE, resolveRequestId(request));
        filterChain.doFilter(request, response);
        if (response.getStatus() != HttpServletResponse.SC_FORBIDDEN
                || !request.getRequestURI().startsWith("/api/v1/agent/")) {
            return;
        }
        metrics.recordAuthorizationDenied();
        appendAudit(request);
    }

    private void appendAudit(HttpServletRequest request) {
        String schoolId = request.getParameter("schoolId");
        String canteenId = request.getParameter("canteenId");
        AuthPrincipal principal = (AuthPrincipal) request.getAttribute(AuthPrincipal.class.getName());
        String resolvedActor = principal == null ? null : principal.userId();
        AuditLog audit = new AuditLog(
                "AUDIT-AGENT-AUTH-" + UUID.randomUUID(),
                resolvedActor,
                "AGENT_AUTHORIZATION_DENIAL",
                "AGENT_HTTP",
                request.getRequestURI(),
                schoolId,
                canteenId,
                "FAILURE",
                "agent authorization denied",
                (String) request.getAttribute(ExecutionContext.REQUEST_ID_ATTRIBUTE),
                Instant.now());
        try {
            audits.append(audit);
        } catch (RuntimeException exception) {
            if (resolvedActor != null) {
                try {
                    audits.append(new AuditLog(
                            audit.auditId() + "-ANON",
                            null,
                            audit.action(),
                            audit.resourceType(),
                            audit.resourceId(),
                            audit.schoolId(),
                            audit.canteenId(),
                            audit.outcome(),
                            audit.detail(),
                            audit.requestId(),
                            audit.createdAt()));
                    return;
                } catch (RuntimeException ignored) {
                    // Keep the process-local counter even when the audit table is unavailable.
                }
            }
            log.warn("Agent authorization denial audit write failed path={}", request.getRequestURI());
        }
    }

    private static String resolveRequestId(HttpServletRequest request) {
        String requestId = request.getHeader("X-Request-Id");
        return requestId == null || requestId.isBlank()
                ? UUID.randomUUID().toString()
                : requestId;
    }
}
