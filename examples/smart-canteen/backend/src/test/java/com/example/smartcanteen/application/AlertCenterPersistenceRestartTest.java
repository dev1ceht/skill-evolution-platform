package com.example.smartcanteen.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.smartcanteen.SmartCanteenApplication;
import com.example.smartcanteen.application.port.AlertCenter;
import com.example.smartcanteen.domain.AlertQuery;
import com.example.smartcanteen.domain.AlertReport;
import com.example.smartcanteen.domain.AlertSource;
import com.example.smartcanteen.domain.AlertStatus;
import java.nio.file.Path;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

class AlertCenterPersistenceRestartTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void reported_alert_and_disposal_state_survive_application_restart() {
        String databaseUrl = "jdbc:h2:file:"
                + temporaryDirectory.resolve("alert-center").toAbsolutePath()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;AUTO_SERVER=TRUE";
        AlertReport report = new AlertReport(
                AlertSource.MORNING_INSPECTION,
                "RESTART-ALERT-001",
                "SCHOOL-RESTART-ALERT",
                "Restart school",
                "440100",
                "DEVICE-RESTART",
                "Morning inspection",
                Instant.parse("2026-08-12T02:15:30Z"),
                "HAND_TEMPERATURE",
                null,
                "temperature abnormal",
                "CANTEEN-RESTART-ALERT");

        try (ConfigurableApplicationContext first = start(databaseUrl)) {
            AlertCenter alerts = first.getBean(AlertCenter.class);
            var created = alerts.report(report);
            alerts.dispose(created.warnId(), new com.example.smartcanteen.domain.AlertDisposal(
                    1,
                    Instant.parse("2026-08-12T02:20:00Z"),
                    "operator-restart",
                    "verified",
                    null));
        }

        try (ConfigurableApplicationContext second = start(databaseUrl)) {
            AlertCenter alerts = second.getBean(AlertCenter.class);
            AlertCenter.AlertPage page = alerts.query(new AlertQuery(
                    "SCHOOL-RESTART-ALERT",
                    "CANTEEN-RESTART-ALERT",
                    AlertSource.MORNING_INSPECTION,
                    AlertStatus.PROCESSED,
                    null,
                    null,
                    null,
                    null,
                    1,
                    20));
            assertThat(page.total()).isEqualTo(1);
            assertThat(page.records().get(0).processUser()).isEqualTo("operator-restart");
        }
    }

    private ConfigurableApplicationContext start(String databaseUrl) {
        return new SpringApplicationBuilder(SmartCanteenApplication.class)
                .web(WebApplicationType.NONE)
                .run(
                        "--spring.datasource.url=" + databaseUrl,
                        "--spring.datasource.username=sa",
                        "--spring.datasource.password=",
                        "--spring.flyway.enabled=true");
    }
}
