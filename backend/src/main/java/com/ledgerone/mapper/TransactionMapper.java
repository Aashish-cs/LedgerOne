package com.ledgerone.mapper;

import com.ledgerone.dto.TradingDtos;
import com.ledgerone.entity.LedgerTransaction;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface TransactionMapper {
    @Mapping(source = "order.id", target = "orderId")
    @Mapping(source = "stock.symbol", target = "symbol")
    TradingDtos.TransactionResponse toResponse(LedgerTransaction transaction);
}
