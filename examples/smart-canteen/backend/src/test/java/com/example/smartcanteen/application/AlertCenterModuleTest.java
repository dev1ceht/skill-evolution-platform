package com.example.smartcanteen.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.smartcanteen.application.port.AlertCenter;
import com.example.smartcanteen.application.port.AlertStore;
import com.example.smartcanteen.domain.AlertDisposal;
import com.example.smartcanteen.domain.AlertQuery;
import com.example.smartcanteen.domain.AlertRecord;
import com.example.smartcanteen.domain.AlertReport;
import com.example.smartcanteen.domain.AlertSource;
import com.example.smartcanteen.domain.AlertStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AlertCenterModuleTest {

    private static final Instant OCCURRED_AT = Instant.parse("2026-08-12T02:15:30Z");

    @Test
    void repeated_report_with_the_same_source_and_third_id_is_idempotent() {
        InMemoryAlertStore store = new InMemoryAlertStore();
        AlertCenter alerts = new AlertCenterService(store);
        AlertReport report = report("ALERT-001", "temperature is too high");

        AlertRecord first = alerts.report(report);
        AlertRecord retry = alerts.report(report);

        assertThat(retry).isEqualTo(first);
        assertThat(store.records()).hasSize(1);
        assertThat(first.status()).isEqualTo(AlertStatus.UNPROCESSED);
    }

    @Test
    void reusing_a_third_id_for_a_different_payload_is_rejected() {
        AlertCenter alerts = new AlertCenterService(new InMemoryAlertStore());
        alerts.report(report("ALERT-CONFLICT", "first payload"));

        assertThatThrownBy(() -> alerts.report(report("ALERT-CONFLICT", "changed payload")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("different alert payload");
    }

    @Test
    void processing_is_idempotent_and_query_returns_the_updated_status() {
        InMemoryAlertStore store = new InMemoryAlertStore();
        AlertCenter alerts = new AlertCenterService(store);
        AlertRecord created = alerts.report(report("ALERT-PROCESS", "wash hands"));
        AlertDisposal disposal = new AlertDisposal(
                1,
                Instant.parse("2026-08-12T02:20:00Z"),
                "operator-1",
                "cleaned and verified",
                "https://files.example/process-1.jpg");

        AlertRecord processed = alerts.dispose(created.warnId(), disposal);
        AlertRecord retry = alerts.dispose(created.warnId(), disposal);

        assertThat(processed.status()).isEqualTo(AlertStatus.PROCESSED);
        assertThat(retry).isEqualTo(processed);
        assertThatThrownBy(() -> alerts.dispose(
                created.warnId(),
                new AlertDisposal(
                        1,
                        Instant.parse("2026-08-12T02:21:00Z"),
                        "operator-2",
                        "different result",
                        null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("processed");
        AlertCenter.AlertPage page = alerts.query(new AlertQuery(
                "SCHOOL-ALERT", null, AlertSource.BRIGHT_KITCHEN,
                AlertStatus.PROCESSED, null, null, null, null, 1, 20));
        assertThat(page.total()).isEqualTo(1);
        assertThat(page.records()).extracting(AlertRecord::warnId)
                .containsExactly(created.warnId());
    }

    @Test
    void public_boundary_rejects_missing_identifiers_and_invalid_disposal_status() {
        AlertCenter alerts = new AlertCenterService(new InMemoryAlertStore());

        assertThatThrownBy(() -> alerts.report(new AlertReport(
                AlertSource.BRIGHT_KITCHEN, "", "SCHOOL-ALERT", null, null,
                null, null, OCCURRED_AT, "FOOD", null, "content", null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("thirdWarnId is required");

        assertThatThrownBy(() -> alerts.dispose(
                "BRIGHT_KITCHEN:ALERT-001",
                new AlertDisposal(2, null, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("processStatus");
    }

    private static AlertReport report(String thirdWarnId, String content) {
        return new AlertReport(
                AlertSource.BRIGHT_KITCHEN,
                thirdWarnId,
                "SCHOOL-ALERT",
                "Alert school",
                "440100",
                "CAM-001",
                "Kitchen camera",
                OCCURRED_AT,
                "FOOD_TEMPERATURE",
                "https://files.example/alert.jpg",
                content,
                "CANTEEN-ALERT");
    }

    private static final class InMemoryAlertStore implements AlertStore {

        private final Map<String, AlertRecord> records = new LinkedHashMap<>();

        @Override
        public AlertRecord report(AlertReport report) {
            String warnId = report.warnId();
            AlertRecord existing = records.get(warnId);
            if (existing != null) {
                if (!existing.matches(report)) {
                    throw new IllegalArgumentException("different alert payload");
                }
                return existing;
            }
            AlertRecord created = AlertRecord.reported(warnId, report, Instant.now());
            records.put(warnId, created);
            return created;
        }

        @Override
        public AlertRecord dispose(String warnId, AlertDisposal disposal) {
            AlertRecord existing = Optional.ofNullable(records.get(warnId))
                    .orElseThrow(() -> new IllegalArgumentException("Alert not found: " + warnId));
            if (existing.hasSameDisposal(disposal)) {
                return existing;
            }
            if (existing.status() == AlertStatus.PROCESSED) {
                throw new IllegalArgumentException("processed alert cannot be changed");
            }
            AlertRecord updated = existing.withDisposal(disposal);
            records.put(warnId, updated);
            return updated;
        }

        @Override
        public AlertCenter.AlertPage query(AlertQuery query) {
            List<AlertRecord> matching = records.values().stream()
                    .filter(query::matches)
                    .sorted(Comparator.comparing(AlertRecord::warnHappenTime).reversed())
                    .toList();
            int from = Math.min((query.pageNum() - 1) * query.pageSize(), matching.size());
            int to = Math.min(from + query.pageSize(), matching.size());
            return new AlertCenter.AlertPage(
                    new ArrayList<>(matching.subList(from, to)),
                    query.pageNum(), query.pageSize(), matching.size());
        }

        List<AlertRecord> records() {
            return List.copyOf(records.values());
        }
    }
}
