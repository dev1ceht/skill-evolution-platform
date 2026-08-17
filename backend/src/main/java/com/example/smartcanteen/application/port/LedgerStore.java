package com.example.smartcanteen.application.port;

import com.example.smartcanteen.domain.LedgerCycleRequest;
import com.example.smartcanteen.domain.LedgerRecordCommand;
import com.example.smartcanteen.domain.LedgerScope;
import com.example.smartcanteen.domain.LedgerState;

/**
 * Persistence seam for the ledger monitoring module.
 *
 * The adapter owns cycle initialization, idempotent completion and durable
 * alert-state updates. Callers observe only a complete state snapshot.
 */
public interface LedgerStore {

    LedgerState startCycle(LedgerCycleRequest request);

    LedgerState completeLedger(LedgerRecordCommand command);

    LedgerState current(LedgerScope scope);
}
