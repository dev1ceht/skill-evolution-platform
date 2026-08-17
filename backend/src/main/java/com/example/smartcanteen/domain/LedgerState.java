package com.example.smartcanteen.domain;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

public record LedgerState(LedgerScope scope, Set<LedgerCode> missingLedgerCodes) {

    public LedgerState {
        scope = Objects.requireNonNull(scope, "scope");
        missingLedgerCodes = missingLedgerCodes == null
                ? Set.of()
                : java.util.Collections.unmodifiableSet(new LinkedHashSet<>(missingLedgerCodes));
    }

    public boolean cleared() {
        return missingLedgerCodes.isEmpty();
    }
}
