package com.stock.platform.service;

import com.stock.platform.entity.Stock;
import com.stock.platform.entity.StockRealtimeData;
import com.stock.platform.repository.StockRealtimeDataRepository;
import com.stock.platform.repository.StockRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

@Component
@Slf4j
public class StockDataScheduler {

    @Autowired
    private StockDataService stockDataService;

    @Autowired
    private EastMoneyStockService eastMoneyStockService;

    @Autowired
    private TencentStockDataService tencentStockDataService;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private StockRealtimeDataRepository realtimeDataRepository;

    @Autowired
    private StockSyncService stockSyncService;

    // 线程池用于并行更新实时数据 - 2核服务器使用2线程
    private final ExecutorService executorService = Executors.newFixedThreadPool(2);

    // 每批处理的股票数量
    private static final int BATCH_SIZE = 100;

    /**
     * 每5秒更新一次实时数据
     * 只在交易时间执行
     */
    @Scheduled(fixedRate = 5000)
    @Transactional
    public void updateRealtimeData() {
        // 只在交易时间更新（9:30-11:30, 13:00-15:00）
        LocalTime now = LocalTime.now();
        boolean isTradingTime = (now.isAfter(LocalTime.of(9, 25)) && now.isBefore(LocalTime.of(11, 35))) ||
                               (now.isAfter(LocalTime.of(12, 55)) && now.isBefore(LocalTime.of(15, 5)));

        if (!isTradingTime) {
            log.debug("非交易时间，Skip实时数据Update");
            return;
        }

        try {
            List<Stock> stocks = stockRepository.findByStatus(1);
            if (stocks.isEmpty()) {
                log.warn("数据库中没有股票数据，Skip实时数据Update");
                return;
            }

            log.debug("StartUpdate {} stocks的实时数据", stocks.size());

            // 分批并行处理
            int totalBatches = (int) Math.ceil((double) stocks.size() / BATCH_SIZE);
            List<CompletableFuture<Void>> futures = new ArrayList<>();

            for (int i = 0; i < totalBatches; i++) {
                final int batchIndex = i;
                List<Stock> batch = stocks.subList(
                        i * BATCH_SIZE,
                        Math.min((i + 1) * BATCH_SIZE, stocks.size())
                );

                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    updateBatchRealtimeData(batch, batchIndex, totalBatches);
                }, executorService);

                futures.add(future);

                // 每2批等待一次，避免同时发起太多请求（2核服务器）
                if (futures.size() >= 2) {
                    @SuppressWarnings("null")
                    CompletableFuture<Void>[] futureArray = futures.toArray(new CompletableFuture[0]);
                    CompletableFuture.allOf(futureArray).join();
                    futures.clear();
                    Thread.sleep(500); // 批次间延迟
                }
            }

