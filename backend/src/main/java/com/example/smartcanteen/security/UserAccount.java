package com.example.smartcanteen.security;

import java.util.Set;

public record UserAccount(
        String userId,
        String username,
        String passwordHash,
        String displayName,
        Role role,
        String schoolId,
        String canteenId,
        boolean active,
        Set<Role> roles) {

    public UserAccount(
            String userId,
            String username,
            String passwordHash,
            String displayName,
            Role role,
            String schoolId,
            String canteenId,
            boolean active) {
        this(userId, username, passwordHash, displayName, role, schoolId, canteenId, active, Set.of(role));
    }

    public UserAccount {
        roles = roles == null || roles.isEmpty()
                ? Set.of(role)
                : Set.copyOf(roles);
    }
}
