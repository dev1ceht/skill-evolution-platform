package com.example.smartcanteen.infrastructure;

import com.example.smartcanteen.application.port.LedgerStore;
import com.example.smartcanteen.domain.LedgerCode;
import com.example.smartcanteen.domain.LedgerCycleRequest;
import com.example.smartcanteen.domain.LedgerRecordCommand;
import com.example.smartcanteen.domain.LedgerScope;
import com.example.smartcanteen.domain.LedgerState;
import java.sql.Date;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcLedgerStore implements LedgerStore {

    private final JdbcTemplate jdbc;

    public JdbcLedgerStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public LedgerState startCycle(LedgerCycleRequest request) {
        ensureSchool(request.scope().schoolId());
        ensureCanteen(request.scope().schoolId(), request.scope().canteenId());

        Optional<CycleDefinition> existingCycle = findCycle(request.scope());
        if (existingCycle.isPresent()) {
            return existingCycle(request, existingCycle.get());
        }

        try {
            jdbc.update(
                    """
                    INSERT INTO ledger_cycles (
                        id, school_id, canteen_id, period_start, period_end, status, version
                    ) VALUES (?, ?, ?, ?, ?, 'OPEN', 0)
                    """,
                    request.scope().cycleId(),
                    request.scope().schoolId(),
                    request.scope().canteenId(),
                    Date.valueOf(request.periodStart()),
                    Date.valueOf(request.periodEnd()));
        } catch (DuplicateKeyException duplicate) {
            // Two identical starts may race between the read and insert. Once
            // the winner commits, re-read it and apply the same idempotence
            // checks instead of leaking a database exception to the API.
            Optional<CycleDefinition> concurrentCycle = findCycle(request.scope());
            if (concurrentCycle.isEmpty()) {
                throw duplicate;
            }
            return existingCycle(request, concurrentCycle.get());
        }

        for (LedgerCode ledgerCode : request.ledgerCodes()) {
            jdbc.update(
                    """
                    INSERT INTO ledger_cycle_requirements (
                        school_id, canteen_id, cycle_id, ledger_code, completed
                    ) VALUES (?, ?, ?, ?, FALSE)
                    """,
                    request.scope().schoolId(),
                    request.scope().canteenId(),
                    request.scope().cycleId(),
                    ledgerCode.name());
        }
        jdbc.update(
                """
                INSERT INTO ledger_alerts (
                    school_id, canteen_id, cycle_id, status, created_at
                ) VALUES (?, ?, ?, 'OPEN', CURRENT_TIMESTAMP)
                """,
                request.scope().schoolId(),
                request.scope().canteenId(),
                request.scope().cycleId());
        return snapshot(request.scope(), true);
    }

    private LedgerState existingCycle(
            LedgerCycleRequest request, CycleDefinition existingCycle) {
        Set<LedgerCode> existingCodes = ledgerCodes(request.scope());
        if (!existingCodes.equals(request.ledgerCodes())) {
            throw new IllegalArgumentException(
                    "Ledger cycle already exists with a different requirement set: "
                            + request.scope().cycleId());
        }
        if (!existingCycle.periodStart().equals(request.periodStart())
                || !existingCycle.periodEnd().equals(request.periodEnd())) {
            throw new IllegalArgumentException(
                    "Ledger cycle already exists with a different period: "
                            + request.scope().cycleId());
        }
        return snapshot(request.scope(), true);
    }

    @Override
    public LedgerState completeLedger(LedgerRecordCommand command) {
        verifyCycle(command.scope());
        Integer requirement = jdbc.queryForObject(
                """
                SELECT COUNT(*)
                FROM ledger_cycle_requirements
                WHERE school_id = ? AND canteen_id = ? AND cycle_id = ?
                  AND ledger_code = ?
                """,
                Integer.class,
                command.scope().schoolId(),
                command.scope().canteenId(),
                command.scope().cycleId(),
                command.ledgerCode().name());
        if (!Objects.equals(requirement, 1)) {
            throw new IllegalArgumentException(
                    "Ledger code is not configured for cycle: " + command.ledgerCode().name());
        }

        // The state predicate makes retries safe. A second caller can update
        // the same row after the first transaction commits and receives the
        // same durable snapshot instead of a duplicate side effect.
        jdbc.update(
                """
                UPDATE ledger_cycle_requirements
                SET completed = TRUE, completed_at = COALESCE(completed_at, CURRENT_TIMESTAMP)
                WHERE school_id = ? AND canteen_id = ? AND cycle_id = ?
                  AND ledger_code = ? AND completed = FALSE
                """,
                command.scope().schoolId(),
                command.scope().canteenId(),
                command.scope().cycleId(),
                command.ledgerCode().name());
        return snapshot(command.scope(), true);
    }

    @Override
    public LedgerState current(LedgerScope scope) {
        verifyCycle(scope);
        return snapshot(scope, false);
    }

    private LedgerState snapshot(LedgerScope scope, boolean persistAlertState) {
        Set<LedgerCode> missing = jdbc.queryForList(
                        """
                        SELECT ledger_code
                        FROM ledger_cycle_requirements
                        WHERE school_id = ? AND canteen_id = ? AND cycle_id = ?
                          AND completed = FALSE
                        ORDER BY ledger_code
                        """,
                        String.class,
                        scope.schoolId(),
                        scope.canteenId(),
                        scope.cycleId())
                .stream()
                .map(LedgerCode::from)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (persistAlertState) {
            syncAlert(scope, missing.isEmpty());
        }
        return new LedgerState(scope, missing);
    }

    private void syncAlert(LedgerScope scope, boolean cleared) {
        String status = cleared ? "CLEARED" : "OPEN";
        jdbc.update(
                """
                UPDATE ledger_cycles
                SET status = ?
                WHERE school_id = ? AND canteen_id = ? AND id = ?
                """,
                status,
                scope.schoolId(),
                scope.canteenId(),
                scope.cycleId());
        if (cleared) {
            jdbc.update(
                    """
                    UPDATE ledger_alerts
                    SET status = 'CLEARED', cleared_at = COALESCE(cleared_at, CURRENT_TIMESTAMP)
                    WHERE school_id = ? AND canteen_id = ? AND cycle_id = ?
                    """,
                    scope.schoolId(),
                    scope.canteenId(),
                    scope.cycleId());
        } else {
            jdbc.update(
                    """
                    UPDATE ledger_alerts
                    SET status = 'OPEN', cleared_at = NULL
                    WHERE school_id = ? AND canteen_id = ? AND cycle_id = ?
                    """,
                    scope.schoolId(),
                    scope.canteenId(),
                    scope.cycleId());
        }
    }

    private void verifyCycle(LedgerScope scope) {
        Optional<CycleDefinition> existing = findCycle(scope);
        if (existing.isEmpty()) {
            throw new IllegalArgumentException("Unknown ledger cycle: " + scope.cycleId());
        }
    }

    private Set<LedgerCode> ledgerCodes(LedgerScope scope) {
        return jdbc.queryForList(
                        """
                        SELECT ledger_code
                        FROM ledger_cycle_requirements
                        WHERE school_id = ? AND canteen_id = ? AND cycle_id = ?
                        """,
                        String.class,
                        scope.schoolId(),
                        scope.canteenId(),
                        scope.cycleId())
                .stream()
                .map(LedgerCode::from)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Optional<CycleDefinition> findCycle(LedgerScope scope) {
        return jdbc.query(
                        """
                        SELECT period_start, period_end
                        FROM ledger_cycles
                        WHERE school_id = ? AND canteen_id = ? AND id = ?
                        """,
                        (result, row) -> new CycleDefinition(
                                result.getDate("period_start").toLocalDate(),
                                result.getDate("period_end").toLocalDate()),
                        scope.schoolId(),
                        scope.canteenId(),
                        scope.cycleId())
                .stream()
                .findFirst();
    }

    private record CycleDefinition(LocalDate periodStart, LocalDate periodEnd) {
    }

    private void ensureSchool(String schoolId) {
        try {
            jdbc.update(
                    "INSERT INTO schools (id, name) VALUES (?, ?)",
                    schoolId,
                    schoolId);
        } catch (DuplicateKeyException ignored) {
            // The catalog already owns this school.
        }
    }

    private void ensureCanteen(String schoolId, String canteenId) {
        Optional<String> existingSchool = findCanteenSchoolId(canteenId);
        if (existingSchool.isPresent()) {
            if (!existingSchool.get().equals(schoolId)) {
                throw new IllegalArgumentException(
                        "Canteen belongs to another school: " + canteenId);
            }
            return;
        }
        try {
            jdbc.update(
                    "INSERT INTO canteens (id, school_id, name) VALUES (?, ?, ?)",
                    canteenId,
                    schoolId,
                    canteenId);
        } catch (DuplicateKeyException ignored) {
            // A concurrent initializer inserted the same canteen. Re-read the
            // association so a race cannot silently link a canteen to a wrong
            // school.
            String actualSchool = findCanteenSchoolId(canteenId)
                    .orElseThrow(() -> new IllegalStateException(
                            "Canteen disappeared during initialization: " + canteenId));
            if (!actualSchool.equals(schoolId)) {
                throw new IllegalArgumentException(
                        "Canteen belongs to another school: " + canteenId);
            }
        }
    }

    private Optional<String> findCanteenSchoolId(String canteenId) {
        return jdbc.query(
                        "SELECT school_id FROM canteens WHERE id = ?",
                        (result, row) -> result.getString("school_id"),
                        canteenId)
                .stream()
                .findFirst();
    }
}
