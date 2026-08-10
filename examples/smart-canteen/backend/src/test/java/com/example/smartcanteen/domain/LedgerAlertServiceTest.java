package com.example.smartcanteen.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;

class LedgerAlertServiceTest {

    @Test
    void completing_the_last_missing_ledger_clears_the_alert() {
        LedgerAlertService alerts = new LedgerAlertService();

        assertThat(alerts.current(Set.of(LedgerCode.PURCHASE_ACCEPTANCE)).cleared()).isFalse();

        assertThat(alerts.current(Set.of()).cleared()).isTrue();
        assertThat(alerts.current(Set.of()).missingLedgerCodes()).isEmpty();
    }
}
