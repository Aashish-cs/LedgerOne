package com.ledgerone.service;

import com.ledgerone.dto.NotificationDtos;
import com.ledgerone.entity.UserAccount;
import com.ledgerone.mapper.NotificationMapper;
import com.ledgerone.repository.NotificationRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationQueryService {
    private final NotificationRepository notificationRepository;
    private final NotificationMapper notificationMapper;

    @Transactional(readOnly = true)
    public List<NotificationDtos.NotificationResponse> recent(UserAccount user) {
        return notificationRepository.findTop10ByUserOrderByCreatedAtDesc(user).stream()
                .map(notificationMapper::toResponse)
                .toList();
    }
}
