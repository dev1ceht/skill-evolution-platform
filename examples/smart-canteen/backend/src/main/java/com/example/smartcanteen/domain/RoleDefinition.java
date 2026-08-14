package com.example.smartcanteen.domain;

import com.example.smartcanteen.security.Role;
import java.util.Set;

public record RoleDefinition(
        Role code,
        String name,
        String description,
        boolean systemRole,
        boolean active,
        Set<String> permissions) {

    public RoleDefinition {
        permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
    }
}
