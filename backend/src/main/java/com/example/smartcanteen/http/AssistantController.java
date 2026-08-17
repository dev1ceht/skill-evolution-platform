package com.example.smartcanteen.http;

import com.example.smartcanteen.agent.domain.ExecutionContext;
import com.example.smartcanteen.application.BusinessAuthorizationPolicy;
import com.example.smartcanteen.assistant.application.AssistantConversationService;
import com.example.smartcanteen.assistant.domain.AssistantConversationHistory;
import com.example.smartcanteen.assistant.domain.AssistantTurn;
import com.example.smartcanteen.domain.CanteenScope;
import com.example.smartcanteen.security.AuthPrincipal;
import com.example.smartcanteen.security.ForbiddenException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** HTTP Adapter for the first natural-language, read-only assistant slice. */
@RestController
@RequestMapping("/api/v1/assistant")
public class AssistantController {

    private final AssistantConversationService conversations;
    private final BusinessAuthorizationPolicy policy;
    private final boolean enabled;

    public AssistantController(
            AssistantConversationService conversations,
            BusinessAuthorizationPolicy policy,
            @Value("${smart-canteen.assistant.enabled:true}") boolean enabled) {
        this.conversations = conversations;
        this.policy = policy;
        this.enabled = enabled;
    }

    @PostMapping("/conversations/{conversationId}/messages")
    public ApiResponse<AssistantTurn> message(
            HttpServletRequest request,
            @PathVariable String conversationId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @RequestParam String schoolId,
            @RequestParam String canteenId,
            @Valid @RequestBody MessageRequest body) {
        if (!enabled) {
            throw new ForbiddenException("Assistant pilot is disabled");
        }
        AuthPrincipal principal = principal(request);
        String resolvedRequestId = requestId == null || requestId.isBlank()
                ? UUID.randomUUID().toString()
                : requestId;
        CanteenScope scope = new CanteenScope(schoolId, canteenId);
        // The assistant is read-only with respect to business data, but this POST persists a
        // conversation turn; use write scope checks so disabled canteens cannot create records.
        ExecutionContext context = policy.establishContext(
                principal,
                resolvedRequestId,
                scope,
                true);
        return ApiResponse.ok(conversations.handle(
                conversationId,
                body.message(),
                idempotencyKey,
                context,
                principal));
    }

    @GetMapping("/conversations/{conversationId}/messages")
    public ApiResponse<AssistantConversationHistory> history(
            HttpServletRequest request,
            @PathVariable String conversationId,
            @RequestParam String schoolId,
            @RequestParam String canteenId,
            @RequestParam(defaultValue = "50") int limit,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        if (!enabled) {
            throw new ForbiddenException("Assistant pilot is disabled");
        }
        AuthPrincipal principal = principal(request);
        String resolvedRequestId = requestId == null || requestId.isBlank()
                ? UUID.randomUUID().toString()
                : requestId;
        ExecutionContext context = policy.establishContext(
                principal,
                resolvedRequestId,
                new CanteenScope(schoolId, canteenId),
                false);
        return ApiResponse.ok(conversations.history(conversationId, context, limit));
    }

    private static AuthPrincipal principal(HttpServletRequest request) {
        Object value = request.getAttribute(AuthPrincipal.class.getName());
        if (value instanceof AuthPrincipal current) {
            return current;
        }
        throw new ForbiddenException("Authentication is required");
    }

    public record MessageRequest(
            @NotBlank @Size(max = 2000) String message) {
    }
}
