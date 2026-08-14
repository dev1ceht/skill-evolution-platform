package com.example.smartcanteen.http;

import com.example.smartcanteen.application.AuthService;
import com.example.smartcanteen.application.AuthorizationService;
import com.example.smartcanteen.security.AuthPrincipal;
import com.example.smartcanteen.security.AuthTokens;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService auth;
    private final AuthorizationService authorization;

    public AuthController(AuthService auth, AuthorizationService authorization) {
        this.auth = auth;
        this.authorization = authorization;
    }

    @PostMapping("/login")
    public ApiResponse<AuthTokens> login(@Valid @RequestBody LoginRequest request) {
        if (request.loginType() != null && !request.loginType().isBlank()
                && !"account".equalsIgnoreCase(request.loginType())) {
            throw new IllegalArgumentException(
                    "Only account login is configured; wxmp login needs vendor credentials");
        }
        return ApiResponse.ok(auth.login(request.username(), request.password()));
    }

    @PostMapping("/refresh-token")
    public ApiResponse<AuthTokens> refresh(@Valid @RequestBody RefreshRequest request) {
        return ApiResponse.ok(auth.refresh(request.refreshToken()));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@RequestBody(required = false) LogoutRequest request) {
        auth.logout(request == null ? null : request.refreshToken());
        return ApiResponse.ok(null);
    }

    @GetMapping("/me")
    public ApiResponse<CurrentUser> me(HttpServletRequest request) {
        Object value = request.getAttribute(AuthPrincipal.class.getName());
        if (!(value instanceof AuthPrincipal principal)) {
            throw new IllegalArgumentException("Authentication is required");
        }
        return ApiResponse.ok(CurrentUser.from(principal, authorization));
    }

    public record LoginRequest(
            @NotBlank String username,
            @NotBlank String password,
            String loginType) {
    }

    public record RefreshRequest(@NotBlank String refreshToken) {
    }

    public record LogoutRequest(String refreshToken) {
    }

    public record CurrentUser(
            String userId,
            String username,
            String nickname,
            String role,
            String schoolId,
            String canteenId,
            Set<String> roles,
            Set<String> permissions) {

        static CurrentUser from(AuthPrincipal principal, AuthorizationService authorization) {
            return new CurrentUser(
                    principal.userId(),
                    principal.username(),
                    principal.displayName(),
                    principal.role().name(),
                    principal.schoolId(),
                    principal.canteenId(),
                    authorization.rolesFor(principal).stream()
                            .map(Enum::name)
                            .collect(Collectors.toUnmodifiableSet()),
                    authorization.permissionsFor(principal));
        }
    }
}
