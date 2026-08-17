package com.example.smartcanteen.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.smartcanteen.SmartCanteenApplication;
import com.example.smartcanteen.domain.LedgerCode;
import com.example.smartcanteen.domain.LedgerCycleRequest;
import com.example.smartcanteen.domain.LedgerRecordCommand;
import com.example.smartcanteen.domain.LedgerScope;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

class LedgerCyclePersistenceRestartTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void cycle_completion_and_alert_state_survive_application_restart() {
        String databaseUrl = "jdbc:h2:file:"
                + temporaryDirectory.resolve("ledger-cycle").toAbsolutePath()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;AUTO_SERVER=TRUE";
        LedgerScope scope = new LedgerScope(
                "SCHOOL-RESTART-PHASE1",
                "CANTEEN-RESTART-PHASE1",
                "CYCLE-RESTART-PHASE1");
        LedgerCycleRequest request = new LedgerCycleRequest(
                scope,
                Set.of(LedgerCode.PURCHASE_ACCEPTANCE, LedgerCode.SAMPLE_RETENTION));

        try (ConfigurableApplicationContext first = start(databaseUrl)) {
            SmartCanteenWorkflow workflow = first.getBean(SmartCanteenWorkflow.class);

            assertThat(workflow.startLedgerCycle(request).cleared()).isFalse();
            assertThat(workflow.completeLedger(new LedgerRecordCommand(
                    scope, LedgerCode.PURCHASE_ACCEPTANCE)).missingLedgerCodes())
                    .containsExactly("SAMPLE_RETENTION");
        }

        try (ConfigurableApplicationContext second = start(databaseUrl)) {
            SmartCanteenWorkflow workflow = second.getBean(SmartCanteenWorkflow.class);

            assertThat(workflow.currentLedgerAlert(scope).missingLedgerCodes())
                    .containsExactly("SAMPLE_RETENTION");
            assertThat(workflow.completeLedger(new LedgerRecordCommand(
                    scope, LedgerCode.SAMPLE_RETENTION)).cleared()).isTrue();
            assertThat(workflow.startLedgerCycle(request).cleared()).isTrue();
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
