package com.ledgerone.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ledgerone.entity.Role;
import com.ledgerone.entity.RoleName;
import com.ledgerone.entity.UserAccount;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JwtServiceTest {
    private final JwtService jwtService = new JwtService(
            new ObjectMapper(),
            new SecurityProperties("test-secret-with-at-least-32-bytes", 20, 7, List.of("http://localhost:5173")));

    @Test
    void generatedTokenCanBeParsed() {
        UserAccount user = new UserAccount();
        user.setId(UUID.randomUUID());
        user.setEmail("analyst@ledgerone.com");
        user.setFullName("Analyst User");
        Role role = new Role();
        role.setName(RoleName.USER);
        user.getRoles().add(role);

        String token = jwtService.generateAccessToken(user);

        assertThat(jwtService.parse(token))
                .isPresent()
                .get()
                .satisfies(claims -> {
                    assertThat(claims.subject()).isEqualTo("analyst@ledgerone.com");
                    assertThat(claims.roles()).containsExactly(RoleName.USER);
                });
    }
}
