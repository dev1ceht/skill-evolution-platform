package com.example.smartcanteen.domain;

import java.time.Instant;

/** Disposal result reported by an alert-center operator or an external system. */
public record AlertDisposal(
        int processStatus,
        Instant processTime,
        String processUser,
        String processContent,
        String processFile) {

    public AlertDisposal {
        if (processStatus != 0 && processStatus != 1) {
            throw new IllegalArgumentException("processStatus must be 0 or 1");
        }
        if (processStatus == 1) {
            if (processTime == null) {
                throw new IllegalArgumentException("processTime is required when processed");
            }
            if (processUser == null || processUser.isBlank()) {
                throw new IllegalArgumentException("processUser is required when processed");
            }
        }
        processUser = normalizeOptional("processUser", processUser, 128);
        processContent = normalizeOptional("processContent", processContent, 2000);
        processFile = normalizeOptional("processFile", processFile, 500);
    }

    private static String normalizeOptional(String name, String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(name + " exceeds " + maxLength + " characters");
        }
        return normalized;
    }
}
