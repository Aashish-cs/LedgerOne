package com.ledgerone.repository;

import com.ledgerone.entity.RefreshToken;
import com.ledgerone.entity.UserAccount;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    void deleteByUser(UserAccount user);
}
