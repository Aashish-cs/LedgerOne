package com.ledgerone.repository;

import com.ledgerone.entity.Holding;
import com.ledgerone.entity.Portfolio;
import com.ledgerone.entity.Stock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HoldingRepository extends JpaRepository<Holding, UUID> {
    @EntityGraph(attributePaths = "stock")
    List<Holding> findByPortfolioAndQuantityGreaterThan(Portfolio portfolio, java.math.BigDecimal quantity);

    @EntityGraph(attributePaths = "stock")
    List<Holding> findByPortfolio(Portfolio portfolio);

    Optional<Holding> findByPortfolioAndStock(Portfolio portfolio, Stock stock);
}
