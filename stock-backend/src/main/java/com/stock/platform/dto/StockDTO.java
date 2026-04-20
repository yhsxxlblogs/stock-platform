package com.stock.platform.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockDTO {

    private Long id;
    private String symbol;
    private String name;
    private String exchange;
    private String industry;
    private BigDecimal marketCap;
    private Integer status;
    private LocalDateTime createdAt;
    
    // 实时数据字段
    private BigDecimal currentPrice;
    private BigDecimal changePrice;
    private BigDecimal changePercent;
    private Long volume;
    private BigDecimal amount;
    private BigDecimal highPrice;
    private BigDecimal lowPrice;
    private BigDecimal openPrice;
    private BigDecimal preClose;
    
    // 格式化展示字段
    private String volumeDisplay;  // 成交量展示：如 "299.18万手"
    private String amountDisplay;  // 成交额展示：如 "105.98亿"

    // 数据来源标记
    @Builder.Default
    private Boolean fromCache = false;  // 是否来自缓存数据
}
