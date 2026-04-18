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
public class StockRealtimeDTO {

    private Long stockId;
    private String symbol;
    private String name;
    private BigDecimal currentPrice;
    private BigDecimal changePrice;
    private BigDecimal changePercent;
    private Long volume;
    private BigDecimal amount;
    private BigDecimal bidPrice1;
    private Long bidVolume1;
    private BigDecimal askPrice1;
    private Long askVolume1;
    private BigDecimal highPrice;
    private BigDecimal lowPrice;
    private BigDecimal openPrice;
    private BigDecimal preClose;
    private LocalDateTime updateTime;
}
