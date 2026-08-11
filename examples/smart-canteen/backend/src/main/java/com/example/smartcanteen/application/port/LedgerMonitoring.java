package com.example.smartcanteen.application.port;

import com.example.smartcanteen.domain.LedgerAlert;
import com.example.smartcanteen.domain.LedgerCycleRequest;
import com.example.smartcanteen.domain.LedgerRecordCommand;
import com.example.smartcanteen.domain.LedgerScope;

/** Public seam for the ledger-cycle business module. */
public interface LedgerMonitoring {

    LedgerAlert startCycle(LedgerCycleRequest request);

    LedgerAlert completeLedger(LedgerRecordCommand command);

    LedgerAlert current(LedgerScope scope);
}
