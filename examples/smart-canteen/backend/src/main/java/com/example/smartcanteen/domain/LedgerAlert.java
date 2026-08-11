package com.example.smartcanteen.domain;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

public record LedgerAlert(
        String schoolId,
        String canteenId,
        String cycleId,
        String status,
        Set<String> missingLedgerCodes) {

    public LedgerAlert {
        schoolId = Objects.requireNonNull(schoolId, "schoolId");
        canteenId = Objects.requireNonNull(canteenId, "canteenId");
        cycleId = Objects.requireNonNull(cycleId, "cycleId");
        status = Objects.requireNonNull(status, "status");
        if (!status.equals("OPEN") && !status.equals("CLEARED")) {
            throw new IllegalArgumentException("Unsupported ledger alert status: " + status);
        }
        Set<String> snapshot = missingLedgerCodes == null
                ? Set.of()
                : new LinkedHashSet<>(missingLedgerCodes);
        if (status.equals("CLEARED") != snapshot.isEmpty()) {
            throw new IllegalArgumentException(
                    "Ledger alert status must match missing ledger codes");
        }
        missingLedgerCodes = java.util.Collections.unmodifiableSet(snapshot);
    }

    public LedgerAlert(boolean cleared, Set<String> missingLedgerCodes) {
        // Compatibility constructor for the original unscoped API response.
        this(
                "SCHOOL-001",
                "CANTEEN-001",
                "CYCLE-001",
                cleared ? "CLEARED" : "OPEN",
                missingLedgerCodes);
    }

    public boolean cleared() {
        return status.equals("CLEARED");
    }

    /** Jackson-friendly boolean property retained for the existing API envelope. */
    public boolean isCleared() {
        return cleared();
    }
}
