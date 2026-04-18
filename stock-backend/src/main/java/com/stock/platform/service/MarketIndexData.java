package com.stock.platform.service;

import lombok.Data;
import java.math.BigDecimal;

/**
 * 大盘指数数据（内部使用）
 */
@Data
public class MarketIndexData {
    private BigDecimal currentPrice;
    private BigDecimal preClose;
    private BigDecimal changePrice;
    private BigDecimal changePercent;
}
