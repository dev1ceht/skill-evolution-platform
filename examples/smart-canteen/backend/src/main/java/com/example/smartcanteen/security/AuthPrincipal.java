package com.example.smartcanteen.security;

public record AuthPrincipal(
        String userId,
        String username,
        String displayName,
        Role role,
        String schoolId,
        String canteenId) {

    public boolean canAccess(String requestedSchoolId, String requestedCanteenId) {
        if (role == Role.SYSTEM_ADMIN || role == Role.REGULATOR) {
            return true;
        }
        return equalsNullable(schoolId, requestedSchoolId)
                && equalsNullable(canteenId, requestedCanteenId);
    }

    private static boolean equalsNullable(String left, String right) {
        return left == null ? right == null : left.equals(right);
    }
}
