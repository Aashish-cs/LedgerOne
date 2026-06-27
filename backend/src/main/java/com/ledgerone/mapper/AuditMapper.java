package com.ledgerone.mapper;

import com.ledgerone.dto.AuditDtos;
import com.ledgerone.entity.AuditLog;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface AuditMapper {
    @Mapping(target = "userId", expression = "java(log.getUser() == null ? null : log.getUser().getId())")
    @Mapping(target = "userEmail", expression = "java(log.getUser() == null ? null : log.getUser().getEmail())")
    AuditDtos.AuditLogResponse toResponse(AuditLog log);
}
