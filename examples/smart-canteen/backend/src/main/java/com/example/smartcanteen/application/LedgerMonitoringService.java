package com.example.smartcanteen.application;

import com.example.smartcanteen.application.port.LedgerMonitoring;
import com.example.smartcanteen.application.port.LedgerStore;
import com.example.smartcanteen.domain.LedgerAlert;
import com.example.smartcanteen.domain.LedgerAlertService;
import com.example.smartcanteen.domain.LedgerCycleRequest;
import com.example.smartcanteen.domain.LedgerRecordCommand;
import com.example.smartcanteen.domain.LedgerScope;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LedgerMonitoringService implements LedgerMonitoring {

    private final LedgerStore store;
    private final LedgerAlertService alerts = new LedgerAlertService();

    public LedgerMonitoringService(LedgerStore store) {
        this.store = store;
    }

    @Override
    @Transactional
    public LedgerAlert startCycle(LedgerCycleRequest request) {
        return alerts.current(store.startCycle(request));
    }

    @Override
    @Transactional
    public LedgerAlert completeLedger(LedgerRecordCommand command) {
        return alerts.current(store.completeLedger(command));
    }

    @Override
    @Transactional(readOnly = true)
    public LedgerAlert current(LedgerScope scope) {
        return alerts.current(store.current(scope));
    }
}