            // 等待剩余任务完成
            if (!futures.isEmpty()) {
                @SuppressWarnings("null")
                CompletableFuture<Void>[] futureArray = futures.toArray(new CompletableFuture[0]);
                CompletableFuture.allOf(futureArray).join();
            }

        } catch (Exception e) {
            log.error("批量Update实时数据Failed: {}", e.getMessage());
        }
    }

    /**
     * 更新一批股票的实时数据
     */
    private void updateBatchRealtimeData(List<Stock> batch, int batchIndex, int totalBatches) {
        int successCount = 0;
        int failCount = 0;

        for (Stock stock : batch) {
            try {
                // 优先使用腾讯API获取实时数据
                StockRealtimeData realtimeData = tencentStockDataService.getRealtimeData(stock.getSymbol());

                if (realtimeData != null && realtimeData.getCurrentPrice() != null) {
                    // 保存到数据库
                    StockRealtimeData existingData = realtimeDataRepository
                            .findByStockId(stock.getId())
                            .orElse(new StockRealtimeData());

                    existingData.setStock(stock);
                    existingData.setCurrentPrice(realtimeData.getCurrentPrice());
                    existingData.setChangePrice(realtimeData.getChangePrice());
                    existingData.setChangePercent(realtimeData.getChangePercent());
                    existingData.setVolume(realtimeData.getVolume());
                    existingData.setAmount(realtimeData.getAmount());
                    existingData.setHighPrice(realtimeData.getHighPrice());
                    existingData.setLowPrice(realtimeData.getLowPrice());
                    existingData.setOpenPrice(realtimeData.getOpenPrice());
                    existingData.setPreClose(realtimeData.getPreClose());
                    existingData.setBidPrice1(realtimeData.getBidPrice1());
                    existingData.setBidVolume1(realtimeData.getBidVolume1());
                    existingData.setBidPrice2(realtimeData.getBidPrice2());
                    existingData.setBidVolume2(realtimeData.getBidVolume2());
                    existingData.setBidPrice3(realtimeData.getBidPrice3());
                    existingData.setBidVolume3(realtimeData.getBidVolume3());
                    existingData.setBidPrice4(realtimeData.getBidPrice4());
                    existingData.setBidVolume4(realtimeData.getBidVolume4());
                    existingData.setBidPrice5(realtimeData.getBidPrice5());
                    existingData.setBidVolume5(realtimeData.getBidVolume5());
                    existingData.setAskPrice1(realtimeData.getAskPrice1());
                    existingData.setAskVolume1(realtimeData.getAskVolume1());
                    existingData.setAskPrice2(realtimeData.getAskPrice2());
                    existingData.setAskVolume2(realtimeData.getAskVolume2());
                    existingData.setAskPrice3(realtimeData.getAskPrice3());
                    existingData.setAskVolume3(realtimeData.getAskVolume3());
                    existingData.setAskPrice4(realtimeData.getAskPrice4());
                    existingData.setAskVolume4(realtimeData.getAskVolume4());
                    existingData.setAskPrice5(realtimeData.getAskPrice5());
                    existingData.setAskVolume5(realtimeData.getAskVolume5());

                    realtimeDataRepository.save(existingData);
                    successCount++;

                    // 添加短暂延迟避免请求过快
                    Thread.sleep(30);
                } else {
                    failCount++;
                }
            } catch (Exception e) {
                log.warn("Update股票 {} 实时数据Failed: {}", stock.getSymbol(), e.getMessage());
                failCount++;
            }
        }

        log.debug("批次 {}/{} Completed: Success {}, Failed {}", batchIndex + 1, totalBatches, successCount, failCount);
    }

    /**
     * 定时增量同步 - 每天凌晨2点执行
     * 检查并添加新上市的股票
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void scheduledIncrementalSync() {
        log.info("定时任务：Start增量同步股票列表...");
        try {
            stockSyncService.incrementalSync();
        } catch (Exception e) {
            log.error("定时增量同步Failed: {}", e.getMessage(), e);
        }
    }

    /**
     * 定时全量检查同步 - 每周日凌晨3点执行
     * 用于修复可能的数据不一致问题
     */
    @Scheduled(cron = "0 0 3 ? * SUN")
    public void scheduledFullSync() {
        log.info("定时任务：Start全量同步股票列表...");
        try {
            stockSyncService.scheduledFullSync();
        } catch (Exception e) {
            log.error("定时全量同步Failed: {}", e.getMessage(), e);
        }
    }

    /**
     * 每日收盘后数据归档 - 每天15:30执行
     */
    @Scheduled(cron = "0 30 15 * * ?")
    public void dailyDataArchive() {
        log.info("定时任务：Start每日数据归档...");
        try {
            // 这里可以添加数据归档逻辑
            // 例如：将实时数据保存到历史数据表
            log.info("每日数据归档Completed");
        } catch (Exception e) {
            log.error("每日数据归档Failed: {}", e.getMessage(), e);
        }
    }

    /**
     * 应用关闭时关闭线程池
     */
    public void shutdown() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
