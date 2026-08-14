package com.example.smartcanteen.application.port;

import com.example.smartcanteen.security.UserAccount;
import java.time.Instant;
import java.util.Optional;

public interface AuthStore {

    Optional<UserAccount> findByUsername(String username);

    Optional<UserAccount> findById(String userId);

    void saveRefreshSession(String sessionId, String userId, String tokenHash, Instant expiresAt);

    Optional<RefreshSession> findRefreshSession(String tokenHash);

    void revokeRefreshSession(String tokenHash);

    void ensureBootstrapAdmin(String username, String passwordHash, String displayName);

    record RefreshSession(String sessionId, UserAccount account, Instant expiresAt) {
    }
}
