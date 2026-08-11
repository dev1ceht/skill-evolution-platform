package com.example.smartcanteen.domain;

import java.util.Objects;

public record LedgerRecordCommand(LedgerScope scope, LedgerCode ledgerCode) {

    public LedgerRecordCommand {
        scope = Objects.requireNonNull(scope, "scope");
        ledgerCode = Objects.requireNonNull(ledgerCode, "ledgerCode");
    }
}
