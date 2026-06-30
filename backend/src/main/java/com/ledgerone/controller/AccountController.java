package com.ledgerone.controller;

import com.ledgerone.dto.AccountDtos;
import com.ledgerone.dto.ApiResponse;
import com.ledgerone.security.CurrentUser;
import com.ledgerone.service.AccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/account")
@RequiredArgsConstructor
public class AccountController {
    private final CurrentUser currentUser;
    private final AccountService accountService;

    @GetMapping("/summary")
    ApiResponse<AccountDtos.PaperAccountResponse> summary() {
        return ApiResponse.ok("Paper account loaded", accountService.summary(currentUser.entity()));
    }
}
