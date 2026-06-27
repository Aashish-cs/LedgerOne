package com.ledgerone.mapper;

import com.ledgerone.dto.MarketDtos;
import com.ledgerone.entity.Stock;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface StockMapper {
    MarketDtos.StockResponse toResponse(Stock stock);
}
