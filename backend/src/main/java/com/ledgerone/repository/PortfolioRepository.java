package com.ledgerone.repository;

import com.ledgerone.entity.Portfolio;
import com.ledgerone.entity.UserAccount;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PortfolioRepository extends JpaRepository<Portfolio, UUID> {
    @EntityGraph(attributePaths = "holdings")
    List<Portfolio> findByUserAndActiveTrue(UserAccount user);

    Optional<Portfolio> findByIdAndUserAndActiveTrue(UUID id, UserAccount user);

    Optional<Portfolio> findFirstByUserAndActiveTrueOrderByCreatedAtAsc(UserAccount user);

    boolean existsByUserAndActiveTrueAndNameIgnoreCase(UserAccount user, String name);

    boolean existsByUserAndActiveTrueAndNameIgnoreCaseAndIdNot(UserAccount user, String name, UUID id);
}
