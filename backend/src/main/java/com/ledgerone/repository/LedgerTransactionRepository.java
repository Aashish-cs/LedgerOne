package com.ledgerone.repository;

import com.ledgerone.entity.LedgerTransaction;
import com.ledgerone.entity.Portfolio;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LedgerTransactionRepository extends JpaRepository<LedgerTransaction, UUID> {
    @EntityGraph(attributePaths = {"stock", "order"})
    Page<LedgerTransaction> findByPortfolio(Portfolio portfolio, Pageable pageable);

    @EntityGraph(attributePaths = {"stock", "order"})
    List<LedgerTransaction> findTop8ByPortfolioOrderByCreatedAtDesc(Portfolio portfolio);
}
