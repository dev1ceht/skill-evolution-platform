package com.example.smartcanteen.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/** Auditable canteen-scoped licence, certificate or governance document. */
public record ComplianceRecord(
        String id,
        ComplianceCategory category,
        String subjectType,
        String subjectId,
        String subjectName,
        String title,
        String credentialNo,
        LocalDate validFrom,
        LocalDate validTo,
        List<String> attachmentRefs,
        ComplianceRecordStatus status,
        String reviewRemark,
        long version,
        Instant createdAt,
        Instant updatedAt,
        Instant submittedAt,
        Instant reviewedAt,
        String reviewedBy) {

    public ComplianceRecord {
        id = required("id", id, 64);
        category = Objects.requireNonNull(category, "category is required");
        subjectType = required("subjectType", subjectType, 32)
                .toUpperCase(java.util.Locale.ROOT);
        subjectId = required("subjectId", subjectId, 64);
        subjectName = required("subjectName", subjectName, 200);
        title = required("title", title, 200);
        credentialNo = optional("credentialNo", credentialNo, 100);
        validFrom = Objects.requireNonNull(validFrom, "validFrom is required");
        validTo = Objects.requireNonNull(validTo, "validTo is required");
        if (validTo.isBefore(validFrom)) {
            throw new IllegalArgumentException("validTo cannot be before validFrom");
        }
        attachmentRefs = normalizeAttachments(attachmentRefs);
        status = Objects.requireNonNull(status, "status is required");
        reviewRemark = optional("reviewRemark", reviewRemark, 1000);
        if (version < 0) {
            throw new IllegalArgumentException("version cannot be negative");
        }
        reviewedBy = optional("reviewedBy", reviewedBy, 64);
    }

    private static List<String> normalizeAttachments(List<String> values) {
        if (values == null) {
            return List.of();
        }
        if (values.size() > 20) {
            throw new IllegalArgumentException("attachmentRefs must contain at most 20 references");
        }
        return values.stream().map(value -> required("attachmentRef", value, 500)).toList();
    }

    private static String required(String name, String value, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(name + " exceeds " + maxLength + " characters");
        }
        return normalized;
    }

    private static String optional(String name, String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return required(name, value, maxLength);
    }
}
