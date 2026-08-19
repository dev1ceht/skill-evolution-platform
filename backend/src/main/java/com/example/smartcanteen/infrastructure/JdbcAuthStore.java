package com.example.smartcanteen.infrastructure;

import com.example.smartcanteen.application.port.AuthStore;
import com.example.smartcanteen.security.Role;
import com.example.smartcanteen.security.UserAccount;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcAuthStore implements AuthStore {

    private final JdbcTemplate jdbc;

    public JdbcAuthStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<UserAccount> findByUsername(String username) {
        return jdbc.query(
                        "SELECT user_id, username, password_hash, display_name, role, "
                                + "school_id, canteen_id, status FROM app_users WHERE username = ?",
                        this::mapAccount,
                        username)
                .stream()
                .findFirst();
    }

    @Override
    public Optional<UserAccount> findById(String userId) {
        return jdbc.query(
                        "SELECT user_id, username, password_hash, display_name, role, "
                                + "school_id, canteen_id, status FROM app_users WHERE user_id = ?",
                        this::mapAccount,
                        userId)
                .stream()
                .findFirst();
    }

    @Override
    public Set<Role> findRolesForUser(String userId) {
        return jdbc.query(
                        "SELECT role_code FROM user_roles WHERE user_id = ?",
                        (result, row) -> Role.valueOf(result.getString("role_code")),
                        userId)
                .stream()
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public void saveRefreshSession(
            String sessionId, String userId, String tokenHash, Instant expiresAt) {
        jdbc.update(
                "INSERT INTO auth_refresh_sessions "
                        + "(session_id, user_id, refresh_token_hash, expires_at) VALUES (?, ?, ?, ?)",
                sessionId,
                userId,
                tokenHash,
                Timestamp.from(expiresAt));
    }

    @Override
    public Optional<RefreshSession> findRefreshSession(String tokenHash) {
        return jdbc.query(
                        "SELECT s.session_id, s.expires_at, u.user_id, u.username, "
                                + "u.password_hash, u.display_name, u.role, u.school_id, "
                                + "u.canteen_id, u.status "
                                + "FROM auth_refresh_sessions s JOIN app_users u ON u.user_id = s.user_id "
                                + "WHERE s.refresh_token_hash = ? AND s.revoked_at IS NULL "
                                + "AND s.expires_at > CURRENT_TIMESTAMP",
                        (result, row) -> new RefreshSession(
                                result.getString("session_id"),
                                mapAccount(result, row),
                                result.getTimestamp("expires_at").toInstant()),
                        tokenHash)
                .stream()
                .findFirst();
    }

    @Override
    public void revokeRefreshSession(String tokenHash) {
        jdbc.update(
                "UPDATE auth_refresh_sessions SET revoked_at = CURRENT_TIMESTAMP "
                        + "WHERE refresh_token_hash = ? AND revoked_at IS NULL",
                tokenHash);
    }

    @Override
    public void ensureBootstrapAdmin(String username, String passwordHash, String displayName) {
        try {
            jdbc.update(
                    "INSERT INTO app_users "
                            + "(user_id, username, password_hash, display_name, role, status) "
                            + "VALUES (?, ?, ?, ?, 'SYSTEM_ADMIN', 'ACTIVE')",
                    "USER-BOOTSTRAP-ADMIN",
                    username,
                    passwordHash,
                    displayName);
        } catch (DuplicateKeyException ignored) {
            // Bootstrap is intentionally create-once; it never overwrites a live password.
        }
        try {
            jdbc.update(
                    "INSERT INTO user_roles (user_id, role_code) VALUES (?, 'SYSTEM_ADMIN')",
                    "USER-BOOTSTRAP-ADMIN");
        } catch (DuplicateKeyException ignored) {
            // The bootstrap role assignment is also create-once.
        }
    }

    private UserAccount mapAccount(java.sql.ResultSet result, int row) throws java.sql.SQLException {
        Role primaryRole = Role.valueOf(result.getString("role"));
        Set<Role> roles = findRolesForUser(result.getString("user_id"));
        return new UserAccount(
                result.getString("user_id"),
                result.getString("username"),
                result.getString("password_hash"),
                result.getString("display_name"),
                primaryRole,
                result.getString("school_id"),
                result.getString("canteen_id"),
                "ACTIVE".equals(result.getString("status")),
                roles.isEmpty() ? Set.of(primaryRole) : roles);
    }
}
