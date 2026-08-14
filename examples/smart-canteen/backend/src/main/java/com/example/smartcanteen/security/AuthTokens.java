package com.example.smartcanteen.security;

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
            String canteenId) {
    }
}
