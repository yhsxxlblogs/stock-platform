package com.stock.platform.repository;

import com.stock.platform.entity.Stock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StockRepository extends JpaRepository<Stock, Long> {

    Optional<Stock> findBySymbol(String symbol);

    List<Stock> findByNameContaining(String name);

    @Query("SELECT s FROM Stock s WHERE s.symbol LIKE %:keyword% OR s.name LIKE %:keyword%")
    List<Stock> searchStocks(@Param("keyword") String keyword);

    List<Stock> findByStatus(Integer status);
}
