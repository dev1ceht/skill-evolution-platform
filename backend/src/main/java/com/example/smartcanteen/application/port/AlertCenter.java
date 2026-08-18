package com.example.smartcanteen.application.port;

import com.example.smartcanteen.domain.AlertDisposal;
import com.example.smartcanteen.domain.AlertQuery;
import com.example.smartcanteen.domain.AlertRecord;
import com.example.smartcanteen.domain.AlertReport;
import java.util.List;
import java.util.Optional;

/** Public deep-module seam for normalized alert ingestion and disposal. */
public interface AlertCenter {

    AlertRecord report(AlertReport report);

    Optional<AlertRecord> find(String warnId);

    AlertRecord dispose(String warnId, AlertDisposal disposal);

    /** Agent/worker seam carrying the stable Step business idempotency key. */
    default AlertRecord dispose(
            String warnId, AlertDisposal disposal, String idempotencyKey) {
        throw new UnsupportedOperationException(
                "Idempotent alert disposal is not configured for this center");
    }

    AlertPage query(AlertQuery query);

    record AlertPage(List<AlertRecord> records, int pageNum, int pageSize, long total) {
        public AlertPage {
            records = List.copyOf(records);
            if (pageNum < 1 || pageSize < 1 || total < 0) {
                throw new IllegalArgumentException("Invalid alert page");
            }
        }
    }
}
