package com.ledgerone.mapper;

import com.ledgerone.dto.TradingDtos;
import com.ledgerone.entity.TradeOrder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface TradeOrderMapper {
    @Mapping(source = "portfolio.id", target = "portfolioId")
    @Mapping(source = "portfolio.name", target = "portfolioName")
    @Mapping(source = "stock.symbol", target = "symbol")
    TradingDtos.OrderResponse toResponse(TradeOrder order);
}
