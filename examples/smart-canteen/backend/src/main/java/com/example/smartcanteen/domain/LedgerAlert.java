package com.example.smartcanteen.domain;

import java.util.Set;

public record LedgerAlert(boolean cleared, Set<String> missingLedgerCodes) {
}
