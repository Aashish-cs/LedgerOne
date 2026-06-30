package com.ledgerone.repository;

import com.ledgerone.entity.Stock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockRepository extends JpaRepository<Stock, UUID> {
    Optional<Stock> findBySymbolIgnoreCase(String symbol);

    List<Stock> findTop8BySymbolContainingIgnoreCaseOrCompanyNameContainingIgnoreCase(String symbol, String companyName);
}
