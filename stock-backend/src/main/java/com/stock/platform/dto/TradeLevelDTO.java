package com.stock.platform.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TradeLevelDTO {
    private int level;
    private BigDecimal price;
    private Long volume;  // 成交量（股）
    private String volumeDisplay;  // 成交量展示（手）：如 "1637手"
}
