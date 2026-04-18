package com.stock.platform.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 大盘指数DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketIndexDTO {
    private String name;
    private BigDecimal currentPrice;
    private BigDecimal changePrice;
    private BigDecimal changePercent;
    private BigDecimal preClose;
}
