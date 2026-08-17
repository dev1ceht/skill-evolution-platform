package com.example.smartcanteen.application;

import com.example.smartcanteen.application.port.AuditStore;
import com.example.smartcanteen.application.port.OrganizationStore;
import com.example.smartcanteen.domain.AuditLog;
import com.example.smartcanteen.domain.Canteen;
import com.example.smartcanteen.domain.CanteenScope;
import com.example.smartcanteen.domain.School;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrganizationService {

    private final OrganizationStore store;
    private final AuditStore audits;

    public OrganizationService(OrganizationStore store, AuditStore audits) {
        this.store = store;
        this.audits = audits;
    }

    public List<School> listSchools(Set<String> allowedSchoolIds, String keyword, boolean includeInactive) {
        return store.listSchools(allowedSchoolIds, keyword, includeInactive);
    }

    public Optional<School> findSchool(String schoolId) {
        return store.findSchool(schoolId);
    }

    @Transactional
    public School createSchool(School school, String actorUserId) {
        School created = store.createSchool(school);
        audit(actorUserId, "CREATE", "SCHOOL", created.id(), created.id(), null, "created school");
        return created;
    }

    @Transactional
    public School updateSchool(School school, String actorUserId) {
        School updated = store.updateSchool(school);
        audit(actorUserId, "UPDATE", "SCHOOL", updated.id(), updated.id(), null, "updated school");
        return updated;
    }

    @Transactional
    public School updateSchoolStatus(String schoolId, boolean active, String actorUserId) {
        store.updateSchoolStatus(schoolId, active);
        School updated = store.findSchool(schoolId).orElseThrow();
        audit(actorUserId, "STATUS", "SCHOOL", schoolId, schoolId, null,
                active ? "enabled school" : "disabled school");
        return updated;
    }

    public List<Canteen> listCanteens(
            Set<String> allowedSchoolIds,
            Set<String> allowedCanteenIds,
            String schoolId,
            String keyword,
            boolean includeInactive) {
        return store.listCanteens(
                allowedSchoolIds, allowedCanteenIds, schoolId, keyword, includeInactive);
    }

    public Optional<Canteen> findCanteen(String canteenId) {
        return store.findCanteen(canteenId);
    }

    public boolean isActiveScope(CanteenScope scope) {
        Optional<School> school = findSchool(scope.schoolId());
        if (school.isEmpty()) {
            return false;
        }
        if (!school.get().active()) {
            return false;
        }
        Optional<Canteen> canteen = findCanteen(scope.canteenId());
        return canteen.isPresent()
                && canteen.get().active()
                && scope.schoolId().equals(canteen.get().schoolId());
    }

    public boolean isKnownScope(CanteenScope scope) {
        return findSchool(scope.schoolId()).flatMap(school -> findCanteen(scope.canteenId()))
                .map(canteen -> scope.schoolId().equals(canteen.schoolId()))
                .orElse(false);
    }

    @Transactional
    public Canteen createCanteen(Canteen canteen, String actorUserId) {
        School school = store.findSchool(canteen.schoolId())
                .orElseThrow(() -> new IllegalArgumentException("school not found: " + canteen.schoolId()));
        if (!school.active()) {
            throw new IllegalArgumentException("cannot create canteen under disabled school");
        }
        Canteen created = store.createCanteen(canteen);
        audit(actorUserId, "CREATE", "CANTEEN", created.id(), created.schoolId(), created.id(), "created canteen");
        return store.findCanteen(created.id()).orElse(created);
    }

    @Transactional
    public Canteen updateCanteen(Canteen canteen, String actorUserId) {
        Canteen updated = store.updateCanteen(canteen);
        audit(actorUserId, "UPDATE", "CANTEEN", updated.id(), updated.schoolId(), updated.id(), "updated canteen");
        return updated;
    }

    @Transactional
    public Canteen updateCanteenStatus(String canteenId, boolean active, String actorUserId) {
        store.updateCanteenStatus(canteenId, active);
        Canteen updated = store.findCanteen(canteenId).orElseThrow();
        audit(actorUserId, "STATUS", "CANTEEN", canteenId, updated.schoolId(), canteenId,
                active ? "enabled canteen" : "disabled canteen");
        return updated;
    }

    private void audit(
            String actorUserId,
            String action,
            String resourceType,
            String resourceId,
            String schoolId,
            String canteenId,
            String detail) {
        audits.append(new AuditLog(
                "AUDIT-" + UUID.randomUUID(), actorUserId, action, resourceType, resourceId,
                schoolId, canteenId, "SUCCESS", detail, null, Instant.now()));
    }
}
