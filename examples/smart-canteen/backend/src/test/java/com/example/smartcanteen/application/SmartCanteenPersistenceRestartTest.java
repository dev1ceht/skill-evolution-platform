package com.example.smartcanteen.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.smartcanteen.SmartCanteenApplication;
import java.math.BigDecimal;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

class SmartCanteenPersistenceRestartTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void workflow_state_and_idempotency_survive_application_restart() {
        String databaseUrl = "jdbc:h2:file:"
                + temporaryDirectory.resolve("smart-canteen").toAbsolutePath()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;AUTO_SERVER=TRUE";

        try (ConfigurableApplicationContext first = start(databaseUrl)) {
            SmartCanteenWorkflow workflow = first.getBean(SmartCanteenWorkflow.class);
            workflow.submitMenu("MENU-001");
            workflow.decideMenu("MENU-001", "APPROVE", "持久化审批");
            workflow.receive(
                    "restart-safe-receipt",
                    "FLOUR",
                    new BigDecimal("1.5"),
                    "kg");
            workflow.completeLedger("PURCHASE_ACCEPTANCE");
        }

        try (ConfigurableApplicationContext second = start(databaseUrl)) {
            SmartCanteenWorkflow workflow = second.getBean(SmartCanteenWorkflow.class);

            assertThat(workflow.generateProcurement("MENU-001")).isEmpty();
            assertThat(workflow.currentLedgerAlert().cleared()).isTrue();
            assertThat(workflow.receive(
                            "restart-safe-receipt",
                            "FLOUR",
                            new BigDecimal("1.5"),
                            "kg")
                    .quantityBase())
                    .isEqualByComparingTo("2000");
            assertThatThrownBy(() -> workflow.receive(
                            "restart-safe-receipt",
                            "FLOUR",
                            new BigDecimal("2.0"),
                            "kg"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Idempotency-Key was already used");
        }
    }

    @Test
    void incompatible_inventory_unit_rolls_back_receipt_reservation_and_quantity() {
        String databaseUrl = "jdbc:h2:file:"
                + temporaryDirectory.resolve("unit-dimension").toAbsolutePath()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;AUTO_SERVER=TRUE";

        try (ConfigurableApplicationContext context = start(databaseUrl)) {
            SmartCanteenWorkflow workflow = context.getBean(SmartCanteenWorkflow.class);

            assertThatThrownBy(() -> workflow.receive(
                            "unit-safe-receipt", "FLOUR", BigDecimal.ONE, "L"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Inventory unit mismatch");

            assertThat(workflow.receive(
                            "unit-safe-receipt", "FLOUR", BigDecimal.ONE, "kg")
                    .quantityBase())
                    .isEqualByComparingTo("1500");
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
