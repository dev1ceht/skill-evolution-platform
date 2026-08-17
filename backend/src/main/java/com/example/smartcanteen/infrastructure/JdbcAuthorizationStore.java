package com.example.smartcanteen.infrastructure;

import com.example.smartcanteen.application.port.AuthorizationStore;
import com.example.smartcanteen.domain.CanteenScope;
import com.example.smartcanteen.domain.ManagedUser;
import com.example.smartcanteen.domain.PermissionDefinition;
import com.example.smartcanteen.domain.RoleDefinition;
import com.example.smartcanteen.domain.ScopeGrant;
import com.example.smartcanteen.domain.ScopeGrantType;
import com.example.smartcanteen.security.Role;
import com.example.smartcanteen.security.UserAccount;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcAuthorizationStore implements AuthorizationStore {

    private final JdbcTemplate jdbc;

    public JdbcAuthorizationStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<RoleDefinition> listRoles() {
        return jdbc.query(
                        "SELECT role_code, name, description, system_role, status "
                                + "FROM roles ORDER BY role_code",
                        (result, row) -> {
                            Role role = Role.valueOf(result.getString("role_code"));
                            return new RoleDefinition(
                                    role,
                                    result.getString("name"),
                                    result.getString("description"),
                                    result.getBoolean("system_role"),
                                    "ACTIVE".equals(result.getString("status")),
                                    permissionsForRoles(Set.of(role)));
                        })
                .stream()
                .toList();
    }

    @Override
    public List<PermissionDefinition> listPermissions() {
        return jdbc.query(
                "SELECT permission_code, name, resource, action, description "
                        + "FROM permissions ORDER BY permission_code",
                (result, row) -> new PermissionDefinition(
                        result.getString("permission_code"),
                        result.getString("name"),
                        result.getString("resource"),
                        result.getString("action"),
                        result.getString("description")));
    }

    @Override
    public Set<Role> rolesForUser(String userId) {
        Set<Role> roles = jdbc.query(
                        "SELECT role_code FROM user_roles WHERE user_id = ? ORDER BY role_code",
                        (result, row) -> Role.valueOf(result.getString("role_code")),
                        userId)
                .stream()
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(Role.class)));
        if (!roles.isEmpty()) {
            return Set.copyOf(roles);
        }
        return jdbc.query(
                        "SELECT role FROM app_users WHERE user_id = ?",
                        (result, row) -> Role.valueOf(result.getString("role")),
                        userId)
                .stream()
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public boolean scopeManagementEnabled(String userId) {
        return jdbc.query(
                        "SELECT scope_management_enabled FROM app_users WHERE user_id = ?",
                        (result, row) -> result.getBoolean("scope_management_enabled"),
                        userId)
                .stream()
                .findFirst()
                .orElse(false);
    }

    @Override
    public Set<String> permissionsForRoles(Set<Role> roles) {
        if (roles == null || roles.isEmpty()) {
            return Set.of();
        }
        String placeholders = "?,".repeat(roles.size());
        placeholders = placeholders.substring(0, placeholders.length() - 1);
        return jdbc.query(
                        "SELECT permission_code FROM role_permissions WHERE role_code IN ("
                                + placeholders + ")",
                        (result, row) -> result.getString("permission_code"),
                        roles.stream().map(Role::name).toArray())
                .stream()
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public void replaceRolePermissions(Role role, Set<String> permissionCodes) {
        jdbc.update("DELETE FROM role_permissions WHERE role_code = ?", role.name());
        if (permissionCodes == null) {
            return;
        }
        for (String permissionCode : permissionCodes) {
            jdbc.update(
                    "INSERT INTO role_permissions (role_code, permission_code) VALUES (?, ?)",
                    role.name(), permissionCode);
        }
    }

    @Override
    public boolean canAccessScope(String userId, CanteenScope scope) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_scope_assignments a "
                        + "WHERE a.user_id = ? "
                        + "AND EXISTS (SELECT 1 FROM canteens c "
                        + "WHERE c.id = ? AND c.school_id = ?) "
                        + "AND ("
                        + "(a.scope_type = 'CANTEEN' AND a.school_id = ? AND a.canteen_id = ?) "
                        + "OR (a.scope_type = 'SCHOOL' AND a.school_id = ?) "
                        + "OR (a.scope_type = 'REGION' AND a.region_code = "
                        + "(SELECT s.region_code FROM schools s WHERE s.id = ?))"
                        + ")",
                Integer.class,
                userId,
                scope.canteenId(),
                scope.schoolId(),
                scope.schoolId(),
                scope.canteenId(),
                scope.schoolId(),
                scope.schoolId());
        return count != null && count > 0;
    }

    @Override
    public Set<String> allowedSchoolIds(String userId) {
        return jdbc.query(
                        "SELECT DISTINCT s.id FROM schools s "
                                + "WHERE EXISTS (SELECT 1 FROM user_scope_assignments a "
                                + "WHERE a.user_id = ? AND ("
                                + "(a.scope_type = 'REGION' AND a.region_code = s.region_code) "
                                + "OR (a.scope_type = 'SCHOOL' AND a.school_id = s.id) "
                                + "OR (a.scope_type = 'CANTEEN' AND EXISTS ("
                                + "SELECT 1 FROM canteens c WHERE c.id = a.canteen_id AND c.school_id = s.id))"
                                + ")) ORDER BY s.id",
                        (result, row) -> result.getString("id"),
                        userId)
                .stream()
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public Set<String> allowedCanteenIds(String userId, String schoolId) {
        StringBuilder sql = new StringBuilder(
                "SELECT DISTINCT c.id FROM canteens c JOIN schools s ON s.id = c.school_id "
                        + "WHERE EXISTS (SELECT 1 FROM user_scope_assignments a "
                        + "WHERE a.user_id = ? AND ("
                        + "(a.scope_type = 'REGION' AND a.region_code = s.region_code) "
                        + "OR (a.scope_type = 'SCHOOL' AND a.school_id = c.school_id) "
                        + "OR (a.scope_type = 'CANTEEN' AND a.canteen_id = c.id)))");
        List<Object> args = new ArrayList<>();
        args.add(userId);
        if (schoolId != null && !schoolId.isBlank()) {
            sql.append(" AND c.school_id = ?");
            args.add(schoolId.trim());
        }
        sql.append(" ORDER BY c.id");
        return jdbc.query(sql.toString(), (result, row) -> result.getString("id"), args.toArray())
                .stream()
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public List<ManagedUser> listUsers(
            String schoolId, String canteenId, Set<String> allowedCanteenIds, Boolean active) {
        if (allowedCanteenIds != null && allowedCanteenIds.isEmpty()) {
            return List.of();
        }
        StringBuilder sql = new StringBuilder(
                "SELECT user_id, username, display_name, role, school_id, canteen_id, status "
                        + "FROM app_users WHERE 1 = 1");
        List<Object> args = new ArrayList<>();
        if (schoolId != null && !schoolId.isBlank()) {
            sql.append(" AND school_id = ?");
            args.add(schoolId.trim());
        }
        if (canteenId != null && !canteenId.isBlank()) {
            sql.append(" AND canteen_id = ?");
            args.add(canteenId.trim());
        }
        appendCanteenFilter(sql, args, "canteen_id", allowedCanteenIds);
        if (active != null) {
            sql.append(" AND status = ?");
            args.add(active ? "ACTIVE" : "DISABLED");
        }
        sql.append(" ORDER BY user_id");
        return jdbc.query(sql.toString(), this::mapManagedUser, args.toArray());
    }

    @Override
    public Optional<ManagedUser> findUser(String userId) {
        return jdbc.query(
                        "SELECT user_id, username, display_name, role, school_id, canteen_id, status "
                                + "FROM app_users WHERE user_id = ?",
                        this::mapManagedUser,
                        userId)
                .stream()
                .findFirst();
    }

    @Override
    public void createUser(UserAccount account) {
        try {
            jdbc.update(
                    "INSERT INTO app_users "
                            + "(user_id, username, password_hash, display_name, role, school_id, canteen_id, "
                            + "status, scope_management_enabled) VALUES (?, ?, ?, ?, ?, ?, ?, ?, TRUE)",
                    account.userId(), account.username(), account.passwordHash(), account.displayName(),
                    account.role().name(), account.schoolId(), account.canteenId(),
                    account.active() ? "ACTIVE" : "DISABLED");
            replaceRoles(account.userId(), account.roles());
        } catch (DuplicateKeyException exception) {
            throw new IllegalArgumentException("username or user already exists", exception);
        }
    }

    @Override
    public void updateUser(
            String userId,
            String displayName,
            Role primaryRole,
            String schoolId,
            String canteenId,
            boolean active,
            String passwordHash) {
        int updated;
        if (passwordHash == null || passwordHash.isBlank()) {
            updated = jdbc.update(
                    "UPDATE app_users SET display_name = ?, role = ?, school_id = ?, "
                            + "canteen_id = ?, status = ?, updated_at = CURRENT_TIMESTAMP WHERE user_id = ?",
                    displayName, primaryRole.name(), schoolId, canteenId,
                    active ? "ACTIVE" : "DISABLED", userId);
        } else {
            updated = jdbc.update(
                    "UPDATE app_users SET display_name = ?, role = ?, school_id = ?, "
                            + "canteen_id = ?, status = ?, password_hash = ?, updated_at = CURRENT_TIMESTAMP "
                            + "WHERE user_id = ?",
                    displayName, primaryRole.name(), schoolId, canteenId,
                    active ? "ACTIVE" : "DISABLED", passwordHash, userId);
        }
        if (updated == 0) {
            throw new IllegalArgumentException("user not found: " + userId);
        }
    }

    @Override
    public void replaceRoles(String userId, Set<Role> roles) {
        if (roles == null || roles.isEmpty()) {
            throw new IllegalArgumentException("at least one role is required");
        }
        jdbc.update("DELETE FROM user_roles WHERE user_id = ?", userId);
        for (Role role : roles) {
            jdbc.update(
                    "INSERT INTO user_roles (user_id, role_code) VALUES (?, ?)",
                    userId, role.name());
        }
    }

    @Override
    public void replaceScopeGrants(String userId, List<ScopeGrant> grants) {
        jdbc.update(
                "UPDATE app_users SET scope_management_enabled = TRUE, updated_at = CURRENT_TIMESTAMP "
                        + "WHERE user_id = ?",
                userId);
        jdbc.update("DELETE FROM user_scope_assignments WHERE user_id = ?", userId);
        if (grants == null) {
            return;
        }
        for (ScopeGrant grant : grants) {
            jdbc.update(
                    "INSERT INTO user_scope_assignments "
                            + "(assignment_id, user_id, scope_type, region_code, school_id, canteen_id) "
                            + "VALUES (?, ?, ?, ?, ?, ?)",
                    grant.assignmentId(), grant.userId(), grant.type().name(), grant.regionCode(),
                    grant.schoolId(), grant.canteenId());
        }
    }

    private ManagedUser mapManagedUser(ResultSet result, int row) throws SQLException {
        String userId = result.getString("user_id");
        Role primaryRole = Role.valueOf(result.getString("role"));
        Set<Role> roles = new LinkedHashSet<>(rolesForUser(userId));
        roles.add(primaryRole);
        return new ManagedUser(
                userId,
                result.getString("username"),
                result.getString("display_name"),
                primaryRole,
                roles,
                result.getString("school_id"),
                result.getString("canteen_id"),
                "ACTIVE".equals(result.getString("status")),
                scopeGrantsForUser(userId));
    }

    private List<ScopeGrant> scopeGrantsForUser(String userId) {
        return jdbc.query(
                "SELECT assignment_id, user_id, scope_type, region_code, school_id, canteen_id "
                        + "FROM user_scope_assignments WHERE user_id = ? ORDER BY assignment_id",
                (result, row) -> new ScopeGrant(
                        result.getString("assignment_id"),
                        result.getString("user_id"),
                        ScopeGrantType.valueOf(result.getString("scope_type")),
                        result.getString("region_code"),
                        result.getString("school_id"),
                        result.getString("canteen_id")),
                userId);
    }

    private static void appendCanteenFilter(
            StringBuilder sql, List<Object> args, String column, Set<String> allowedCanteenIds) {
        if (allowedCanteenIds == null) {
            return;
        }
        sql.append(" AND ").append(column).append(" IN (")
                .append("?,".repeat(allowedCanteenIds.size()));
        sql.deleteCharAt(sql.length() - 1).append(")");
        args.addAll(allowedCanteenIds);
    }
}
