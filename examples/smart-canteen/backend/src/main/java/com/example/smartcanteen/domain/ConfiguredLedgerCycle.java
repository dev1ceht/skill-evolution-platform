package com.example.smartcanteen.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/** Durable execution snapshot of one ledger configuration for one period. */
public record ConfiguredLedgerCycle(
        String cycleId,
        String configurationId,
        String ledgerCode,
        LocalDate periodStart,
        LocalDate periodEnd,
        String status,
        List<String> missingLedgerCodes) {

    public ConfiguredLedgerCycle {
        cycleId = required("cycleId", cycleId);
        configurationId = required("configurationId", configurationId);
        ledgerCode = required("ledgerCode", ledgerCode);
        periodStart = Objects.requireNonNull(periodStart, "periodStart is required");
        periodEnd = Objects.requireNonNull(periodEnd, "periodEnd is required");
        if (periodEnd.isBefore(periodStart)) {
            throw new IllegalArgumentException("periodEnd cannot be before periodStart");
        }
        status = required("status", status).toUpperCase(java.util.Locale.ROOT);
        if (!status.equals("OPEN") && !status.equals("CLEARED")) {
            throw new IllegalArgumentException("Unsupported ledger cycle status: " + status);
        }
        missingLedgerCodes = missingLedgerCodes == null ? List.of() : List.copyOf(missingLedgerCodes);
        if (status.equals("CLEARED") != missingLedgerCodes.isEmpty()) {
            throw new IllegalArgumentException("Ledger cycle status must match missing ledger codes");
        }
    }

    public boolean cleared() {
        return status.equals("CLEARED");
    }

    private static String required(String name, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }
}
