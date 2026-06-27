package com.ledgerone.service;

import com.ledgerone.audit.AuditService;
import com.ledgerone.dto.AdminDtos;
import com.ledgerone.dto.AuditDtos;
import com.ledgerone.dto.PageResponse;
import com.ledgerone.dto.RiskDtos;
import com.ledgerone.dto.TradingDtos;
import com.ledgerone.entity.AccountStatus;
import com.ledgerone.entity.AuditAction;
import com.ledgerone.entity.UserAccount;
import com.ledgerone.exception.ResourceNotFoundException;
import com.ledgerone.mapper.AuditMapper;
import com.ledgerone.mapper.RiskAlertMapper;
import com.ledgerone.mapper.TradeOrderMapper;
import com.ledgerone.mapper.UserMapper;
import com.ledgerone.repository.AuditLogRepository;
import com.ledgerone.repository.RiskAlertRepository;
import com.ledgerone.repository.TradeOrderRepository;
import com.ledgerone.repository.UserAccountRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminService {
    private final UserAccountRepository userRepository;
    private final TradeOrderRepository orderRepository;
    private final AuditLogRepository auditLogRepository;
    private final RiskAlertRepository riskAlertRepository;
    private final UserMapper userMapper;
    private final TradeOrderMapper tradeOrderMapper;
    private final AuditMapper auditMapper;
    private final RiskAlertMapper riskAlertMapper;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public PageResponse<AdminDtos.UserAdminResponse> users(String search, Pageable pageable) {
        String term = search == null ? "" : search;
        return PageResponse.from(userRepository
                .findByEmailContainingIgnoreCaseOrFullNameContainingIgnoreCase(term, term, pageable)
                .map(userMapper::toAdmin));
    }

    @Transactional
    public AdminDtos.AdminActionResponse freeze(UserAccount admin, UUID userId) {
        UserAccount user = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        user.setStatus(AccountStatus.FROZEN);
        auditService.record(admin, AuditAction.ADMIN_ACTION, "Freeze account", user.getEmail());
        return new AdminDtos.AdminActionResponse(user.getId(), user.getStatus(), "Account frozen");
    }

    @Transactional
    public AdminDtos.AdminActionResponse unfreeze(UserAccount admin, UUID userId) {
        UserAccount user = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        user.setStatus(AccountStatus.ACTIVE);
        auditService.record(admin, AuditAction.ADMIN_ACTION, "Unfreeze account", user.getEmail());
        return new AdminDtos.AdminActionResponse(user.getId(), user.getStatus(), "Account unfrozen");
    }

    @Transactional(readOnly = true)
    public PageResponse<TradingDtos.OrderResponse> orders(Pageable pageable) {
        return PageResponse.from(orderRepository.findAll(pageable).map(tradeOrderMapper::toResponse));
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditDtos.AuditLogResponse> auditLogs(Pageable pageable) {
        return PageResponse.from(auditLogRepository.findAll(pageable).map(auditMapper::toResponse));
    }

    @Transactional(readOnly = true)
    public PageResponse<RiskDtos.RiskAlertResponse> openRiskAlerts(Pageable pageable) {
        return PageResponse.from(riskAlertRepository.findByResolvedFalse(pageable).map(riskAlertMapper::toResponse));
    }
}
