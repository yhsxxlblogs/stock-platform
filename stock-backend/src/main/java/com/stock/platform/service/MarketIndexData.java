package com.stock.platform.service;

import lombok.Data;
import java.math.BigDecimal;

/**
 * 大盘指数数据
 * 用于存储从API获取的大盘指数信息
 */
@Data
public class MarketIndexData {
    private String name;
    private BigDecimal currentPrice;
    private BigDecimal changePrice;
    private BigDecimal changePercent;
    private BigDecimal preClose;
}
