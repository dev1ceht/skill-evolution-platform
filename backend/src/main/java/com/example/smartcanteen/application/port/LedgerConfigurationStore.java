package com.example.smartcanteen.application.port;

import com.example.smartcanteen.domain.CanteenScope;
import com.example.smartcanteen.domain.ConfiguredLedgerCycle;
import com.example.smartcanteen.domain.LedgerConfiguration;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Persistence seam for canteen-specific ledger rules and generated cycles. */
public interface LedgerConfigurationStore {

    List<LedgerConfiguration> list(CanteenScope scope, boolean includeDisabled);

    Optional<LedgerConfiguration> findById(CanteenScope scope, String configurationId);

    Optional<LedgerConfiguration> findByCode(CanteenScope scope, String code);

    LedgerConfiguration create(CanteenScope scope, LedgerConfiguration configuration);

    LedgerConfiguration update(CanteenScope scope, LedgerConfiguration configuration);

    ConfiguredLedgerCycle ensureCycle(
            CanteenScope scope,
            LedgerConfiguration configuration,
            LocalDate periodStart,
            LocalDate periodEnd);

    Optional<ConfiguredLedgerCycle> findCycle(CanteenScope scope, String cycleId);
}
