package com.ledgerone.service;

import com.ledgerone.audit.AuditService;
import com.ledgerone.dto.AuthDtos;
import com.ledgerone.entity.AuditAction;
import com.ledgerone.entity.RefreshToken;
import com.ledgerone.entity.Role;
import com.ledgerone.entity.RoleName;
import com.ledgerone.entity.UserAccount;
import com.ledgerone.exception.BadRequestException;
import com.ledgerone.exception.ConflictException;
import com.ledgerone.exception.ResourceNotFoundException;
import com.ledgerone.mapper.UserMapper;
import com.ledgerone.repository.RefreshTokenRepository;
import com.ledgerone.repository.RoleRepository;
import com.ledgerone.repository.UserAccountRepository;
import com.ledgerone.security.JwtService;
import com.ledgerone.security.SecurityProperties;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthenticationService {
    private final UserAccountRepository userRepository;
    private final RoleRepository roleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final SecurityProperties securityProperties;
    private final UserMapper userMapper;
    private final AuditService auditService;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public AuthDtos.AuthResponse register(AuthDtos.RegisterRequest request, String ipAddress) {
        if (userRepository.existsByEmailIgnoreCase(request.email())) {
            throw new ConflictException("An account already exists for this email");
        }
        Role userRole = roleRepository.findByName(RoleName.USER).orElseThrow(() -> new ResourceNotFoundException("USER role missing"));
        UserAccount user = new UserAccount();
        user.setEmail(request.email().toLowerCase());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setFullName(request.fullName());
        user.setAccountCashBalance(new BigDecimal("100000.0000"));
        user.getRoles().add(userRole);
        UserAccount savedUser = userRepository.save(user);

        auditService.record(savedUser, AuditAction.PROFILE_UPDATE, "Registration", "User registered", ipAddress);
        return issueTokens(savedUser);
    }

    @Transactional
    public AuthDtos.AuthResponse login(AuthDtos.LoginRequest request, String ipAddress) {
        try {
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(request.email(), request.password()));
        } catch (AuthenticationException exception) {
            userRepository.findByEmailIgnoreCase(request.email()).ifPresentOrElse(
                    user -> auditService.record(user, AuditAction.FAILED_LOGIN, "Login", "Failed login attempt", ipAddress),
                    () -> auditService.record(null, AuditAction.FAILED_LOGIN, "Login", "Unknown account login attempt", ipAddress));
            throw new BadCredentialsException("Invalid credentials");
        }
        UserAccount user = userRepository
                .findByEmailIgnoreCase(request.email())
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));
        if (user.isFrozen()) {
            throw new BadRequestException("Account is frozen");
        }
        auditService.record(user, AuditAction.LOGIN, "Login", "Successful login", ipAddress);
        return issueTokens(user);
    }

    @Transactional
    public AuthDtos.AuthResponse refresh(AuthDtos.RefreshRequest request) {
        String tokenHash = hash(request.refreshToken());
        RefreshToken refreshToken = refreshTokenRepository
                .findByTokenHash(tokenHash)
                .orElseThrow(() -> new BadCredentialsException("Invalid refresh token"));
        if (!refreshToken.isUsable()) {
            throw new BadCredentialsException("Invalid refresh token");
        }
        refreshToken.setRevoked(true);
        return issueTokens(refreshToken.getUser());
    }

    @Transactional
    public void logout(String refreshTokenValue) {
        if (refreshTokenValue == null || refreshTokenValue.isBlank()) {
            return;
        }
        refreshTokenRepository.findByTokenHash(hash(refreshTokenValue)).ifPresent(refreshToken -> {
            refreshToken.setRevoked(true);
            auditService.record(refreshToken.getUser(), AuditAction.LOGOUT, "Logout", "Refresh token revoked");
        });
    }

    private AuthDtos.AuthResponse issueTokens(UserAccount user) {
        String accessToken = jwtService.generateAccessToken(user);
        String refreshTokenValue = randomToken();
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(user);
        refreshToken.setTokenHash(hash(refreshTokenValue));
        refreshToken.setExpiresAt(Instant.now().plus(securityProperties.refreshTokenDays(), ChronoUnit.DAYS));
        refreshTokenRepository.save(refreshToken);
        return new AuthDtos.AuthResponse(
                accessToken,
                refreshTokenValue,
                "Bearer",
                jwtService.accessTokenExpiresAt(),
                userMapper.toPrincipal(user));
    }

    private String randomToken() {
        byte[] bytes = new byte[48];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String value) {
        try {
            byte[] hashed = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hashed);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to hash refresh token", exception);
        }
    }
}
