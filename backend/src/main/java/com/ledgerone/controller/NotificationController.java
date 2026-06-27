package com.ledgerone.controller;

import com.ledgerone.dto.ApiResponse;
import com.ledgerone.dto.NotificationDtos;
import com.ledgerone.security.CurrentUser;
import com.ledgerone.service.NotificationQueryService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {
    private final NotificationQueryService notificationQueryService;
    private final CurrentUser currentUser;

    @GetMapping
    ApiResponse<List<NotificationDtos.NotificationResponse>> recent() {
        return ApiResponse.ok("Notifications loaded", notificationQueryService.recent(currentUser.entity()));
    }
}
