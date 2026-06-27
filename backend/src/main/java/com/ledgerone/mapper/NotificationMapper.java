package com.ledgerone.mapper;

import com.ledgerone.dto.NotificationDtos;
import com.ledgerone.entity.Notification;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface NotificationMapper {
    NotificationDtos.NotificationResponse toResponse(Notification notification);
}
