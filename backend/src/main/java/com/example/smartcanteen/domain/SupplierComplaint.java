package com.example.smartcanteen.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record SupplierComplaint(
        String id,
        String supplierId,
        String subject,
        String description,
        List<String> attachmentRefs,
        LocalDate deadline,
        SupplierComplaintStatus status,
        String reply,
        long version,
        String createdBy,
        String assignedTo,
        Instant createdAt,
        Instant updatedAt,
        Instant acceptedAt,
        Instant closedAt) {

    public SupplierComplaint {
        id = required("id", id, 64);
        supplierId = required("supplierId", supplierId, 64);
        subject = required("subject", subject, 200);
        description = required("description", description, 5000);
        attachmentRefs = normalizeAttachments(attachmentRefs);
        status = java.util.Objects.requireNonNull(status, "status is required");
        reply = optional("reply", reply, 3000);
        createdBy = required("createdBy", createdBy, 64);
        assignedTo = optional("assignedTo", assignedTo, 64);
        if (version < 0) {
            throw new IllegalArgumentException("version cannot be negative");
        }
    }

    private static List<String> normalizeAttachments(List<String> values) {
        if (values == null) {
            return List.of();
        }
        if (values.size() > 20) {
            throw new IllegalArgumentException("At most 20 complaint attachments are allowed");
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
