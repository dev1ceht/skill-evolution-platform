package com.example.smartcanteen.domain;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

public record LedgerCycleRequest(
        LedgerScope scope,
        Set<LedgerCode> ledgerCodes,
        LocalDate periodStart,
        LocalDate periodEnd) {

    public LedgerCycleRequest {
        scope = Objects.requireNonNull(scope, "scope");
        if (ledgerCodes == null || ledgerCodes.isEmpty()) {
            throw new IllegalArgumentException("At least one ledger code is required");
        }
        ledgerCodes = Set.copyOf(EnumSet.copyOf(ledgerCodes));
        periodStart = Objects.requireNonNull(periodStart, "periodStart");
        periodEnd = Objects.requireNonNull(periodEnd, "periodEnd");
        if (periodEnd.isBefore(periodStart)) {
            throw new IllegalArgumentException("periodEnd cannot be before periodStart");
        }
    }

    public LedgerCycleRequest(LedgerScope scope, Set<LedgerCode> ledgerCodes) {
        this(scope, ledgerCodes, LocalDate.now(), LocalDate.now());
    }
}
