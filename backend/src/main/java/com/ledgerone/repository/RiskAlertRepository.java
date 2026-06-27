package com.ledgerone.repository;

import com.ledgerone.entity.Portfolio;
import com.ledgerone.entity.RiskAlert;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RiskAlertRepository extends JpaRepository<RiskAlert, UUID> {
    List<RiskAlert> findTop10ByPortfolioOrderByCreatedAtDesc(Portfolio portfolio);

    Page<RiskAlert> findByResolvedFalse(Pageable pageable);
}
