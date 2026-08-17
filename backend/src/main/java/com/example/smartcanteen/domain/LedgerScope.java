package com.example.smartcanteen.domain;

import java.util.Objects;

public record LedgerScope(String schoolId, String canteenId, String cycleId) {

    public LedgerScope {
        requireIdentifier("schoolId", schoolId, 64);
        requireIdentifier("canteenId", canteenId, 64);
        requireIdentifier("cycleId", cycleId, 64);
    }

    private static void requireIdentifier(String name, String value, int maxLength) {
        Objects.requireNonNull(value, name);
        if (value.isBlank() || value.length() > maxLength) {
            throw new IllegalArgumentException(name + " must be non-blank and at most " + maxLength + " characters");
        }
    }
}
