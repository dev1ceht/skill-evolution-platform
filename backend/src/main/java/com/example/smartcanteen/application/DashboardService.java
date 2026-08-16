package com.example.smartcanteen.application;

import com.example.smartcanteen.application.port.OperationalStore;
import com.example.smartcanteen.domain.CanteenScope;
import com.example.smartcanteen.domain.DashboardSummary;
import com.example.smartcanteen.domain.RiskAssessment;
import com.example.smartcanteen.domain.TraceabilityResult;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardService {

    private final OperationalStore store;

    public DashboardService(OperationalStore store) {
        this.store = store;
    }

    @Transactional(readOnly = true)
    public DashboardSummary summary(CanteenScope scope, LocalDate date) {
        return store.dashboardSummary(scope, date == null ? LocalDate.now() : date);
    }

    @Transactional(readOnly = true)
    public RiskAssessment risk(CanteenScope scope, LocalDate date) {
        DashboardSummary summary = summary(scope, date);
        int penalty = 0;
        List<String> factors = new ArrayList<>();
        if (summary.openLedgerAlertCount() > 0) {
            penalty += Math.min(40, Math.toIntExact(summary.openLedgerAlertCount()) * 10);
            factors.add("存在未完成台账周期");
        }
        if (summary.inventoryWarningCount() > 0) {
            penalty += Math.min(30, Math.toIntExact(summary.inventoryWarningCount()) * 5);
            factors.add("库存低于预警阈值");
        }
        if (summary.openExternalAlertCount() > 0) {
            penalty += Math.min(40, Math.toIntExact(summary.openExternalAlertCount()) * 10);
            factors.add("存在未处置外部预警");
        }
        if (summary.todayMenuCount() == 0) {
            penalty += 20;
            factors.add("当日尚未编制食谱");
        }
        int score = Math.max(0, 100 - penalty);
        String level = score >= 80 ? "LOW" : score >= 60 ? "MEDIUM" : "HIGH";
        return new RiskAssessment(score, level, factors);
    }

    /**
     * A missing trace is a normal Agent business failure. Do not poison the caller's Run
     * transaction before AgentExecutionService can persist FAILED + evidence state.
     */
    @Transactional(readOnly = true, noRollbackFor = RuntimeException.class)
    public TraceabilityResult trace(CanteenScope scope, String traceCode) {
        return store.trace(scope, traceCode)
                .orElseThrow(() -> new IllegalArgumentException("Traceability code not found: " + traceCode));
    }
}
