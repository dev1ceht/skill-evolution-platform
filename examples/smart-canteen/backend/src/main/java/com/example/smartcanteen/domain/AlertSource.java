package com.example.smartcanteen.domain;

/** External or internal producer of a normalized alert-center record. */
public enum AlertSource {
    DISTRICT_PLATFORM,
    BRIGHT_KITCHEN,
    MORNING_INSPECTION,
    LEDGER;

    public static AlertSource from(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("source is required");
        }
        try {
            return valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unsupported alert source: " + value);
        }
    }
}
