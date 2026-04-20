package com.stock.platform.controller;

import com.stock.platform.dto.ApiResponse;
import com.stock.platform.dto.StockDTO;
import com.stock.platform.entity.User;
import com.stock.platform.entity.UserFavorite;
import com.stock.platform.repository.StockRepository;
import com.stock.platform.repository.StockRealtimeDataRepository;
import com.stock.platform.repository.UserFavoriteRepository;
import com.stock.platform.repository.UserRepository;
import com.stock.platform.service.TencentStockDataService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/favorites")
@Slf4j
public class UserFavoriteController {

    @Autowired
    private UserFavoriteRepository favoriteRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private StockRealtimeDataRepository realtimeDataRepository;

    @Autowired
    private TencentStockDataService tencentStockDataService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<StockDTO>>> getFavorites(Authentication authentication) {
        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        List<UserFavorite> favorites = favoriteRepository.findByUserIdWithStock(user.getId());

        // 获取所有自选股的代码
        List<String> symbols = favorites.stream()
                .map(f -> f.getStock().getSymbol())
                .collect(Collectors.toList());

        // 直接从API获取最新实时数据
        List<StockDTO> result = new ArrayList<>();
        if (!symbols.isEmpty()) {
            try {
                List<StockDTO> realtimeDataList = tencentStockDataService.getRealtimeDataBatch(
                        favorites.stream().map(f -> f.getStock()).collect(Collectors.toList())
                );

                // 构建结果，保持原有股票信息
                for (UserFavorite favorite : favorites) {
                    var stock = favorite.getStock();
                    // 从API数据中找到对应的实时数据
                    StockDTO realtimeData = realtimeDataList.stream()
                            .filter(dto -> dto.getSymbol().equals(stock.getSymbol()))
                            .findFirst()
                            .orElse(null);

                    var dtoBuilder = StockDTO.builder()
                            .id(stock.getId())
                            .symbol(stock.getSymbol())
                            .name(stock.getName())
                            .exchange(stock.getExchange())
                            .industry(stock.getIndustry())
                            .marketCap(stock.getMarketCap())
                            .status(stock.getStatus())
                            .createdAt(stock.getCreatedAt());

                    if (realtimeData != null) {
                        // 使用API获取的最新数据
                        dtoBuilder
                                .currentPrice(realtimeData.getCurrentPrice())
                                .changePrice(realtimeData.getChangePrice())
                                .changePercent(realtimeData.getChangePercent())
                                .volume(realtimeData.getVolume())
                                .amount(realtimeData.getAmount())
                                .highPrice(realtimeData.getHighPrice())
                                .lowPrice(realtimeData.getLowPrice())
                                .openPrice(realtimeData.getOpenPrice())
                                .preClose(realtimeData.getPreClose());
                    } else {
                        // API获取失败，使用数据库缓存的数据
                        realtimeDataRepository.findByStockId(stock.getId()).ifPresent(cached -> {
                            dtoBuilder
                                    .currentPrice(cached.getCurrentPrice())
                                    .changePrice(cached.getChangePrice())
                                    .changePercent(cached.getChangePercent())
                                    .volume(cached.getVolume())
                                    .amount(cached.getAmount())
                                    .highPrice(cached.getHighPrice())
                                    .lowPrice(cached.getLowPrice())
                                    .openPrice(cached.getOpenPrice())
                                    .preClose(cached.getPreClose());
                        });
                    }

                    result.add(dtoBuilder.build());
                }
            } catch (Exception e) {
                log.error("获取自选股实时数据失败: {}", e.getMessage());
                // API调用失败，回退到数据库查询
                result = getFavoritesFromDatabase(favorites);
            }
        }

        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /**
     * 从数据库获取自选股数据（备用方案）
     */
    private List<StockDTO> getFavoritesFromDatabase(List<UserFavorite> favorites) {
        return favorites.stream()
                .map(f -> {
                    var stock = f.getStock();
                    var dtoBuilder = StockDTO.builder()
                            .id(stock.getId())
                            .symbol(stock.getSymbol())
                            .name(stock.getName())
                            .exchange(stock.getExchange())
                            .industry(stock.getIndustry())
                            .marketCap(stock.getMarketCap())
                            .status(stock.getStatus())
                            .createdAt(stock.getCreatedAt());

                    realtimeDataRepository.findByStockId(stock.getId()).ifPresent(realtime -> {
                        dtoBuilder
                                .currentPrice(realtime.getCurrentPrice())
                                .changePrice(realtime.getChangePrice())
                                .changePercent(realtime.getChangePercent())
                                .volume(realtime.getVolume())
                                .amount(realtime.getAmount())
                                .highPrice(realtime.getHighPrice())
                                .lowPrice(realtime.getLowPrice())
                                .openPrice(realtime.getOpenPrice())
                                .preClose(realtime.getPreClose());
                    });

                    return dtoBuilder.build();
                })
                .collect(Collectors.toList());
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
