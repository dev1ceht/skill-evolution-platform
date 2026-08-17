package com.example.smartcanteen.application;

import com.example.smartcanteen.application.port.AlertCenter;
import com.example.smartcanteen.application.port.AlertStore;
import com.example.smartcanteen.domain.AlertDisposal;
import com.example.smartcanteen.domain.AlertQuery;
import com.example.smartcanteen.domain.AlertRecord;
import com.example.smartcanteen.domain.AlertReport;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Objects;
import java.util.Optional;

@Service
public class AlertCenterService implements AlertCenter {

    private final AlertStore store;

    public AlertCenterService(AlertStore store) {
        this.store = store;
    }

    @Override
    @Transactional
    public AlertRecord report(AlertReport report) {
        return store.report(Objects.requireNonNull(report, "report is required"));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AlertRecord> find(String warnId) {
        if (warnId == null || warnId.isBlank()) {
            return Optional.empty();
        }
        return store.find(warnId.trim());
    }

    @Override
    @Transactional
    public AlertRecord dispose(String warnId, AlertDisposal disposal) {
        if (warnId == null || warnId.isBlank()) {
            throw new IllegalArgumentException("warnId is required");
        }
        return store.dispose(
                warnId.trim(),
                Objects.requireNonNull(disposal, "disposal is required"));
    }

    @Override
    @Transactional(readOnly = true)
    public AlertPage query(AlertQuery query) {
        return store.query(Objects.requireNonNull(query, "query is required"));
    }
}
