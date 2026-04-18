package com.stock.platform.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 股票完整信息DTO（包含股本、财务指标等）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockInfoDTO {
    // 基本信息
    private String symbol;
    private String name;
    
    // 价格数据
    private BigDecimal currentPrice;
    private BigDecimal preClose;
    private BigDecimal openPrice;
    private BigDecimal highPrice;
    private BigDecimal lowPrice;
    private BigDecimal changePrice;
    private BigDecimal changePercent;
    
    // 成交量额
    private Long volume;  // 成交量（股）
    private BigDecimal amount;  // 成交额（元）
    
    // 股本信息
    private Long totalShares;  // 总股本（股）
    private Long floatShares;  // 流通股本（股）
    
    // 市值
    private BigDecimal totalMarketCap;  // 总市值（元）
    private BigDecimal floatMarketCap;  // 流通市值（元）
    
    // 财务指标
    private BigDecimal peRatio;  // 市盈率
    private BigDecimal pbRatio;  // 市净率
    private BigDecimal eps;  // 每股收益
    private BigDecimal bvps;  // 每股净资产
    
    // 交易指标
    private BigDecimal turnoverRate;  // 换手率
    private BigDecimal amplitude;  // 振幅
    private BigDecimal quantityRatio;  // 量比
    private BigDecimal commissionRatio;  // 委比
    private BigDecimal limitUpPrice;  // 涨停价
    private BigDecimal limitDownPrice;  // 跌停价
}
