package com.example.smartcanteen.domain;

public enum LedgerCode {
    PERSONNEL_MORNING_CHECK,
    FACILITY_DISINFECTION,
    FOOD_PROCESSING,
    SAMPLE_RETENTION,
    ACCOMPANYING_MEAL_EVALUATION,
    PURCHASE_ACCEPTANCE;

    public static LedgerCode from(String value) {
        try {
            return LedgerCode.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unsupported ledger code: " + value, exception);
        }
    }
}
