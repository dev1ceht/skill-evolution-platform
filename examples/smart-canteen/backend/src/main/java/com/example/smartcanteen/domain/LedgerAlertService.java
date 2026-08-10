package com.example.smartcanteen.domain;

import java.util.HashSet;
import java.util.Set;

public final class LedgerAlertService {

    private final Set<LedgerCode> missingLedgerCodes;

    public LedgerAlertService(Set<LedgerCode> requiredLedgerCodes) {
        this.missingLedgerCodes = new HashSet<>(requiredLedgerCodes);
    }

    public synchronized LedgerAlert complete(LedgerCode ledgerCode) {
        missingLedgerCodes.remove(ledgerCode);
        return current();
    }

    public synchronized LedgerAlert current() {
        Set<String> snapshot = missingLedgerCodes.stream()
                .map(LedgerCode::name)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        return new LedgerAlert(snapshot.isEmpty(), snapshot);
    }
}
