package com.example.smartcanteen.domain;

import java.time.Instant;

public record AuditLog(
        String auditId,
        String actorUserId,
        String action,
        String resourceType,
        String resourceId,
        String schoolId,
        String canteenId,
        String outcome,
        String detail,
        String requestId,
        Instant createdAt) {
}
