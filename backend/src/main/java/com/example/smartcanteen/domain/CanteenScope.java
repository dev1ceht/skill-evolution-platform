package com.example.smartcanteen.domain;

import java.util.Objects;

/** School/canteen identity used to isolate operational business data. */
public record CanteenScope(String schoolId, String canteenId) {

    public static final CanteenScope DEFAULT =
            new CanteenScope("SCHOOL-001", "CANTEEN-001");

    public CanteenScope {
        requireIdentifier("schoolId", schoolId, 64);
        requireIdentifier("canteenId", canteenId, 64);
    }

    private static void requireIdentifier(String name, String value, int maxLength) {
        Objects.requireNonNull(value, name);
        if (value.isBlank() || value.length() > maxLength) {
            throw new IllegalArgumentException(
                    name + " must be non-blank and at most " + maxLength + " characters");
        }
    }
}
