package com.stock.platform.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * A股股票基础信息表 - 存储所有A股股票
 */
@Entity
@Table(name = "stock_basic")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockBasic {

    @Id
    @Column(name = "symbol", length = 20)
    private String symbol;  // 股票代码

    @Column(name = "name", length = 100, nullable = false)
    private String name;  // 股票名称

    @Column(name = "exchange", length = 10)
    private String exchange;  // 交易所 SH/SZ/BJ

    @Column(name = "market_type", length = 20)
    private String marketType;  // 市场类型 主板/创业板/科创板/北交所

    @Column(name = "industry", length = 100)
    private String industry;  // 所属行业

    @Column(name = "list_date", length = 20)
    private String listDate;  // 上市日期

    @Column(name = "status")
    private Integer status;  // 状态 1-正常 0-退市

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) {
            status = 1;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
