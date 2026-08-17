package com.example.smartcanteen.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.smartcanteen.SmartCanteenApplication;
import com.example.smartcanteen.application.port.AlertCenter;
import com.example.smartcanteen.domain.AlertQuery;
import com.example.smartcanteen.domain.AlertSource;
import com.example.smartcanteen.domain.AlertStatus;
import com.example.smartcanteen.domain.CanteenScope;
import com.example.smartcanteen.domain.CanteenShowcase;
import com.example.smartcanteen.domain.CanteenShowcaseStatus;
import com.example.smartcanteen.domain.ComplianceCategory;
import com.example.smartcanteen.domain.ComplianceRecord;
import com.example.smartcanteen.domain.ComplianceRecordStatus;
import com.example.smartcanteen.domain.GovernanceHistory;
import com.example.smartcanteen.domain.LedgerConfiguration;
import com.example.smartcanteen.domain.LedgerConfigurationStatus;
import com.example.smartcanteen.domain.LedgerFrequency;
import com.example.smartcanteen.domain.MealPeriod;
import com.example.smartcanteen.domain.MealSuspension;
import com.example.smartcanteen.domain.MealSuspensionStatus;
import com.example.smartcanteen.domain.SupplierComplaint;
import com.example.smartcanteen.domain.SupplierComplaintStatus;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

class FoodSafetyGovernancePersistenceRestartTest {

    private static final CanteenScope SCOPE = new CanteenScope(
            "SCHOOL-RESTART-PHASE3", "CANTEEN-RESTART-PHASE3");
    private static final LocalDate AS_OF = LocalDate.of(2026, 8, 15);

    @TempDir
    Path temporaryDirectory;

