package com.stock.platform.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "stock_realtime_data")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StockRealtimeData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_id", nullable = false)
    private Stock stock;

    @Column(name = "current_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal currentPrice;

    @Column(name = "change_price", precision = 10, scale = 2)
    private BigDecimal changePrice;

    @Column(name = "change_percent", precision = 5, scale = 2)
    private BigDecimal changePercent;

    @Column
    private Long volume;

    @Column(precision = 20, scale = 2)
    private BigDecimal amount;

    // 买一
    @Column(name = "bid_price1", precision = 10, scale = 2)
    private BigDecimal bidPrice1;

    @Column(name = "bid_volume1")
    private Long bidVolume1;

    // 买二
    @Column(name = "bid_price2", precision = 10, scale = 2)
    private BigDecimal bidPrice2;

    @Column(name = "bid_volume2")
    private Long bidVolume2;

    // 买三
    @Column(name = "bid_price3", precision = 10, scale = 2)
    private BigDecimal bidPrice3;

    @Column(name = "bid_volume3")
    private Long bidVolume3;

    // 买四
    @Column(name = "bid_price4", precision = 10, scale = 2)
    private BigDecimal bidPrice4;

    @Column(name = "bid_volume4")
    private Long bidVolume4;

    // 买五
    @Column(name = "bid_price5", precision = 10, scale = 2)
    private BigDecimal bidPrice5;

    @Column(name = "bid_volume5")
    private Long bidVolume5;

    // 卖一
    @Column(name = "ask_price1", precision = 10, scale = 2)
    private BigDecimal askPrice1;

    @Column(name = "ask_volume1")
    private Long askVolume1;

    // 卖二
    @Column(name = "ask_price2", precision = 10, scale = 2)
    private BigDecimal askPrice2;

    @Column(name = "ask_volume2")
    private Long askVolume2;

    // 卖三
    @Column(name = "ask_price3", precision = 10, scale = 2)
    private BigDecimal askPrice3;

    @Column(name = "ask_volume3")
    private Long askVolume3;

    // 卖四
    @Column(name = "ask_price4", precision = 10, scale = 2)
    private BigDecimal askPrice4;

    @Column(name = "ask_volume4")
    private Long askVolume4;

    // 卖五
    @Column(name = "ask_price5", precision = 10, scale = 2)
    private BigDecimal askPrice5;

    @Column(name = "ask_volume5")
    private Long askVolume5;

    @Column(name = "high_price", precision = 10, scale = 2)
    private BigDecimal highPrice;

    @Column(name = "low_price", precision = 10, scale = 2)
    private BigDecimal lowPrice;

    @Column(name = "open_price", precision = 10, scale = 2)
    private BigDecimal openPrice;

    @Column(name = "pre_close", precision = 10, scale = 2)
    private BigDecimal preClose;

    @Column(name = "update_time")
    private LocalDateTime updateTime;

    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        updateTime = LocalDateTime.now();
    }
}
