package com.ledgerone.repository;

import com.ledgerone.entity.OrderStatus;
import com.ledgerone.entity.Portfolio;
import com.ledgerone.entity.TradeOrder;
import com.ledgerone.entity.UserAccount;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TradeOrderRepository extends JpaRepository<TradeOrder, UUID> {
    Optional<TradeOrder> findByUserAndClientOrderId(UserAccount user, String clientOrderId);

    @EntityGraph(attributePaths = {"stock", "portfolio"})
    Page<TradeOrder> findByPortfolio(Portfolio portfolio, Pageable pageable);

    @EntityGraph(attributePaths = {"stock", "portfolio", "user"})
    Page<TradeOrder> findAllBy(Pageable pageable);

    @EntityGraph(attributePaths = {"stock", "portfolio", "user"})
    List<TradeOrder> findByStatus(OrderStatus status);

    long countByPortfolioAndStatus(Portfolio portfolio, OrderStatus status);
}
