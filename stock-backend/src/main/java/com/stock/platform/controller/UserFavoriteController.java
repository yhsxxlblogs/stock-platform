package com.stock.platform.controller;

import com.stock.platform.dto.ApiResponse;
import com.stock.platform.dto.StockDTO;
import com.stock.platform.entity.User;
import com.stock.platform.entity.UserFavorite;
import com.stock.platform.repository.StockRepository;
import com.stock.platform.repository.StockRealtimeDataRepository;
import com.stock.platform.repository.UserFavoriteRepository;
import com.stock.platform.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/favorites")
public class UserFavoriteController {

    @Autowired
    private UserFavoriteRepository favoriteRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private StockRealtimeDataRepository realtimeDataRepository;

    @GetMapping
    public ResponseEntity<ApiResponse<List<StockDTO>>> getFavorites(Authentication authentication) {
        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        List<UserFavorite> favorites = favoriteRepository.findByUserIdWithStock(user.getId());

        List<StockDTO> result = favorites.stream()
                .map(f -> {
                    var stock = f.getStock();
                    var dto = StockDTO.builder()
                            .id(stock.getId())
                            .symbol(stock.getSymbol())
                            .name(stock.getName())
                            .exchange(stock.getExchange())
                            .industry(stock.getIndustry())
                            .marketCap(stock.getMarketCap())
                            .status(stock.getStatus())
                            .createdAt(stock.getCreatedAt())
                            .build();
                    
                    // 添加实时数据
                    realtimeDataRepository.findByStockId(stock.getId()).ifPresent(realtime -> {
                        dto.setCurrentPrice(realtime.getCurrentPrice());
                        dto.setChangePrice(realtime.getChangePrice());
                        dto.setChangePercent(realtime.getChangePercent());
                        dto.setVolume(realtime.getVolume());
                        dto.setAmount(realtime.getAmount());
                        dto.setHighPrice(realtime.getHighPrice());
                        dto.setLowPrice(realtime.getLowPrice());
                        dto.setOpenPrice(realtime.getOpenPrice());
                        dto.setPreClose(realtime.getPreClose());
                    });
                    
                    return dto;
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping("/{stockId}")
    public ResponseEntity<ApiResponse<?>> addFavorite(
            @PathVariable Long stockId,
            Authentication authentication) {
        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (favoriteRepository.existsByUserIdAndStockId(user.getId(), stockId)) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(400, "该股票已在自选股中"));
        }

        UserFavorite favorite = new UserFavorite();
        favorite.setUser(user);
        favorite.setStock(stockRepository.findById(stockId)
                .orElseThrow(() -> new RuntimeException("Stock not found")));

        favoriteRepository.save(favorite);

        return ResponseEntity.ok(ApiResponse.success("添加成功", null));
    }

    @DeleteMapping("/{stockId}")
    public ResponseEntity<ApiResponse<?>> removeFavorite(
            @PathVariable Long stockId,
            Authentication authentication) {
        try {
            User user = userRepository.findByUsername(authentication.getName())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            System.out.println("删除自选股 - 用户ID: " + user.getId() + ", 股票ID: " + stockId);

            // 检查是否存在该自选股
            boolean exists = favoriteRepository.existsByUserIdAndStockId(user.getId(), stockId);
            System.out.println("自选股是否存在: " + exists);

            if (!exists) {
                return ResponseEntity.ok(ApiResponse.error(404, "该股票不在自选股中"));
            }

            favoriteRepository.deleteByUserIdAndStockId(user.getId(), stockId);
            System.out.println("自选股DeleteSuccess");

            return ResponseEntity.ok(ApiResponse.success("删除成功", null));
        } catch (Exception e) {
            System.out.println("删除自选股失败: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.ok(ApiResponse.error(500, "删除失败: " + e.getMessage()));
        }
    }
}
