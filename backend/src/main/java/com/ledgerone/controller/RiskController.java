package com.ledgerone.controller;

import com.ledgerone.dto.ApiResponse;
import com.ledgerone.dto.RiskDtos;
import com.ledgerone.security.CurrentUser;
import com.ledgerone.service.RiskService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/risk")
@RequiredArgsConstructor
public class RiskController {
    private final RiskService riskService;
    private final CurrentUser currentUser;

    @GetMapping("/portfolios/{portfolioId}")
    ApiResponse<RiskDtos.RiskSummaryResponse> summary(@PathVariable UUID portfolioId) {
        return ApiResponse.ok("Risk summary loaded", riskService.evaluate(currentUser.entity(), portfolioId));
    }
}
