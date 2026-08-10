package com.example.smartcanteen.domain;

import java.util.Set;

public final class LedgerAlertService {

    public LedgerAlert current(Set<LedgerCode> missingLedgerCodes) {
        Set<String> snapshot = missingLedgerCodes.stream()
                .map(LedgerCode::name)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        return new LedgerAlert(snapshot.isEmpty(), snapshot);
    }
}
