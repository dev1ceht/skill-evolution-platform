package com.example.smartcanteen.application.port;

import com.example.smartcanteen.domain.AuditLog;
import java.util.List;

public interface AuditStore {

    void append(AuditLog auditLog);

    List<AuditLog> listRecent(int limit);
}
