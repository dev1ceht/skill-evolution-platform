package com.example.smartcanteen.application;

import com.example.smartcanteen.domain.CanteenScope;
import com.example.smartcanteen.security.ForbiddenException;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Resolves the one operational canteen used by this deployment.
 *
 * <p>The persistence model still carries the legacy scope columns while the codebase is being
 * simplified. They are treated as storage details here; callers cannot switch the business
 * context by sending another school or canteen id.</p>
 */
@Component
public final class SingleCanteenContext {

    private final boolean fixed;
    private final CanteenScope scope;

    public SingleCanteenContext(
            @Value("${smart-canteen.canteen.single-mode:true}") boolean fixed,
            @Value("${smart-canteen.canteen.school-id:SCHOOL-001}") String schoolId,
            @Value("${smart-canteen.canteen.id:CANTEEN-001}") String canteenId) {
        this.fixed = fixed;
        this.scope = new CanteenScope(
                Objects.requireNonNull(schoolId, "schoolId"),
                Objects.requireNonNull(canteenId, "canteenId"));
    }

    public CanteenScope scope() {
        return scope;
    }

    /** Resolves an HTTP/Agent supplied scope without allowing cross-canteen access in production. */
    public CanteenScope resolve(String schoolId, String canteenId) {
        if (!fixed) {
            return new CanteenScope(
                    Objects.requireNonNull(schoolId, "schoolId"),
                    Objects.requireNonNull(canteenId, "canteenId"));
        }
        if (schoolId == null || schoolId.isBlank() || canteenId == null || canteenId.isBlank()) {
            return scope;
        }
        if (!scope.schoolId().equals(schoolId) || !scope.canteenId().equals(canteenId)) {
            throw new ForbiddenException("This deployment operates one fixed canteen only");
        }
        return scope;
    }

    public CanteenScope resolve(CanteenScope requested) {
        Objects.requireNonNull(requested, "requested");
        return resolve(requested.schoolId(), requested.canteenId());
    }
}
