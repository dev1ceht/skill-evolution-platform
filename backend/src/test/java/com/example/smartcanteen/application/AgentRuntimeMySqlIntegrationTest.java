package com.example.smartcanteen.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.smartcanteen.SmartCanteenApplication;
import com.example.smartcanteen.agent.application.AgentRuntime;
import com.example.smartcanteen.agent.domain.AgentRun;
import com.example.smartcanteen.agent.domain.ExecutionContext;
import com.example.smartcanteen.agent.domain.StartRunCommand;
import com.example.smartcanteen.agent.port.AgentRunStore;
import com.example.smartcanteen.domain.CanteenScope;
import com.example.smartcanteen.security.AuthPrincipal;
import com.example.smartcanteen.security.Role;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/** Real MySQL gate for Agent idempotency, durable evidence, and application restart recovery. */
@EnabledIfEnvironmentVariable(named = "SMART_CANTEEN_MYSQL_IT", matches = "true")
class AgentRuntimeMySqlIntegrationTest {

    private static final String ACTOR_ID = "MYSQL-AGENT-IT-USER";
    private static final String SCHOOL_ID = "SCHOOL-AGENT-MYSQL";
    private static final String CANTEEN_ID = "CANTEEN-AGENT-MYSQL";
    private static final CanteenScope SCOPE = new CanteenScope(SCHOOL_ID, CANTEEN_ID);

    @Test
    @Timeout(value = 90)
    void real_mysql_serializes_same_key_start_and_survives_restart() throws Exception {
        String runId;
        ExecutionContext context = context("agent-mysql-request-001");
        StartRunCommand command = new StartRunCommand(
                context.requestId(),
                "traceability.query",
                "{\"traceCode\":\"TRACE-MYSQL-AGENT-001\"}",
                "agent-mysql-same-key");

        try (ConfigurableApplicationContext first = start()) {
            JdbcTemplate jdbc = first.getBean(JdbcTemplate.class);
            ensureAuditActor(jdbc);
            AgentRuntime runtime = first.getBean(AgentRuntime.class);
            AgentRunStore runs = first.getBean(AgentRunStore.class);

            List<AgentRun> concurrent = runConcurrently(
                    () -> runtime.start(command, context),
                    () -> runtime.start(command, context));
            runId = concurrent.get(0).runId();

            assertThat(concurrent).extracting(AgentRun::runId).containsOnly(runId);
            assertThat(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM agent_runs WHERE actor_user_id = ? "
                            + "AND school_id = ? AND canteen_id = ? AND idempotency_key = ?",
                    Integer.class,
                    ACTOR_ID,
                    SCHOOL_ID,
                    CANTEEN_ID,
                    "agent-mysql-same-key")).isEqualTo(1);
            assertThat(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM agent_steps WHERE run_id = ?",
                    Integer.class,
                    runId)).isEqualTo(1);
            assertThat(runs.listEvents(runId))
                    .extracting(event -> event.eventType())
                    .contains("RUN_PLANNED", "RUN_IDEMPOTENCY_REPLAY");
            assertThat(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM audit_logs WHERE resource_type = 'AGENT_RUN' "
                            + "AND resource_id = ? AND action = 'AGENT_RUN_PLAN'",
                    Integer.class,
                    runId)).isEqualTo(1);

            assertThatThrownBy(() -> runtime.start(
                            new StartRunCommand(
                                    context.requestId(),
                                    command.intent(),
                                    "{\"traceCode\":\"TRACE-MYSQL-AGENT-DIFFERENT\"}",
                                    command.idempotencyKey()),
                            context))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("different Agent request");

            TransactionTemplate transaction = new TransactionTemplate(
                    first.getBean(org.springframework.transaction.PlatformTransactionManager.class));
            StartRunCommand sameTransactionCommand = new StartRunCommand(
                    "agent-mysql-request-same-tx",
                    command.intent(),
                    "{\"traceCode\":\"TRACE-MYSQL-AGENT-SAME-TX\"}",
                    "agent-mysql-same-transaction");
            AgentRun sameTransactionRun = transaction.execute(status -> {
                AgentRun firstStart = runtime.start(
                        sameTransactionCommand,
                        context("agent-mysql-request-same-tx"));
                AgentRun secondStart = runtime.start(
                        sameTransactionCommand,
                        context("agent-mysql-request-same-tx"));
                assertThat(secondStart.runId()).isEqualTo(firstStart.runId());
                return firstStart;
            });
            assertThat(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM agent_runs WHERE idempotency_key = ?",
                    Integer.class,
                    sameTransactionCommand.idempotencyKey())).isEqualTo(1);
            assertThat(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM agent_run_events WHERE run_id = ?",
                    Integer.class,
                    sameTransactionRun.runId())).isEqualTo(2);

            StartRunCommand rollbackCommand = new StartRunCommand(
                    "agent-mysql-request-rollback",
                    command.intent(),
                    "{\"traceCode\":\"TRACE-MYSQL-AGENT-ROLLBACK\"}",
                    "agent-mysql-rollback");
            assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
                        runtime.start(rollbackCommand, context("agent-mysql-request-rollback"));
                        throw new IllegalStateException("force outer transaction rollback");
                    }))
                    .hasMessageContaining("force outer transaction rollback");
            assertThat(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM agent_runs WHERE idempotency_key = ?",
                    Integer.class,
                    rollbackCommand.idempotencyKey())).isZero();
            assertThat(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM audit_logs WHERE action = 'AGENT_RUN_PLAN' "
                            + "AND request_id = ?",
                    Integer.class,
                    rollbackCommand.requestId())).isZero();
        }

        try (ConfigurableApplicationContext second = start()) {
            AgentRunStore runs = second.getBean(AgentRunStore.class);
            AgentRun reloaded = runs.findById(runId).orElseThrow();

            assertThat(reloaded.status()).isEqualTo(com.example.smartcanteen.agent.domain.RunStatus.PLANNED);
            assertThat(reloaded.scope()).isEqualTo(SCOPE);
            assertThat(runs.listEvents(runId))
                    .extracting(event -> event.eventType())
                    .contains("RUN_PLANNED", "RUN_IDEMPOTENCY_REPLAY");
        }
    }

    private ExecutionContext context(String requestId) {
        return ExecutionContext.fromTrustedPrincipal(
                requestId,
                new AuthPrincipal(
                        ACTOR_ID,
                        "mysql-agent-it",
                        "MySQL Agent IT",
                        Role.CANTEEN_STAFF,
                        SCHOOL_ID,
                        CANTEEN_ID),
                SCOPE,
                Set.of(Role.CANTEEN_STAFF),
                Set.of("TRACEABILITY_READ"));
    }

    private void ensureAuditActor(JdbcTemplate jdbc) {
        jdbc.update(
                "INSERT INTO app_users (user_id, username, password_hash, display_name, role, "
                        + "school_id, canteen_id, status) VALUES (?, ?, ?, ?, ?, ?, ?, 'ACTIVE')",
                ACTOR_ID,
                "mysql-agent-it",
                "integration-test-password-hash",
                "MySQL Agent IT",
                Role.CANTEEN_STAFF.name(),
                SCHOOL_ID,
                CANTEEN_ID);
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

    private <T> List<T> runConcurrently(Callable<T> first, Callable<T> second) throws Exception {
        AtomicInteger workerNumber = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(2, operation -> {
            Thread worker = new Thread(
                    operation, "mysql-agent-it-" + workerNumber.incrementAndGet());
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
                throw new IllegalStateException("Concurrent MySQL Agent workers did not terminate");
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
}
