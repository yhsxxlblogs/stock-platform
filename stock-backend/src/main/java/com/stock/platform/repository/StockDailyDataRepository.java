package com.stock.platform.repository;

import com.stock.platform.entity.StockDailyData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface StockDailyDataRepository extends JpaRepository<StockDailyData, Long> {

    List<StockDailyData> findByStockIdOrderByTradeDateDesc(Long stockId);

    List<StockDailyData> findByStockIdAndTradeDateBetweenOrderByTradeDateAsc(
            Long stockId, LocalDate startDate, LocalDate endDate);

    Optional<StockDailyData> findByStockIdAndTradeDate(Long stockId, LocalDate tradeDate);

    @Query("SELECT d FROM StockDailyData d WHERE d.stock.symbol = :symbol " +
           "AND d.tradeDate BETWEEN :startDate AND :endDate ORDER BY d.tradeDate ASC")
    List<StockDailyData> findBySymbolAndDateRange(
            @Param("symbol") String symbol,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    @Query(value = "SELECT * FROM stock_daily_data WHERE stock_id = :stockId " +
                   "ORDER BY trade_date DESC LIMIT :limit", nativeQuery = true)
    List<StockDailyData> findRecentData(@Param("stockId") Long stockId, @Param("limit") int limit);
}
