package com.example.smartcanteen.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;

class LedgerAlertServiceTest {

    @Test
    void completing_the_last_missing_ledger_clears_the_alert() {
        LedgerAlertService alerts = new LedgerAlertService(Set.of(LedgerCode.PURCHASE_ACCEPTANCE));

        assertThat(alerts.current().cleared()).isFalse();
        alerts.complete(LedgerCode.PURCHASE_ACCEPTANCE);

        assertThat(alerts.current().cleared()).isTrue();
        assertThat(alerts.current().missingLedgerCodes()).isEmpty();
    }
}
