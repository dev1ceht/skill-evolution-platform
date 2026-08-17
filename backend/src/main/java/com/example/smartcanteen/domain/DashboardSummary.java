package com.example.smartcanteen.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DashboardSummary(
        LocalDate date,
        long todayMenuCount,
        long publishedMenuCount,
        long pendingPurchaseOrderCount,
        long inventoryWarningCount,
        long openLedgerAlertCount,
        long openExternalAlertCount,
        BigDecimal purchaseAmount) {
}
