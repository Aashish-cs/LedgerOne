package com.ledgerone.service;

import com.ledgerone.audit.AuditService;
import com.ledgerone.dto.WatchlistDtos;
import com.ledgerone.entity.AuditAction;
import com.ledgerone.entity.Stock;
import com.ledgerone.entity.UserAccount;
import com.ledgerone.entity.WatchlistItem;
import com.ledgerone.exception.ResourceNotFoundException;
import com.ledgerone.mapper.StockMapper;
import com.ledgerone.repository.WatchlistItemRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WatchlistService {
    private final WatchlistItemRepository watchlistItemRepository;
    private final MarketDataService marketDataService;
    private final StockMapper stockMapper;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<WatchlistDtos.WatchlistResponse> list(UserAccount user) {
        return watchlistItemRepository.findByUserOrderByCreatedAtDesc(user).stream().map(this::toResponse).toList();
    }

    @Transactional
    public WatchlistDtos.WatchlistResponse add(UserAccount user, WatchlistDtos.WatchlistRequest request) {
        Stock stock = marketDataService.findTradableStock(request.symbol());
        WatchlistItem item = watchlistItemRepository.findByUserAndStock(user, stock).orElseGet(() -> {
            WatchlistItem created = new WatchlistItem();
            created.setUser(user);
            created.setStock(stock);
            return watchlistItemRepository.save(created);
        });
        auditService.record(user, AuditAction.WATCHLIST_UPDATE, "Watchlist add", stock.getSymbol());
        return toResponse(item);
    }

    @Transactional
    public void remove(UserAccount user, UUID itemId) {
        WatchlistItem item = watchlistItemRepository
                .findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("Watchlist item not found"));
        if (!item.getUser().getId().equals(user.getId())) {
            throw new ResourceNotFoundException("Watchlist item not found");
        }
        watchlistItemRepository.delete(item);
        auditService.record(user, AuditAction.WATCHLIST_UPDATE, "Watchlist remove", item.getStock().getSymbol());
    }

    private WatchlistDtos.WatchlistResponse toResponse(WatchlistItem item) {
        return new WatchlistDtos.WatchlistResponse(item.getId(), stockMapper.toResponse(item.getStock()), item.getCreatedAt());
    }
}
