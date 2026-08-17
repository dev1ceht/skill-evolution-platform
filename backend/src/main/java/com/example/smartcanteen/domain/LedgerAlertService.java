package com.example.smartcanteen.domain;

import java.util.Set;
import java.util.stream.Collectors;

public final class LedgerAlertService {

    public LedgerAlert current(Set<LedgerCode> missingLedgerCodes) {
        return current(
                new LedgerScope("SCHOOL-001", "CANTEEN-001", "CYCLE-001"),
                missingLedgerCodes);
    }

    public LedgerAlert current(LedgerState state) {
        return current(state.scope(), state.missingLedgerCodes());
    }

    public LedgerAlert current(LedgerScope scope, Set<LedgerCode> missingLedgerCodes) {
        Set<String> snapshot = missingLedgerCodes.stream()
                .map(LedgerCode::name)
                .sorted()
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
        return new LedgerAlert(
                scope.schoolId(),
                scope.canteenId(),
                scope.cycleId(),
                snapshot.isEmpty() ? "CLEARED" : "OPEN",
                snapshot);
    }
}
