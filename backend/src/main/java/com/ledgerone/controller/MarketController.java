package com.ledgerone.controller;

import com.ledgerone.dto.ApiResponse;
import com.ledgerone.dto.MarketDtos;
import com.ledgerone.service.MarketDataService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/market")
@RequiredArgsConstructor
public class MarketController {
    private final MarketDataService marketDataService;

    @GetMapping("/stocks")
    ApiResponse<List<MarketDtos.StockResponse>> stocks() {
        return ApiResponse.ok("Stocks loaded", marketDataService.listStocks());
    }

    @GetMapping("/stocks/{symbol}/history")
    ApiResponse<List<MarketDtos.PricePoint>> history(@PathVariable String symbol, @RequestParam(defaultValue = "30") int days) {
        return ApiResponse.ok("Price history loaded", marketDataService.history(symbol, days));
    }
}
