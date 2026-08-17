package com.example.smartcanteen.application;

import com.example.smartcanteen.application.port.AuthStore;
import com.example.smartcanteen.security.AuthPrincipal;
import com.example.smartcanteen.security.AuthTokens;
import com.example.smartcanteen.security.PasswordHasher;
import com.example.smartcanteen.security.TokenService;
import com.example.smartcanteen.security.UserAccount;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.stream.Collectors;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final long REFRESH_DAYS = 7;
    private final AuthStore store;
    private final PasswordHasher passwords;
    private final TokenService tokens;

    public AuthService(AuthStore store, PasswordHasher passwords, TokenService tokens) {
        this.store = store;
        this.passwords = passwords;
        this.tokens = tokens;
    }

    @Transactional
    public AuthTokens login(String username, String password) {
        UserAccount account = store.findByUsername(require(username, "username"))
                .filter(UserAccount::active)
                .filter(value -> passwords.matches(password, value.passwordHash()))
                .orElseThrow(() -> new IllegalArgumentException("Invalid username or password"));
        return issue(account);
    }

    @Transactional
    public AuthTokens refresh(String refreshToken) {
        String raw = require(refreshToken, "refreshToken");
        String hash = tokens.hashRefreshToken(raw);
        AuthStore.RefreshSession session = store.findRefreshSession(hash)
                .orElseThrow(() -> new IllegalArgumentException("Refresh token is invalid or expired"));
        if (!session.account().active()) {
            throw new IllegalArgumentException("Refresh token is invalid or expired");
        }
        store.revokeRefreshSession(hash);
        return issue(session.account());
    }

    @Transactional
    public void logout(String refreshToken) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            store.revokeRefreshSession(tokens.hashRefreshToken(refreshToken.trim()));
        }
    }

    public AuthPrincipal principalFromAccessToken(String token) {
        AuthPrincipal principal = tokens.verifyAccess(token);
        UserAccount account = store.findById(principal.userId())
                .orElseThrow(() -> new IllegalArgumentException("User account no longer exists"));
        if (!account.active()) {
            throw new IllegalArgumentException("User account is disabled");
        }
        return principal;
    }

    /** Rebuilds a principal from current persisted account state for async Agent execution. */
    public AuthPrincipal principalForUser(String userId) {
        UserAccount account = store.findById(userId)
                .filter(UserAccount::active)
                .orElseThrow(() -> new IllegalArgumentException(
                        "User account is no longer active: " + userId));
        return new AuthPrincipal(
                account.userId(),
                account.username(),
                account.displayName(),
                account.role(),
                account.schoolId(),
                account.canteenId(),
                account.roles());
    }

    private AuthTokens issue(UserAccount account) {
        TokenService.IssuedAccessToken access = tokens.issueAccess(account);
        String refresh = tokens.newRefreshToken();
        store.saveRefreshSession(
                UUID.randomUUID().toString(),
                account.userId(),
                tokens.hashRefreshToken(refresh),
                Instant.now().plus(REFRESH_DAYS, ChronoUnit.DAYS));
        return new AuthTokens(
                access.value(),
                refresh,
                access.expiresIn(),
                new AuthTokens.UserInfo(
                        account.userId(),
                        account.username(),
                        account.displayName(),
                        account.role().name(),
                        account.schoolId(),
                        account.canteenId(),
                        account.roles().stream()
                                .map(Enum::name)
                                .collect(Collectors.toUnmodifiableSet())));
    }

    private static String require(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }
}
