package com.ledgerone.controller;

import com.ledgerone.dto.ApiResponse;
import com.ledgerone.dto.PageResponse;
import com.ledgerone.dto.TradingDtos;
import com.ledgerone.security.CurrentUser;
import com.ledgerone.service.TradingService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class TradingController {
    private final TradingService tradingService;
    private final CurrentUser currentUser;

    @PostMapping("/orders")
    ApiResponse<TradingDtos.OrderResponse> place(@Valid @RequestBody TradingDtos.OrderRequest request) {
        return ApiResponse.ok("Order accepted", tradingService.placeOrder(currentUser.principal().id(), request));
    }

    @PostMapping("/orders/{orderId}/cancel")
    ApiResponse<TradingDtos.OrderResponse> cancel(@PathVariable UUID orderId) {
        return ApiResponse.ok("Order cancelled", tradingService.cancel(currentUser.principal().id(), orderId));
    }

    @GetMapping("/portfolios/{portfolioId}/orders")
    ApiResponse<PageResponse<TradingDtos.OrderResponse>> orders(@PathVariable UUID portfolioId,
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.ok("Orders loaded", tradingService.listPortfolioOrders(currentUser.entity(), portfolioId, pageable));
    }

    @GetMapping("/portfolios/{portfolioId}/transactions")
    ApiResponse<PageResponse<TradingDtos.TransactionResponse>> transactions(@PathVariable UUID portfolioId,
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.ok("Transactions loaded", tradingService.listTransactions(currentUser.entity(), portfolioId, pageable));
    }
}
