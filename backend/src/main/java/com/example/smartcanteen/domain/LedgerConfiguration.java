package com.example.smartcanteen.domain;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** A canteen-scoped rule from which executable ledger cycles are generated. */
public record LedgerConfiguration(
        String id,
        String code,
        String name,
        LedgerFrequency frequency,
        Integer periodDays,
        List<String> requiredFields,
        Map<String, Object> template,
        String responsibleRole,
        int reminderDays,
        LedgerConfigurationStatus status,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    public LedgerConfiguration {
        id = required("id", id, 64);
        code = required("code", code, 64).toUpperCase(java.util.Locale.ROOT);
        if (!code.matches("[A-Z][A-Z0-9_]*")) {
            throw new IllegalArgumentException("code must contain only uppercase letters, digits and underscores");
        }
        name = required("name", name, 200);
        frequency = Objects.requireNonNull(frequency, "frequency is required");
        if (frequency == LedgerFrequency.CUSTOM) {
            if (periodDays == null || periodDays < 1 || periodDays > 365) {
                throw new IllegalArgumentException("CUSTOM frequency periodDays must be between 1 and 365");
            }
        } else if (periodDays != null) {
            throw new IllegalArgumentException("periodDays is only allowed for CUSTOM frequency");
        }
        requiredFields = normalizeFields(requiredFields);
        template = template == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(template));
        responsibleRole = optional("responsibleRole", responsibleRole, 64);
        if (reminderDays < 0 || reminderDays > 365) {
            throw new IllegalArgumentException("reminderDays must be between 0 and 365");
        }
        status = Objects.requireNonNull(status, "status is required");
        if (version < 0) {
            throw new IllegalArgumentException("version cannot be negative");
        }
    }

    private static List<String> normalizeFields(List<String> fields) {
        if (fields == null || fields.isEmpty() || fields.size() > 50) {
            throw new IllegalArgumentException("requiredFields must contain between 1 and 50 fields");
        }
        return fields.stream()
                .map(field -> required("requiredField", field, 64))
                .distinct()
                .toList();
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
