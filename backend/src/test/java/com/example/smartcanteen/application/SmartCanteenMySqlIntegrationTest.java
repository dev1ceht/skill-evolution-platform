package com.example.smartcanteen.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.smartcanteen.SmartCanteenApplication;
import com.example.smartcanteen.application.SmartCanteenWorkflow.ReceiptResult;
import com.example.smartcanteen.domain.LedgerAlert;
import com.example.smartcanteen.domain.LedgerCode;
import com.example.smartcanteen.domain.LedgerCycleRequest;
import com.example.smartcanteen.domain.LedgerRecordCommand;
import com.example.smartcanteen.domain.LedgerScope;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

@EnabledIfEnvironmentVariable(named = "SMART_CANTEEN_MYSQL_IT", matches = "true")
class SmartCanteenMySqlIntegrationTest {

    @Test
    @Timeout(value = 90)
    void real_mysql_preserves_state_and_serializes_concurrent_receipts() throws Exception {
        try (ConfigurableApplicationContext first = start()) {
            SmartCanteenWorkflow workflow = first.getBean(SmartCanteenWorkflow.class);
            workflow.submitMenu("MENU-001");
            workflow.decideMenu("MENU-001", "APPROVE", "real MySQL integration");

            List<ReceiptResult> sameKeyResults = runConcurrently(
                    () -> workflow.receive(
                            "mysql-concurrent-same-key", "FLOUR", new BigDecimal("1.5"), "kg"),
                    () -> workflow.receive(
                            "mysql-concurrent-same-key", "FLOUR", new BigDecimal("1.5"), "kg"));
            assertThat(sameKeyResults)
                    .extracting(ReceiptResult::quantityBase)
                    .allSatisfy(quantity -> assertThat(quantity).isEqualByComparingTo("2000"));

            List<ReceiptResult> firstMaterialResults = runConcurrently(
                    () -> workflow.receive(
                            "mysql-first-material-a", "NAPKIN", BigDecimal.ONE, "count"),
                    () -> workflow.receive(
                            "mysql-first-material-b", "NAPKIN", BigDecimal.ONE, "count"));
            assertThat(firstMaterialResults)
                    .extracting(ReceiptResult::quantityBase)
                    .map(BigDecimal::stripTrailingZeros)
                    .containsExactlyInAnyOrder(new BigDecimal("1"), new BigDecimal("2"));

            assertThatThrownBy(() -> workflow.receive(
                            "mysql-incompatible-unit", "FLOUR", BigDecimal.ONE, "L"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Inventory unit mismatch");
            assertThat(workflow.receive(
                            "mysql-incompatible-unit",
                            "FLOUR",
                            new BigDecimal("0.5"),
                            "kg")
                    .quantityBase())
                    .isEqualByComparingTo("2500");

            LedgerScope ledgerScope = new LedgerScope(
                    "SCHOOL-001", "CANTEEN-001", "CYCLE-MYSQL-PHASE1");
            LedgerCycleRequest ledgerCycle = new LedgerCycleRequest(
                    ledgerScope,
                    Set.of(LedgerCode.PURCHASE_ACCEPTANCE, LedgerCode.SAMPLE_RETENTION));
            List<LedgerAlert> sameCycleStarts = runConcurrently(
                    () -> workflow.startLedgerCycle(ledgerCycle),
                    () -> workflow.startLedgerCycle(ledgerCycle));
            assertThat(sameCycleStarts).allSatisfy(alert -> assertThat(alert.cleared()).isFalse());
            List<LedgerAlert> sameLedgerResults = runConcurrently(
                    () -> workflow.completeLedger(new LedgerRecordCommand(
                            ledgerScope, LedgerCode.PURCHASE_ACCEPTANCE)),
                    () -> workflow.completeLedger(new LedgerRecordCommand(
                            ledgerScope, LedgerCode.PURCHASE_ACCEPTANCE)));
            assertThat(sameLedgerResults)
                    .allSatisfy(alert -> assertThat(alert.missingLedgerCodes())
                            .containsExactly("SAMPLE_RETENTION"));
            assertThat(workflow.completeLedger(new LedgerRecordCommand(
                    ledgerScope, LedgerCode.SAMPLE_RETENTION)).cleared()).isTrue();
            workflow.completeLedger("PURCHASE_ACCEPTANCE");
        }

        try (ConfigurableApplicationContext second = start()) {
            SmartCanteenWorkflow workflow = second.getBean(SmartCanteenWorkflow.class);

            assertThat(workflow.generateProcurement("MENU-001")).isEmpty();
            assertThat(workflow.currentLedgerAlert().cleared()).isTrue();
            assertThat(workflow.currentLedgerAlert(new LedgerScope(
                    "SCHOOL-001", "CANTEEN-001", "CYCLE-MYSQL-PHASE1")).cleared()).isTrue();
            assertThat(workflow.receive(
                            "mysql-concurrent-same-key",
                            "FLOUR",
                            new BigDecimal("1.5"),
                            "kg")
                    .quantityBase())
                    .isEqualByComparingTo("2000");
            assertThatThrownBy(() -> workflow.receive(
                            "mysql-concurrent-same-key",
                            "FLOUR",
                            new BigDecimal("2.0"),
                            "kg"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Idempotency-Key was already used");
            assertThat(workflow.receive(
                            "mysql-incompatible-unit",
                            "FLOUR",
                            new BigDecimal("0.5"),
                            "kg")
                    .quantityBase())
                    .isEqualByComparingTo("2500");
            assertThat(workflow.receive(
                            "mysql-first-material-c", "NAPKIN", BigDecimal.ONE, "count")
                    .quantityBase())
                    .isEqualByComparingTo("3");
        }
    }

    private <T> List<T> runConcurrently(
            Callable<T> first, Callable<T> second) throws Exception {
        AtomicInteger workerNumber = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(2, operation -> {
            Thread worker = new Thread(
                    operation, "mysql-receipt-it-" + workerNumber.incrementAndGet());
            worker.setDaemon(true);
            return worker;
        });
        CyclicBarrier startTogether = new CyclicBarrier(2);
        Future<T> firstResult = null;
        Future<T> secondResult = null;
        try {
            firstResult = executor.submit(afterBarrier(startTogether, first));
            secondResult = executor.submit(afterBarrier(startTogether, second));
            return List.of(
                    firstResult.get(30, TimeUnit.SECONDS),
                    secondResult.get(30, TimeUnit.SECONDS));
        } finally {
            if (firstResult != null) {
                firstResult.cancel(true);
            }
            if (secondResult != null) {
                secondResult.cancel(true);
            }
            executor.shutdownNow();
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Concurrent MySQL receipt workers did not terminate");
            }
        }
    }

    private <T> Callable<T> afterBarrier(
            CyclicBarrier barrier, Callable<T> operation) {
        return () -> {
            barrier.await(10, TimeUnit.SECONDS);
            return operation.call();
        };
    }

    private ConfigurableApplicationContext start() {
        return new SpringApplicationBuilder(SmartCanteenApplication.class)
                .web(WebApplicationType.NONE)
                .run(
                        "--spring.datasource.url=" + requiredEnvironment("SMART_CANTEEN_DB_URL"),
                        "--spring.datasource.username="
                                + requiredEnvironment("SMART_CANTEEN_DB_USERNAME"),
                        "--spring.datasource.password="
                                + requiredEnvironment("SMART_CANTEEN_DB_PASSWORD"),
                        "--spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
                        "--spring.flyway.enabled=true");
    }

    private String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required for the MySQL integration test");
        }
        return value;
    }
}
