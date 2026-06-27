package com.ledgerone.repository;

import com.ledgerone.entity.PriceHistory;
import com.ledgerone.entity.Stock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PriceHistoryRepository extends JpaRepository<PriceHistory, UUID> {
    List<PriceHistory> findTop40ByStockOrderByObservedAtDesc(Stock stock);

    List<PriceHistory> findByStockAndObservedAtAfterOrderByObservedAtAsc(Stock stock, Instant observedAt);
}