    @Test
    void stage3_state_history_and_expiry_alert_survive_application_restart() {
        String databaseUrl = "jdbc:h2:file:"
                + temporaryDirectory.resolve("food-safety-governance").toAbsolutePath()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;AUTO_SERVER=TRUE";

        try (ConfigurableApplicationContext first = start(databaseUrl)) {
            seedScope(first.getBean(JdbcTemplate.class));
            ConfigurableLedgerService ledgers = first.getBean(ConfigurableLedgerService.class);
            ComplianceRecordService compliance = first.getBean(ComplianceRecordService.class);
            GovernanceService governance = first.getBean(GovernanceService.class);

            ledgers.create(SCOPE, new LedgerConfiguration(
                    "CONFIG-RESTART-PHASE3",
                    "TEMPERATURE_CHECK",
                    "冷藏温度记录",
                    LedgerFrequency.DAILY,
                    null,
                    List.of("temperature"),
                    Map.of("unit", "C"),
                    "CANTEEN_STAFF",
                    1,
                    LedgerConfigurationStatus.ACTIVE,
                    0,
                    null,
                    null));
            var cycle = ledgers.ensureCurrent(SCOPE, AS_OF).get(0);
            assertThat(cycle.missingLedgerCodes()).containsExactly("TEMPERATURE_CHECK");
            ledgers.complete(
                    SCOPE,
                    cycle.cycleId(),
                    "TEMPERATURE_CHECK",
                    "LEDGER-RESTART-PHASE3",
                    null,
                    "operator-phase3",
                    Map.of("temperature", 4.2),
                    List.of("https://example.test/temperature.jpg"),
                    "正常");

            ComplianceRecord record = compliance.create(SCOPE, new ComplianceRecord(
                    "COMPLIANCE-RESTART-PHASE3",
                    ComplianceCategory.LICENSE,
                    "CANTEEN",
                    SCOPE.canteenId(),
                    "重启测试食堂",
                    "食品经营许可证",
                    "LIC-RESTART-PHASE3",
                    AS_OF.minusDays(30),
                    AS_OF.plusDays(10),
                    List.of("https://example.test/license.pdf"),
                    ComplianceRecordStatus.DRAFT,
                    null,
                    0,
                    null,
                    null,
                    null,
                    null,
                    null), "operator-phase3");
            record = compliance.submit(SCOPE, record.id(), record.version(), "operator-phase3");
            record = compliance.review(
                    SCOPE,
                    record.id(),
                    record.version(),
                    ComplianceRecordStatus.APPROVED,
                    "资料核验通过",
                    "reviewer-phase3");
            assertThat(compliance.scanExpiry(SCOPE, AS_OF, 30)).hasSize(1);

            CanteenShowcase showcase = governance.createShowcase(SCOPE, new CanteenShowcase(
                    "SHOWCASE-RESTART-PHASE3",
                    "食堂风采",
                    "公开后厨和陪餐记录",
                    List.of(),
                    CanteenShowcaseStatus.DRAFT,
                    null,
                    0,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null), "operator-phase3");
            showcase = governance.submitShowcase(SCOPE, showcase.id(), showcase.version(), "operator-phase3");
            showcase = governance.reviewShowcase(
                    SCOPE,
                    showcase.id(),
                    showcase.version(),
                    CanteenShowcaseStatus.APPROVED,
                    "内容合规",
                    "reviewer-phase3");
            governance.publishShowcase(SCOPE, showcase.id(), showcase.version(), "publisher-phase3");

            MealSuspension suspension = governance.createMealSuspension(SCOPE, new MealSuspension(
                    "SUSPENSION-RESTART-PHASE3",
                    AS_OF.plusDays(1),
                    MealPeriod.LUNCH,
                    "设备维护",
                    MealSuspensionStatus.SUBMITTED,
                    null,
                    0,
                    null,
                    null,
                    null,
                    null), "operator-phase3");
            governance.reviewMealSuspension(
                    SCOPE,
                    suspension.id(),
                    suspension.version(),
                    MealSuspensionStatus.APPROVED,
                    "已确认",
                    "reviewer-phase3");

            SupplierComplaint complaint = governance.createComplaint(SCOPE, new SupplierComplaint(
                    "COMPLAINT-RESTART-PHASE3",
                    "SUPPLIER-RESTART-PHASE3",
                    "食材质量问题",
                    "批次标签缺失",
                    List.of(),
                    AS_OF.plusDays(7),
                    SupplierComplaintStatus.SUBMITTED,
                    null,
                    0,
                    "operator-phase3",
                    null,
                    null,
                    null,
                    null,
                    null), "operator-phase3");
            complaint = governance.reviewComplaint(
                    SCOPE,
                    complaint.id(),
                    complaint.version(),
                    SupplierComplaintStatus.ACCEPTED,
                    null,
                    "reviewer-phase3");
            complaint = governance.processComplaint(
                    SCOPE, complaint.id(), complaint.version(), "operator-phase3");
            complaint = governance.replyComplaint(
                    SCOPE, complaint.id(), complaint.version(), "已完成整改", "operator-phase3");
            governance.closeComplaint(SCOPE, complaint.id(), complaint.version(), "reviewer-phase3");
        }

        try (ConfigurableApplicationContext second = start(databaseUrl)) {
            ConfigurableLedgerService ledgers = second.getBean(ConfigurableLedgerService.class);
            ComplianceRecordService compliance = second.getBean(ComplianceRecordService.class);
            GovernanceService governance = second.getBean(GovernanceService.class);
            AlertCenter alerts = second.getBean(AlertCenter.class);

            assertThat(ledgers.ensureCurrent(SCOPE, AS_OF).get(0).cleared()).isTrue();
            assertThat(compliance.find(SCOPE, "COMPLIANCE-RESTART-PHASE3")).get()
                    .extracting(ComplianceRecord::status)
                    .isEqualTo(ComplianceRecordStatus.APPROVED);
            assertThat(compliance.history(SCOPE, "COMPLIANCE-RESTART-PHASE3"))
                    .extracting(history -> history.action())
                    .containsExactly("CREATED", "SUBMITTED", "APPROVED");
            assertThat(alerts.query(new AlertQuery(
                    SCOPE.schoolId(), SCOPE.canteenId(), AlertSource.COMPLIANCE,
                    AlertStatus.UNPROCESSED, "COMPLIANCE_EXPIRY", null,
                    null, null, 1, 20)).total()).isEqualTo(1);

            assertThat(governance.findShowcase(SCOPE, "SHOWCASE-RESTART-PHASE3")).get()
                    .extracting(CanteenShowcase::status)
                    .isEqualTo(CanteenShowcaseStatus.PUBLISHED);
            assertThat(governance.showcaseHistory(SCOPE, "SHOWCASE-RESTART-PHASE3"))
                    .extracting(GovernanceHistory::action)
                    .containsExactly("CREATED", "SUBMITTED", "APPROVED", "PUBLISHED");
            assertThat(governance.listMealSuspensions(SCOPE, null, null, null, 1, 20).records())
                    .extracting(MealSuspension::status)
                    .containsExactly(MealSuspensionStatus.APPROVED);
            assertThat(governance.listComplaints(SCOPE, null, null, 1, 20).records())
                    .extracting(SupplierComplaint::status)
                    .containsExactly(SupplierComplaintStatus.CLOSED);
        }
    }

    private void seedScope(JdbcTemplate jdbc) {
        jdbc.update("INSERT INTO schools (id, name) VALUES (?, ?)", SCOPE.schoolId(), "阶段3重启学校");
        jdbc.update(
                "INSERT INTO canteens (id, school_id, name) VALUES (?, ?, ?)",
                SCOPE.canteenId(),
                SCOPE.schoolId(),
                "阶段3重启食堂");
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
