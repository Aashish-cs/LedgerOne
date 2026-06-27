package com.ledgerone.mapper;

import com.ledgerone.dto.RiskDtos;
import com.ledgerone.entity.RiskAlert;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface RiskAlertMapper {
    @Mapping(source = "portfolio.id", target = "portfolioId")
    RiskDtos.RiskAlertResponse toResponse(RiskAlert alert);
}
