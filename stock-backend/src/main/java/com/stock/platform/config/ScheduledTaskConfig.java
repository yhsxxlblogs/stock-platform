package com.stock.platform.config;

import com.stock.platform.service.StockBasicSyncService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * 定时任务配置类
 */
@Configuration
@EnableScheduling
@Slf4j
public class ScheduledTaskConfig {

    @Autowired
    private StockBasicSyncService stockBasicSyncService;

    /**
     * 定时同步股票列表
     * 每天凌晨2点执行一次
     * cron表达式: 秒 分 时 日 月 周
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void scheduledStockSync() {
        log.info("触发定时任务: 同步股票列表");
        stockBasicSyncService.scheduledSync();
    }

    /**
     * 每小时检查一次股票数量，如果少于1000只则触发同步
     * 用于初次部署或数据丢失时自动恢复
     */
    @Scheduled(cron = "0 0 * * * ?")
    public void checkStockCount() {
        long count = stockBasicSyncService.getStockCount();
        log.info("定时检查: 数据库中当前有{}stocks", count);

        if (count < 1000) {
            log.info("股票数量不足1000只，触发自动同步");
            stockBasicSyncService.scheduledSync();
        }
    }

    /**
     * 每30分钟打印一次股票统计信息（用于监控）
     */
    @Scheduled(cron = "0 */30 * * * ?")
    public void printStockStats() {
        long count = stockBasicSyncService.getStockCount();
        log.info("股票统计: 数据库中共有{}stocks", count);
    }
}
