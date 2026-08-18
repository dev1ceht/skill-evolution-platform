package com.example.smartcanteen.application.port;

import com.example.smartcanteen.domain.AlertDisposal;
import com.example.smartcanteen.domain.AlertQuery;
import com.example.smartcanteen.domain.AlertRecord;
import com.example.smartcanteen.domain.AlertReport;
import java.util.Optional;

/** Persistence port for the alert-center deep module. */
public interface AlertStore {

    AlertRecord report(AlertReport report);

    Optional<AlertRecord> find(String warnId);

    AlertRecord dispose(String warnId, AlertDisposal disposal);

    /** Persists the idempotency evidence for a Runtime-triggered disposal. */
    default AlertRecord dispose(
            String warnId, AlertDisposal disposal, String idempotencyKey) {
        throw new UnsupportedOperationException(
                "Idempotent alert disposal is not configured for this store");
    }

    AlertCenter.AlertPage query(AlertQuery query);
}
