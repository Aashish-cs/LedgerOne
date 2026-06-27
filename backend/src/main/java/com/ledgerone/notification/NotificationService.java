package com.ledgerone.notification;

import com.ledgerone.entity.Notification;
import com.ledgerone.entity.NotificationType;
import com.ledgerone.entity.UserAccount;
import com.ledgerone.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NotificationService {
    private final NotificationRepository notificationRepository;

    public void create(UserAccount user, NotificationType type, String title, String message) {
        Notification notification = new Notification();
        notification.setUser(user);
        notification.setType(type);
        notification.setTitle(title);
        notification.setMessage(message);
        notificationRepository.save(notification);
    }
}
