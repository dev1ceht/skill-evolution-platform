package com.example.smartcanteen.domain;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Canonical short identifier for a daily menu. */
public final class MenuId {

    private static final Pattern EXACT = Pattern.compile("M(?:[0-9]{3}|[A-F0-9]{6})", Pattern.CASE_INSENSITIVE);
    private static final Pattern IN_TEXT = Pattern.compile(
            "(?i)(?<![A-Za-z0-9])M(?:[0-9]{3}|[A-F0-9]{6})(?![A-Za-z0-9])");

    private MenuId() {}

    public static String generate() {
        return "M" + UUID.randomUUID().toString().replace("-", "")
                .substring(0, 6).toUpperCase(Locale.ROOT);
    }

    public static String normalize(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("menuId is required");
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!EXACT.matcher(normalized).matches()) {
            throw new IllegalArgumentException(
                    "menuId must use short format M001 or MABC123");
        }
        return normalized;
    }

    public static String normalizeOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return normalize(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public static boolean isValid(String value) {
        return normalizeOrNull(value) != null;
    }

    public static Optional<String> findIn(String text) {
        Matcher matcher = IN_TEXT.matcher(text == null ? "" : text);
        return matcher.find()
                ? Optional.of(matcher.group().toUpperCase(Locale.ROOT))
                : Optional.empty();
    }
}
