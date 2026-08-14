package com.example.smartcanteen.security;

import java.util.Set;

public record AuthTokens(
        String token,
        String refreshToken,
        long expiresIn,
        UserInfo userInfo) {

    public record UserInfo(
            String userId,
            String username,
            String nickname,
            String role,
            String schoolId,
            String canteenId,
            Set<String> roles) {

        public UserInfo(
                String userId,
                String username,
                String nickname,
                String role,
                String schoolId,
                String canteenId) {
            this(userId, username, nickname, role, schoolId, canteenId, Set.of(role));
        }

        public UserInfo {
            roles = roles == null ? Set.of(role) : Set.copyOf(roles);
        }
    }
}
