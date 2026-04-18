package com.stock.platform.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket 配置类
 * 用于配置股票实时数据推送的 WebSocket 端点
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    @Autowired
    private StockWebSocketHandler stockWebSocketHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // 注册股票实时数据 WebSocket 端点
        // 注意：由于配置了 context-path: /api，这里只注册 /ws/stock
        // Spring Boot 会自动加上 context-path，完整路径为 /api/ws/stock
        registry.addHandler(stockWebSocketHandler, "/ws/stock")
                .setAllowedOriginPatterns("*");  // 使用 patterns 替代 origins，支持通配符和 credentials
    }
}
