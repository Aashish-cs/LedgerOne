package com.ledgerone.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(SecurityConfigTest.PolicyTestRoutes.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, SecurityConfigTest.PolicyTestRoutes.class})
class SecurityConfigTest {
    @Autowired MockMvc mvc;
    @MockitoBean JwtService jwt;
    @MockitoBean LedgerUserDetailsService users;

    @ParameterizedTest
    @ValueSource(strings = {"/actuator", "/actuator/health", "/actuator/env", "/api/admin/users",
            "/api/v1/admin/users", "/swagger-ui/index.html", "/v3/api-docs"})
    void anonymousCannotAccessDiagnostics(String path) throws Exception {
        mvc.perform(get(path).servletPath(path)).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @WithMockUser(roles = "USER")
    @ValueSource(strings = {"/actuator", "/actuator/health", "/actuator/env", "/api/admin/users",
            "/api/v1/admin/users", "/swagger-ui/index.html", "/v3/api-docs"})
    void standardUserCannotAccessDiagnostics(String path) throws Exception {
        mvc.perform(get(path).servletPath(path)).andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @WithMockUser(roles = "ADMIN")
    @ValueSource(strings = {"/actuator", "/actuator/health", "/actuator/env", "/api/admin/users",
            "/api/v1/admin/users", "/swagger-ui/index.html", "/v3/api-docs"})
    void adminCanAccessDiagnostics(String path) throws Exception {
        mvc.perform(get(path).servletPath(path)).andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/auth/register", "/api/auth/login", "/api/auth/refresh", "/api/auth/logout",
            "/api/v1/auth/register", "/api/v1/auth/login", "/api/v1/auth/refresh", "/api/v1/auth/logout"})
    void explicitAuthenticationPostsRemainPublic(String path) throws Exception {
        mvc.perform(post(path).servletPath(path)).andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/trading/orders", "/api/orders"})
    void tradingRequiresAuthentication(String path) throws Exception {
        mvc.perform(get(path).servletPath(path)).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @WithMockUser(roles = "USER")
    @ValueSource(strings = {"/api/v1/trading/orders", "/api/orders"})
    void authenticatedUsersCanReachTrading(String path) throws Exception {
        mvc.perform(get(path).servletPath(path)).andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/auth/login", "/api/v1/auth/login", "/api/v1/auth/diagnostics", "/api/authenticate/login"})
    void wrongMethodsAndLookalikePathsAreNotPublic(String path) throws Exception {
        mvc.perform(get(path).servletPath(path)).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/livez", "/readyz", "/api/system/status"})
    void publicHealthReadsStayAvailable(String path) throws Exception {
        mvc.perform(get(path).servletPath(path)).andExpect(status().isOk());
        mvc.perform(post(path).servletPath(path)).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/auth/../admin/users", "/actuator/health;ignored=1", "/api/auth/%2e%2e/admin/users"})
    void malformedPathsAreRejectedByTheFirewall(String path) throws Exception {
        mvc.perform(get(URI.create(path)).servletPath(path)).andExpect(status().isBadRequest());
    }

    @Test void allowedCorsPreflightDoesNotNeedAnAuthenticationToken() throws Exception {
        mvc.perform(options("/api/orders").servletPath("/api/orders")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
    }

    @Test void nonCorsOptionsDoesNotBypassAdminAuthorization() throws Exception {
        mvc.perform(options("/actuator/health").servletPath("/actuator/health"))
                .andExpect(status().isUnauthorized());
    }

    // Stub handlers isolate URL-policy tests; they do not add endpoints to the application.
    @RestController
    static class PolicyTestRoutes {
        @GetMapping({"/actuator", "/actuator/health", "/actuator/env", "/api/admin/users", "/api/v1/admin/users",
                "/swagger-ui/index.html", "/v3/api-docs", "/api/v1/trading/orders", "/api/orders",
                "/livez", "/readyz", "/api/system/status"})
        Map<String, String> get() { return Map.of("status", "ok"); }

        @PostMapping({"/api/auth/register", "/api/auth/login", "/api/auth/refresh", "/api/auth/logout",
                "/api/v1/auth/register", "/api/v1/auth/login", "/api/v1/auth/refresh", "/api/v1/auth/logout"})
        Map<String, String> post() { return Map.of("status", "ok"); }
    }
}
