package com.stock.platform.repository;

import com.stock.platform.entity.UserFavorite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserFavoriteRepository extends JpaRepository<UserFavorite, Long> {

    List<UserFavorite> findByUserId(Long userId);

    @Query("SELECT f FROM UserFavorite f JOIN FETCH f.stock WHERE f.user.id = :userId")
    List<UserFavorite> findByUserIdWithStock(@Param("userId") Long userId);

    Optional<UserFavorite> findByUserIdAndStockId(Long userId, Long stockId);

    boolean existsByUserIdAndStockId(Long userId, Long stockId);

    @Modifying
    @Transactional
    @Query("DELETE FROM UserFavorite f WHERE f.user.id = :userId AND f.stock.id = :stockId")
    void deleteByUserIdAndStockId(@Param("userId") Long userId, @Param("stockId") Long stockId);
}
