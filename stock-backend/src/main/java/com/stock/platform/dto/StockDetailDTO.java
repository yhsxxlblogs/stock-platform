package com.stock.platform.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockDetailDTO {
    private Long id;
    private String symbol;
    private String name;
    private String exchange;
    private String industry;
    private BigDecimal marketCap;

    // 实时价格数据
    private BigDecimal currentPrice;
    private BigDecimal changePrice;
    private BigDecimal changePercent;
    private BigDecimal openPrice;
    private BigDecimal highPrice;
    private BigDecimal lowPrice;
    private BigDecimal preClose;
    private Long volume;  // 成交量（股）
    private BigDecimal amount;  // 成交额（元）
    private BigDecimal turnoverRate;  // 换手率（%）
    private BigDecimal amplitude;  // 振幅（%）
    private BigDecimal peRatio;  // 市盈率
    private BigDecimal pbRatio;  // 市净率
    private BigDecimal limitUpPrice;  // 涨停价
    private BigDecimal limitDownPrice;  // 跌停价
    private Long totalShares;  // 总股本（股）
    private Long floatShares;  // 流通股本（股）

    // 格式化展示字段（雪球等平台习惯）
    private String volumeDisplay;  // 成交量展示：如 "299.18万手"
    private String amountDisplay;  // 成交额展示：如 "105.98亿"
    private String marketCapDisplay;  // 总市值展示：如 "9282.82亿"
    private String floatMarketCapDisplay;  // 流通市值展示：如 "7192.09亿"
    private BigDecimal quantityRatio;  // 量比
    private BigDecimal commissionRatio;  // 委比

    // 买卖五档
    private List<TradeLevelDTO> bidLevels;
    private List<TradeLevelDTO> askLevels;
}
