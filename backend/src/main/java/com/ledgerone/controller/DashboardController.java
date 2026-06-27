package com.ledgerone.controller;

import com.ledgerone.dto.ApiResponse;
import com.ledgerone.dto.DashboardDtos;
import com.ledgerone.security.CurrentUser;
import com.ledgerone.service.DashboardService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {
    private final DashboardService dashboardService;
    private final CurrentUser currentUser;

    @GetMapping
    ApiResponse<DashboardDtos.DashboardResponse> dashboard(@RequestParam(required = false) UUID portfolioId) {
        return ApiResponse.ok("Dashboard loaded", dashboardService.dashboard(currentUser.entity(), portfolioId));
    }
}
