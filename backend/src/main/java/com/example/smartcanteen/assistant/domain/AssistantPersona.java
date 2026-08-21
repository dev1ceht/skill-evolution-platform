package com.example.smartcanteen.assistant.domain;

import com.example.smartcanteen.security.Role;
import java.util.Set;

/** User-facing assistant persona derived from server-side roles. */
public enum AssistantPersona {
    EMPLOYEE_STUDENT,
    CANTEEN_OPERATOR,
    CANTEEN_MANAGER;

    public static AssistantPersona fromRoles(Set<Role> roles) {
        Set<Role> assigned = roles == null ? Set.of() : roles;
        if (assigned.contains(Role.SYSTEM_ADMIN) || assigned.contains(Role.SCHOOL_ADMIN)) {
            return CANTEEN_MANAGER;
        }
        if (assigned.contains(Role.CANTEEN_STAFF)) {
            return CANTEEN_OPERATOR;
        }
        return EMPLOYEE_STUDENT;
    }
}
