package com.ledgerone.repository;

import com.ledgerone.entity.AuditLog;
import com.ledgerone.entity.UserAccount;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {
    Page<AuditLog> findByUser(UserAccount user, Pageable pageable);
}
