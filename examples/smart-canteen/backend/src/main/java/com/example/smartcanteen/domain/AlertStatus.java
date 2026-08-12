package com.example.smartcanteen.domain;

public enum AlertStatus {
    UNPROCESSED,
    PROCESSED;

    public static AlertStatus from(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("status is required");
        }
        String normalized = value.trim();
        if ("1".equals(normalized) || "\u5df2\u5904\u7406".equals(normalized)
                || "PROCESSED".equalsIgnoreCase(normalized)) {
            return PROCESSED;
        }
        if ("0".equals(normalized) || "\u672a\u5904\u7406".equals(normalized)
                || "UNPROCESSED".equalsIgnoreCase(normalized)) {
            return UNPROCESSED;
        }
        try {
            return valueOf(normalized.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unsupported alert status: " + value);
        }
    }
}
