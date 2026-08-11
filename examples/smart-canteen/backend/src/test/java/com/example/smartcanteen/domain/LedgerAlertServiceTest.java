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

    @Test
    void alert_snapshot_keeps_school_canteen_and_cycle_scope() {
        LedgerAlertService alerts = new LedgerAlertService();
        LedgerScope scope = new LedgerScope("SCHOOL-002", "CANTEEN-002", "CYCLE-002");

        LedgerAlert result = alerts.current(
                new LedgerState(scope, Set.of(LedgerCode.SAMPLE_RETENTION)));

        assertThat(result.schoolId()).isEqualTo("SCHOOL-002");
        assertThat(result.canteenId()).isEqualTo("CANTEEN-002");
        assertThat(result.cycleId()).isEqualTo("CYCLE-002");
        assertThat(result.status()).isEqualTo("OPEN");
        assertThat(result.cleared()).isFalse();
        assertThat(result.missingLedgerCodes()).containsExactly("SAMPLE_RETENTION");
    }
}
