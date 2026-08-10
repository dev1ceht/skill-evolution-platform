package com.example.smartcanteen.domain;

public enum LedgerCode {
    PURCHASE_ACCEPTANCE;

    public static LedgerCode from(String value) {
        try {
            return LedgerCode.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unsupported ledger code: " + value, exception);
        }
    }
}
