package com.example.smartcanteen.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.smartcanteen.application.port.LedgerConfigurationStore;
import com.example.smartcanteen.domain.CanteenScope;
import com.example.smartcanteen.domain.ConfiguredLedgerCycle;
import com.example.smartcanteen.domain.LedgerConfiguration;
import com.example.smartcanteen.domain.LedgerConfigurationStatus;
import com.example.smartcanteen.domain.LedgerFrequency;
import com.example.smartcanteen.domain.OperationalLedgerRecord;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class ConfigurableLedgerServiceTest {

    private static final CanteenScope SCOPE = new CanteenScope("SCHOOL-P3", "CANTEEN-P3");

    @Test
    void currentCycleIsGeneratedIdempotentlyAndKeepsOnlyMissingLedgerItems() {
        LedgerConfigurationStore store = mock(LedgerConfigurationStore.class);
        OperationalLedgerService ledgers = mock(OperationalLedgerService.class);
        ConfigurableLedgerService service = new ConfigurableLedgerService(store, ledgers);
        LedgerConfiguration config = configuration();
        ConfiguredLedgerCycle cycle = cycle(config, LocalDate.of(2026, 8, 17));

        when(store.list(SCOPE, false)).thenReturn(List.of(config));
        when(store.ensureCycle(eq(SCOPE), eq(config), eq(LocalDate.of(2026, 8, 17)),
                eq(LocalDate.of(2026, 8, 23)))).thenReturn(cycle);

        Assertions.assertThat(service.ensureCurrent(SCOPE, LocalDate.of(2026, 8, 18)))
                .containsExactly(cycle);
        Assertions.assertThat(service.ensureCurrent(SCOPE, LocalDate.of(2026, 8, 19)))
                .containsExactly(cycle);
        verify(store, times(2)).ensureCycle(eq(SCOPE), eq(config), eq(LocalDate.of(2026, 8, 17)),
                eq(LocalDate.of(2026, 8, 23)));
    }

    @Test
    void completionRequiresConfiguredFieldsAndDelegatesToExistingLedgerFact() {
        LedgerConfigurationStore store = mock(LedgerConfigurationStore.class);
        OperationalLedgerService ledgers = mock(OperationalLedgerService.class);
        ConfigurableLedgerService service = new ConfigurableLedgerService(store, ledgers);
        LedgerConfiguration config = configuration();
        ConfiguredLedgerCycle cycle = cycle(config, LocalDate.of(2026, 8, 17));
        when(store.findCycle(SCOPE, cycle.cycleId())).thenReturn(Optional.of(cycle));
        when(store.findById(SCOPE, config.id())).thenReturn(Optional.of(config));

        assertThatThrownBy(() -> service.complete(
                SCOPE, cycle.cycleId(), config.code(), null, null, null,
                Map.of("remark", "missing temperature"), List.of(), "bad"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("temperature");

        OperationalLedgerRecord persisted = new OperationalLedgerRecord(
                "LEDGER-P3", cycle.cycleId(), config.code(), Instant.EPOCH, "USER-P3",
                Map.of("temperature", 4.2), List.of(), "COMPLETED", null, Instant.EPOCH);
        when(ledgers.save(eq(SCOPE), eq("LEDGER-P3"), eq(cycle.cycleId()), eq(config.code()),
                any(), eq("USER-P3"), eq(Map.of("temperature", 4.2)), eq(List.of()), eq("ok")))
                .thenReturn(persisted);

        Assertions.assertThat(service.complete(
                SCOPE, cycle.cycleId(), config.code(), "LEDGER-P3", Instant.EPOCH, "USER-P3",
                Map.of("temperature", 4.2), List.of(), "ok"))
                .isEqualTo(persisted);
        verify(ledgers).save(eq(SCOPE), eq("LEDGER-P3"), eq(cycle.cycleId()), eq(config.code()),
                eq(Instant.EPOCH), eq("USER-P3"), eq(Map.of("temperature", 4.2)), eq(List.of()),
                eq("ok"));
    }

    private static LedgerConfiguration configuration() {
        return new LedgerConfiguration(
                "CONFIG-P3-TEMP", "TEMPERATURE_CHECK", "冷藏温度记录", LedgerFrequency.WEEKLY,
                null, List.of("temperature"), Map.of("unit", "℃"), "CANTEEN_STAFF", 1,
                LedgerConfigurationStatus.ACTIVE, 0, Instant.EPOCH, Instant.EPOCH);
    }

    private static ConfiguredLedgerCycle cycle(LedgerConfiguration config, LocalDate start) {
        return new ConfiguredLedgerCycle(
                "CYCLE-P3-TEMP", config.id(), config.code(), start, start.plusDays(6), "OPEN",
                List.of(config.code()));
    }
}
