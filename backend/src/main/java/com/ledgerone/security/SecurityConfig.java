package com.ledgerone.security;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
@EnableConfigurationProperties(SecurityProperties.class)
public class SecurityConfig {
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final LedgerUserDetailsService userDetailsService;
    private final SecurityProperties securityProperties;

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) -> {
                            response.setStatus(401);
                            response.setContentType("application/json");
                            response.getWriter().write("{\"success\":false,\"message\":\"Authentication required\"}");
                        })
                        .accessDeniedHandler((request, response, exception) -> {
                            response.setStatus(403);
                            response.setContentType("application/json");
                            response.getWriter().write("{\"success\":false,\"message\":\"Access denied\"}");
                        }))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                new AntPathRequestMatcher("/actuator/**"),
                                new AntPathRequestMatcher("/api/admin/**"),
                                new AntPathRequestMatcher("/api/v1/admin/**"),
                                new AntPathRequestMatcher("/swagger-ui/**"),
                                new AntPathRequestMatcher("/v3/api-docs/**"),
                                new AntPathRequestMatcher("/swagger-ui.html"))
                        .hasRole("ADMIN")
                        .requestMatchers(
                                new AntPathRequestMatcher("/api/auth/register", "POST"),
                                new AntPathRequestMatcher("/api/auth/login", "POST"),
                                new AntPathRequestMatcher("/api/auth/refresh", "POST"),
                                new AntPathRequestMatcher("/api/auth/logout", "POST"),
                                new AntPathRequestMatcher("/api/v1/auth/register", "POST"),
                                new AntPathRequestMatcher("/api/v1/auth/login", "POST"),
                                new AntPathRequestMatcher("/api/v1/auth/refresh", "POST"),
                                new AntPathRequestMatcher("/api/v1/auth/logout", "POST"))
                        .permitAll()
                        .requestMatchers(
                                new AntPathRequestMatcher("/api/system/status", "GET"),
                                new AntPathRequestMatcher("/livez", "GET"),
                                new AntPathRequestMatcher("/readyz", "GET"))
                        .permitAll()
                        .requestMatchers(
                                new AntPathRequestMatcher("/api/v1/trading/**"),
                                new AntPathRequestMatcher("/api/orders/**"))
                        .authenticated()
                        .anyRequest()
                        .authenticated())
                .authenticationProvider(authenticationProvider())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(securityProperties.allowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of(HttpHeaders.AUTHORIZATION, HttpHeaders.CONTENT_TYPE));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
