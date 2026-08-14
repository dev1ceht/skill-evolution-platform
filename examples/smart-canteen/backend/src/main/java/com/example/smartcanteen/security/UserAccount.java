package com.example.smartcanteen.security;

public record UserAccount(
        String userId,
        String username,
        String passwordHash,
        String displayName,
        Role role,
        String schoolId,
        String canteenId,
        boolean active) {
}
