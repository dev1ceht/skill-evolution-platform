package com.example.smartcanteen.domain;

import com.example.smartcanteen.security.Role;
import java.util.List;
import java.util.Set;

public record ManagedUser(
        String userId,
        String username,
        String displayName,
        Role primaryRole,
        Set<Role> roles,
        String schoolId,
        String canteenId,
        boolean active,
        List<ScopeGrant> scopeGrants) {

    public ManagedUser {
        roles = roles == null ? Set.of() : Set.copyOf(roles);
        scopeGrants = scopeGrants == null ? List.of() : List.copyOf(scopeGrants);
    }
}
