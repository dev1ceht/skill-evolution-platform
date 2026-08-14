package com.example.smartcanteen.application;

import com.example.smartcanteen.application.port.LedgerConfigurationStore;
import com.example.smartcanteen.domain.CanteenScope;
import com.example.smartcanteen.domain.ConfiguredLedgerCycle;
import com.example.smartcanteen.domain.LedgerConfiguration;
import com.example.smartcanteen.domain.LedgerConfigurationStatus;
import com.example.smartcanteen.domain.LedgerFrequency;
import com.example.smartcanteen.domain.OperationalLedgerRecord;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Orchestrates configurable ledger rules without creating a second ledger fact model. */
@Service
public class ConfigurableLedgerService {

    private final LedgerConfigurationStore store;
    private final OperationalLedgerService ledgers;

    public ConfigurableLedgerService(
            LedgerConfigurationStore store,
            OperationalLedgerService ledgers) {
        this.store = store;
        this.ledgers = ledgers;
    }

    @Transactional(readOnly = true)
    public List<LedgerConfiguration> list(CanteenScope scope, boolean includeDisabled) {
        return store.list(scope, includeDisabled);
    }

    @Transactional
    public LedgerConfiguration create(CanteenScope scope, LedgerConfiguration configuration) {
        return store.create(scope, configuration);
    }

    @Transactional
    public LedgerConfiguration update(CanteenScope scope, LedgerConfiguration configuration) {
        return store.update(scope, configuration);
    }

    @Transactional
    public List<ConfiguredLedgerCycle> ensureCurrent(CanteenScope scope, LocalDate asOf) {
        LocalDate date = asOf == null ? LocalDate.now() : asOf;
        return store.list(scope, false).stream()
                .filter(configuration -> configuration.status() == LedgerConfigurationStatus.ACTIVE)
                .map(configuration -> {
                    Period period = periodFor(configuration, date);
                    return store.ensureCycle(scope, configuration, period.start(), period.end());
                })
                .toList();
    }

    @Transactional
    public OperationalLedgerRecord complete(
            CanteenScope scope,
            String cycleId,
            String ledgerCode,
            String recordId,
            Instant recordTime,
            String recorderId,
            Map<String, Object> content,
            List<String> photos,
            String remark) {
        ConfiguredLedgerCycle cycle = store.findCycle(scope, require(cycleId, "cycleId"))
                .orElseThrow(() -> new IllegalArgumentException("Unknown configured ledger cycle: " + cycleId));
        String normalizedCode = require(ledgerCode, "ledgerCode").toUpperCase(java.util.Locale.ROOT);
        if (!cycle.ledgerCode().equals(normalizedCode)) {
            throw new IllegalArgumentException("Ledger code is not configured for cycle: " + ledgerCode);
        }
        LedgerConfiguration configuration = store.findById(scope, cycle.configurationId())
                .orElseThrow(() -> new IllegalStateException(
                        "Ledger configuration is missing: " + cycle.configurationId()));
        Map<String, Object> normalizedContent = content == null ? Map.of() : content;
        for (String field : configuration.requiredFields()) {
            Object value = normalizedContent.get(field);
            if (value == null || (value instanceof String text && text.isBlank())) {
                throw new IllegalArgumentException("Required ledger field is missing: " + field);
            }
        }
        return ledgers.save(
                scope,
                recordId,
                cycle.cycleId(),
                cycle.ledgerCode(),
                recordTime,
                recorderId,
                normalizedContent,
                photos,
                remark);
    }

    private static Period periodFor(LedgerConfiguration configuration, LocalDate date) {
        LedgerFrequency frequency = Objects.requireNonNull(configuration.frequency());
        return switch (frequency) {
            case DAILY -> new Period(date, date);
            case WEEKLY -> {
                LocalDate start = date.minusDays(date.getDayOfWeek().getValue() - 1L);
                yield new Period(start, start.plusDays(6));
            }
            case MONTHLY -> {
                LocalDate start = date.withDayOfMonth(1);
                yield new Period(start, start.withDayOfMonth(start.lengthOfMonth()));
            }
            case CUSTOM -> {
                long daysSinceEpoch = ChronoUnit.DAYS.between(LocalDate.ofEpochDay(0), date);
                long offset = Math.floorMod(daysSinceEpoch, configuration.periodDays().longValue());
                LocalDate start = date.minusDays(offset);
                yield new Period(start, start.plusDays(configuration.periodDays() - 1L));
            }
        };
    }

    private static String require(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }

    private record Period(LocalDate start, LocalDate end) {
    }
}
