package com.ledgerone.controller;

import com.ledgerone.dto.ApiResponse;
import com.ledgerone.dto.PortfolioDtos;
import com.ledgerone.security.CurrentUser;
import com.ledgerone.service.PortfolioService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/portfolios")
@RequiredArgsConstructor
public class PortfolioController {
    private final PortfolioService portfolioService;
    private final CurrentUser currentUser;

    @GetMapping
    ApiResponse<List<PortfolioDtos.PortfolioResponse>> list() {
        return ApiResponse.ok("Portfolios loaded", portfolioService.list(currentUser.entity()));
    }

    @PostMapping
    ResponseEntity<ApiResponse<PortfolioDtos.PortfolioResponse>> create(@Valid @RequestBody PortfolioDtos.PortfolioCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Portfolio created", portfolioService.create(currentUser.entity(), request)));
    }

    @PutMapping("/{portfolioId}")
    ApiResponse<PortfolioDtos.PortfolioResponse> rename(
            @PathVariable UUID portfolioId, @Valid @RequestBody PortfolioDtos.PortfolioRenameRequest request) {
        return ApiResponse.ok("Portfolio renamed", portfolioService.rename(currentUser.entity(), portfolioId, request));
    }

    @DeleteMapping("/{portfolioId}")
    ApiResponse<Map<String, Boolean>> delete(@PathVariable UUID portfolioId) {
        portfolioService.delete(currentUser.entity(), portfolioId);
        return ApiResponse.ok("Portfolio deleted", Map.of("deleted", true));
    }
}
