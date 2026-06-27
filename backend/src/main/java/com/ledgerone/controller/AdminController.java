package com.ledgerone.controller;

import com.ledgerone.dto.AdminDtos;
import com.ledgerone.dto.ApiResponse;
import com.ledgerone.dto.AuditDtos;
import com.ledgerone.dto.PageResponse;
import com.ledgerone.dto.RiskDtos;
import com.ledgerone.dto.TradingDtos;
import com.ledgerone.security.CurrentUser;
import com.ledgerone.service.AdminService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {
    private final AdminService adminService;
    private final CurrentUser currentUser;

    @GetMapping("/users")
    ApiResponse<PageResponse<AdminDtos.UserAdminResponse>> users(
            @RequestParam(required = false) String search, Pageable pageable) {
        return ApiResponse.ok("Users loaded", adminService.users(search, pageable));
    }

    @PatchMapping("/users/{userId}/freeze")
    ApiResponse<AdminDtos.AdminActionResponse> freeze(@PathVariable UUID userId) {
        return ApiResponse.ok("Account frozen", adminService.freeze(currentUser.entity(), userId));
    }

    @PatchMapping("/users/{userId}/unfreeze")
    ApiResponse<AdminDtos.AdminActionResponse> unfreeze(@PathVariable UUID userId) {
        return ApiResponse.ok("Account unfrozen", adminService.unfreeze(currentUser.entity(), userId));
    }

    @GetMapping("/orders")
    ApiResponse<PageResponse<TradingDtos.OrderResponse>> orders(Pageable pageable) {
        return ApiResponse.ok("Orders loaded", adminService.orders(pageable));
    }

    @GetMapping("/audit-logs")
    ApiResponse<PageResponse<AuditDtos.AuditLogResponse>> auditLogs(Pageable pageable) {
        return ApiResponse.ok("Audit logs loaded", adminService.auditLogs(pageable));
    }

    @GetMapping("/risk-alerts")
    ApiResponse<PageResponse<RiskDtos.RiskAlertResponse>> riskAlerts(Pageable pageable) {
        return ApiResponse.ok("Risk alerts loaded", adminService.openRiskAlerts(pageable));
    }
}
