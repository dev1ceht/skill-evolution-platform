package com.example.smartcanteen.security;

import com.example.smartcanteen.application.AuthorizationService;
import com.example.smartcanteen.application.AuthService;
import com.example.smartcanteen.domain.CanteenScope;
import com.example.smartcanteen.http.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthenticationInterceptor implements HandlerInterceptor {

    private final AuthService auth;
    private final ObjectMapper objectMapper;
    private final AuthorizationService authorization;
    private final boolean enabled;

    public AuthenticationInterceptor(
            AuthService auth,
            ObjectMapper objectMapper,
            AuthorizationService authorization,
            @Value("${smart-canteen.security.enabled:true}") boolean enabled) {
        this.auth = auth;
        this.objectMapper = objectMapper;
        this.authorization = authorization;
        this.enabled = enabled;
    }

    @Override
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler) throws IOException {
        if (!enabled || isPublic(request)) {
            return true;
        }
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith("Bearer ")) {
            return reject(response, "Authentication is required");
        }
        try {
            AuthPrincipal principal = auth.principalFromAccessToken(header.substring(7).trim());
            request.setAttribute(AuthPrincipal.class.getName(), principal);
            String schoolId = request.getParameter("schoolId");
            String canteenId = request.getParameter("canteenId");
            if ((schoolId == null) != (canteenId == null)
                    && !(schoolId != null && canteenId == null && allowsSchoolOnlyScope(request))) {
                return reject(
                        response,
                        HttpServletResponse.SC_BAD_REQUEST,
                        40001,
                        "schoolId and canteenId must be provided together");
            }
            if (schoolId != null && canteenId == null
                    && !authorization.canAccessSchool(principal, schoolId)) {
                return reject(
                        response,
                        HttpServletResponse.SC_FORBIDDEN,
                        40300,
                        "User is outside the requested school scope");
            }
            if (schoolId != null && canteenId != null && !authorization.canAccess(
                    principal, new CanteenScope(schoolId, canteenId))) {
                return reject(
                        response,
                        HttpServletResponse.SC_FORBIDDEN,
                        40300,
                        "User is outside the requested school/canteen scope");
            }
            return true;
        } catch (IllegalArgumentException exception) {
            return reject(
                    response,
                    HttpServletResponse.SC_UNAUTHORIZED,
                    40100,
                    exception.getMessage());
        }
    }

    private boolean reject(HttpServletResponse response, String message) throws IOException {
        return reject(response, HttpServletResponse.SC_UNAUTHORIZED, 40100, message);
    }

    private boolean reject(
            HttpServletResponse response, int status, int code, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(
                new ApiResponse<Void>(
                        code,
                        message == null ? "Authentication failed" : message,
                        null)));
        return false;
    }

    private static boolean isPublic(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.equals("/api/v1/auth/login")
                || path.equals("/api/v1/auth/refresh-token")
                || path.equals("/api/v1/auth/logout")
                || path.startsWith("/actuator/")
                || path.equals("/error");
    }

    private static boolean allowsSchoolOnlyScope(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.equals("/api/v1/canteens") || path.equals("/api/v1/users");
    }
}
