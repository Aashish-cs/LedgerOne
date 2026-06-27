package com.ledgerone.repository;

import com.ledgerone.entity.Stock;
import com.ledgerone.entity.UserAccount;
import com.ledgerone.entity.WatchlistItem;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WatchlistItemRepository extends JpaRepository<WatchlistItem, UUID> {
    @EntityGraph(attributePaths = "stock")
    List<WatchlistItem> findByUserOrderByCreatedAtDesc(UserAccount user);

    Optional<WatchlistItem> findByUserAndStock(UserAccount user, Stock stock);
}
