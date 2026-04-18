package com.stock.platform.repository;

import com.stock.platform.entity.StockBasic;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StockBasicRepository extends JpaRepository<StockBasic, String> {

    /**
     * 根据代码或名称搜索股票
     */
    @Query("SELECT s FROM StockBasic s WHERE s.status = 1 AND " +
           "(s.symbol LIKE %:keyword% OR s.name LIKE %:keyword%) " +
           "ORDER BY s.symbol")
    List<StockBasic> searchByKeyword(@Param("keyword") String keyword);

    /**
     * 根据代码搜索
     */
    List<StockBasic> findBySymbolContainingAndStatus(String symbol, Integer status);

    /**
     * 根据名称搜索
     */
    List<StockBasic> findByNameContainingAndStatus(String name, Integer status);

    /**
     * 根据交易所查询
     */
    List<StockBasic> findByExchangeAndStatus(String exchange, Integer status);

    /**
     * 查询所有正常状态的股票
     */
    List<StockBasic> findByStatus(Integer status);

    /**
     * 根据代码查询
     */
    Optional<StockBasic> findBySymbol(String symbol);

    /**
     * 检查股票是否存在
     */
    boolean existsBySymbol(String symbol);
}
