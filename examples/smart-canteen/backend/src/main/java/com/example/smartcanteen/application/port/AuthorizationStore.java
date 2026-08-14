package com.example.smartcanteen.application.port;

import com.example.smartcanteen.domain.CanteenScope;
import com.example.smartcanteen.domain.ManagedUser;
import com.example.smartcanteen.domain.PermissionDefinition;
import com.example.smartcanteen.domain.RoleDefinition;
import com.example.smartcanteen.domain.ScopeGrant;
import com.example.smartcanteen.security.Role;
import com.example.smartcanteen.security.UserAccount;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface AuthorizationStore {

    List<RoleDefinition> listRoles();

    List<PermissionDefinition> listPermissions();

    Set<Role> rolesForUser(String userId);

    boolean scopeManagementEnabled(String userId);

    Set<String> permissionsForRoles(Set<Role> roles);

    void replaceRolePermissions(Role role, Set<String> permissionCodes);

    boolean canAccessScope(String userId, CanteenScope scope);

    Set<String> allowedSchoolIds(String userId);

    Set<String> allowedCanteenIds(String userId, String schoolId);

    List<ManagedUser> listUsers(
            String schoolId, String canteenId, Set<String> allowedCanteenIds, Boolean active);

    Optional<ManagedUser> findUser(String userId);

    void createUser(UserAccount account);

    void updateUser(
            String userId,
            String displayName,
            Role primaryRole,
            String schoolId,
            String canteenId,
            boolean active,
            String passwordHash);

    void replaceRoles(String userId, Set<Role> roles);

    void replaceScopeGrants(String userId, List<ScopeGrant> grants);
}
