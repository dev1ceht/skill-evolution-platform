package com.example.smartcanteen.infrastructure;

import com.example.smartcanteen.application.port.LedgerConfigurationStore;
import com.example.smartcanteen.domain.CanteenScope;
import com.example.smartcanteen.domain.ConfiguredLedgerCycle;
import com.example.smartcanteen.domain.LedgerConfiguration;
import com.example.smartcanteen.domain.LedgerConfigurationStatus;
import com.example.smartcanteen.domain.LedgerFrequency;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcLedgerConfigurationStore implements LedgerConfigurationStore {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public JdbcLedgerConfigurationStore(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<LedgerConfiguration> list(CanteenScope scope, boolean includeDisabled) {
        String statusClause = includeDisabled ? "" : " AND status = 'ACTIVE'";
        return jdbc.query(
                "SELECT * FROM ledger_configurations WHERE school_id = ? AND canteen_id = ?"
                        + statusClause + " ORDER BY code",
                this::mapConfiguration,
                scope.schoolId(),
                scope.canteenId());
    }

    @Override
    public Optional<LedgerConfiguration> findById(CanteenScope scope, String configurationId) {
        return jdbc.query(
                        "SELECT * FROM ledger_configurations WHERE school_id = ? AND canteen_id = ?"
                                + " AND configuration_id = ?",
                        this::mapConfiguration,
                        scope.schoolId(),
                        scope.canteenId(),
                        configurationId)
                .stream()
                .findFirst();
    }

    @Override
    public Optional<LedgerConfiguration> findByCode(CanteenScope scope, String code) {
        return jdbc.query(
                        "SELECT * FROM ledger_configurations WHERE school_id = ? AND canteen_id = ?"
                                + " AND code = ?",
                        this::mapConfiguration,
                        scope.schoolId(),
                        scope.canteenId(),
                        code.trim().toUpperCase(java.util.Locale.ROOT))
                .stream()
                .findFirst();
    }

    @Override
    public LedgerConfiguration create(CanteenScope scope, LedgerConfiguration configuration) {
        Instant now = Instant.now();
        try {
            jdbc.update(
                    "INSERT INTO ledger_configurations (school_id, canteen_id, configuration_id, code, name,"
                            + " frequency, period_days, required_fields_json, template_json, responsible_role,"
                            + " reminder_days, status, version, created_at, updated_at)"
                            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?)",
                    scope.schoolId(),
                    scope.canteenId(),
                    configuration.id(),
                    configuration.code(),
                    configuration.name(),
                    configuration.frequency().name(),
                    configuration.periodDays(),
                    writeJson(configuration.requiredFields()),
                    writeJson(configuration.template()),
                    configuration.responsibleRole(),
                    configuration.reminderDays(),
                    configuration.status().name(),
                    Timestamp.from(now),
                    Timestamp.from(now));
        } catch (DuplicateKeyException exception) {
            throw new IllegalArgumentException(
                    "Ledger configuration id or code already exists: " + configuration.code(), exception);
        }
        return findById(scope, configuration.id())
                .orElseThrow(() -> new IllegalStateException("Ledger configuration was not persisted"));
    }

    @Override
    public LedgerConfiguration update(CanteenScope scope, LedgerConfiguration configuration) {
        LedgerConfiguration current = findById(scope, configuration.id())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Ledger configuration not found: " + configuration.id()));
        if (!current.code().equals(configuration.code())) {
            throw new IllegalArgumentException("Ledger configuration code cannot be changed");
        }
        int changed = jdbc.update(
                "UPDATE ledger_configurations SET name = ?, frequency = ?, period_days = ?,"
                        + " required_fields_json = ?, template_json = ?, responsible_role = ?,"
                        + " reminder_days = ?, status = ?, version = version + 1, updated_at = ?"
                        + " WHERE school_id = ? AND canteen_id = ? AND configuration_id = ? AND version = ?",
                configuration.name(),
                configuration.frequency().name(),
                configuration.periodDays(),
                writeJson(configuration.requiredFields()),
                writeJson(configuration.template()),
                configuration.responsibleRole(),
                configuration.reminderDays(),
                configuration.status().name(),
                Timestamp.from(Instant.now()),
                scope.schoolId(),
                scope.canteenId(),
                configuration.id(),
                configuration.version());
        if (changed != 1) {
            throw new IllegalArgumentException(
                    "Ledger configuration was changed concurrently: " + configuration.id());
        }
        return findById(scope, configuration.id())
                .orElseThrow(() -> new IllegalStateException("Ledger configuration disappeared"));
    }

    @Override
    public ConfiguredLedgerCycle ensureCycle(
            CanteenScope scope,
            LedgerConfiguration configuration,
            LocalDate periodStart,
            LocalDate periodEnd) {
        String cycleId = cycleId(configuration.id(), periodStart);
        Optional<CycleRow> existing = findCycleRow(scope, cycleId);
        if (existing.isPresent()) {
            verifyCycle(existing.get(), configuration, periodStart, periodEnd);
            return snapshot(scope, cycleId);
        }

        try {
            jdbc.update(
                    "INSERT INTO ledger_cycles (id, school_id, canteen_id, period_start, period_end,"
                            + " status, version, configuration_id) VALUES (?, ?, ?, ?, ?, 'OPEN', 0, ?)",
                    cycleId,
                    scope.schoolId(),
                    scope.canteenId(),
                    java.sql.Date.valueOf(periodStart),
                    java.sql.Date.valueOf(periodEnd),
                    configuration.id());
        } catch (DuplicateKeyException exception) {
            CycleRow concurrent = findCycleRow(scope, cycleId)
                    .orElseThrow(() -> exception);
            verifyCycle(concurrent, configuration, periodStart, periodEnd);
        }

        try {
            jdbc.update(
                    "INSERT INTO ledger_cycle_requirements (school_id, canteen_id, cycle_id, ledger_code, completed)"
                            + " VALUES (?, ?, ?, ?, FALSE)",
                    scope.schoolId(),
                    scope.canteenId(),
                    cycleId,
                    configuration.code());
        } catch (DuplicateKeyException ignored) {
            // The cycle winner already installed the requirement.
        }
        try {
            jdbc.update(
                    "INSERT INTO ledger_alerts (school_id, canteen_id, cycle_id, status, created_at)"
                            + " VALUES (?, ?, ?, 'OPEN', CURRENT_TIMESTAMP)",
                    scope.schoolId(),
                    scope.canteenId(),
                    cycleId);
        } catch (DuplicateKeyException ignored) {
            // The cycle winner already installed the alert row.
        }
        return snapshot(scope, cycleId);
    }

    @Override
    public Optional<ConfiguredLedgerCycle> findCycle(CanteenScope scope, String cycleId) {
        if (cycleId == null || cycleId.isBlank()) {
            return Optional.empty();
        }
        return jdbc.query(
                        "SELECT c.id, c.configuration_id, cfg.code, c.period_start, c.period_end, c.status"
                                + " FROM ledger_cycles c JOIN ledger_configurations cfg"
                                + " ON cfg.school_id = c.school_id AND cfg.canteen_id = c.canteen_id"
                                + " AND cfg.configuration_id = c.configuration_id"
                                + " WHERE c.school_id = ? AND c.canteen_id = ? AND c.id = ?",
                        this::mapCycle,
                        scope.schoolId(),
                        scope.canteenId(),
                        cycleId)
                .stream()
                .findFirst();
    }

    private ConfiguredLedgerCycle snapshot(CanteenScope scope, String cycleId) {
        ConfiguredLedgerCycle cycle = findCycle(scope, cycleId)
                .orElseThrow(() -> new IllegalStateException("Configured ledger cycle was not persisted"));
        List<String> missing = jdbc.queryForList(
                "SELECT ledger_code FROM ledger_cycle_requirements"
                        + " WHERE school_id = ? AND canteen_id = ? AND cycle_id = ? AND completed = FALSE"
                        + " ORDER BY ledger_code",
                String.class,
                scope.schoolId(),
                scope.canteenId(),
                cycleId);
        String status = missing.isEmpty() ? "CLEARED" : "OPEN";
        syncCycleState(scope, cycleId, status);
        return new ConfiguredLedgerCycle(
                cycle.cycleId(), cycle.configurationId(), cycle.ledgerCode(), cycle.periodStart(),
                cycle.periodEnd(), status, missing);
    }

    private void syncCycleState(CanteenScope scope, String cycleId, String status) {
        jdbc.update(
                "UPDATE ledger_cycles SET status = ? WHERE school_id = ? AND canteen_id = ? AND id = ?",
                status,
                scope.schoolId(),
                scope.canteenId(),
                cycleId);
        if (status.equals("CLEARED")) {
            jdbc.update(
                    "UPDATE ledger_alerts SET status = 'CLEARED', cleared_at = COALESCE(cleared_at, CURRENT_TIMESTAMP)"
                            + " WHERE school_id = ? AND canteen_id = ? AND cycle_id = ?",
                    scope.schoolId(),
                    scope.canteenId(),
                    cycleId);
        } else {
            jdbc.update(
                    "UPDATE ledger_alerts SET status = 'OPEN', cleared_at = NULL"
                            + " WHERE school_id = ? AND canteen_id = ? AND cycle_id = ?",
                    scope.schoolId(),
                    scope.canteenId(),
                    cycleId);
        }
    }

    private Optional<CycleRow> findCycleRow(CanteenScope scope, String cycleId) {
        return jdbc.query(
                        "SELECT id, configuration_id, period_start, period_end"
                                + " FROM ledger_cycles WHERE school_id = ? AND canteen_id = ? AND id = ?",
                        (result, row) -> new CycleRow(
                                result.getString("id"),
                                result.getString("configuration_id"),
                                result.getDate("period_start").toLocalDate(),
                                result.getDate("period_end").toLocalDate()),
                        scope.schoolId(),
                        scope.canteenId(),
                        cycleId)
                .stream()
                .findFirst();
    }

    private void verifyCycle(
            CycleRow cycle,
            LedgerConfiguration configuration,
            LocalDate periodStart,
            LocalDate periodEnd) {
        if (!configuration.id().equals(cycle.configurationId())
                || !periodStart.equals(cycle.periodStart())
                || !periodEnd.equals(cycle.periodEnd())) {
            throw new IllegalArgumentException(
                    "Ledger cycle already exists with a different configuration or period: " + cycle.id());
        }
    }

    private ConfiguredLedgerCycle mapCycle(ResultSet result, int row) throws SQLException {
        String status = result.getString("status");
        return new ConfiguredLedgerCycle(
                result.getString("id"),
                result.getString("configuration_id"),
                result.getString("code"),
                result.getDate("period_start").toLocalDate(),
                result.getDate("period_end").toLocalDate(),
                status,
                status.equals("CLEARED") ? List.of() : List.of(result.getString("code")));
    }

    private LedgerConfiguration mapConfiguration(ResultSet result, int row) throws SQLException {
        return new LedgerConfiguration(
                result.getString("configuration_id"),
                result.getString("code"),
                result.getString("name"),
                LedgerFrequency.valueOf(result.getString("frequency")),
                nullableInteger(result, "period_days"),
                readJson(result.getString("required_fields_json"), new TypeReference<>() {
                }),
                readJson(result.getString("template_json"), new TypeReference<>() {
                }),
                result.getString("responsible_role"),
                result.getInt("reminder_days"),
                LedgerConfigurationStatus.valueOf(result.getString("status")),
                result.getLong("version"),
                instant(result.getTimestamp("created_at")),
                instant(result.getTimestamp("updated_at")));
    }

    private static Integer nullableInteger(ResultSet result, String column) throws SQLException {
        int value = result.getInt(column);
        return result.wasNull() ? null : value;
    }

    private static Instant instant(Timestamp timestamp) {
        return timestamp == null ? Instant.EPOCH : timestamp.toInstant();
    }

    private <T> T readJson(String json, TypeReference<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Invalid ledger configuration JSON", exception);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Ledger configuration contains invalid JSON", exception);
        }
    }

    private static String cycleId(String configurationId, LocalDate periodStart) {
        String seed = configurationId + "|" + periodStart;
        return "CFG-" + UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8));
    }

    private record CycleRow(
            String id,
            String configurationId,
            LocalDate periodStart,
            LocalDate periodEnd) {
    }
}
