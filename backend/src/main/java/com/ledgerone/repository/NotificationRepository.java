package com.ledgerone.repository;

import com.ledgerone.entity.Notification;
import com.ledgerone.entity.UserAccount;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {
    List<Notification> findTop10ByUserOrderByCreatedAtDesc(UserAccount user);
}
