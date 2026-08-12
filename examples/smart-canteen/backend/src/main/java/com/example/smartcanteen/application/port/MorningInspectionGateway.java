package com.example.smartcanteen.application.port;

import com.example.smartcanteen.domain.AlertReport;
import java.util.List;

/** Replaceable boundary for morning-inspection instruments and check records. */
public interface MorningInspectionGateway {

    void syncPeople(Session session, List<Person> people);

    List<CheckRecord> records(Session session, String schoolId);

    List<AlertReport> warnings(Session session, String schoolId);

    record Session(String accessToken, java.time.Instant expiresAt) {
    }

    record Person(String personId, String name, String role) {
    }

    record CheckRecord(String recordId, String personId, java.time.Instant checkedAt,
                       String result) {
    }
}
