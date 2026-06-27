package com.ledgerone.controller;

import com.ledgerone.dto.ApiResponse;
import com.ledgerone.dto.WatchlistDtos;
import com.ledgerone.security.CurrentUser;
import com.ledgerone.service.WatchlistService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/watchlist")
@RequiredArgsConstructor
public class WatchlistController {
    private final WatchlistService watchlistService;
    private final CurrentUser currentUser;

    @GetMapping
    ApiResponse<List<WatchlistDtos.WatchlistResponse>> list() {
        return ApiResponse.ok("Watchlist loaded", watchlistService.list(currentUser.entity()));
    }

    @PostMapping
    ApiResponse<WatchlistDtos.WatchlistResponse> add(@Valid @RequestBody WatchlistDtos.WatchlistRequest request) {
        return ApiResponse.ok("Watchlist updated", watchlistService.add(currentUser.entity(), request));
    }

    @DeleteMapping("/{itemId}")
    ApiResponse<Map<String, Boolean>> remove(@PathVariable UUID itemId) {
        watchlistService.remove(currentUser.entity(), itemId);
        return ApiResponse.ok("Watchlist item removed", Map.of("deleted", true));
    }
}
