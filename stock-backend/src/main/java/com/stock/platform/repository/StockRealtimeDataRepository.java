package com.stock.platform.repository;

import com.stock.platform.entity.StockRealtimeData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StockRealtimeDataRepository extends JpaRepository<StockRealtimeData, Long> {

    Optional<StockRealtimeData> findByStockId(Long stockId);

    @Query("SELECT r FROM StockRealtimeData r JOIN FETCH r.stock ORDER BY r.changePercent DESC")
    List<StockRealtimeData> findAllOrderByChangePercentDesc();

    @Query("SELECT r FROM StockRealtimeData r WHERE r.stock.symbol IN :symbols")
    List<StockRealtimeData> findBySymbols(@Param("symbols") List<String> symbols);

    @Query("SELECT r FROM StockRealtimeData r JOIN FETCH r.stock s WHERE s.status = 1")
    List<StockRealtimeData> findAllActiveStocks();
}
