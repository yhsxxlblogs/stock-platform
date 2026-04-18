package com.stock.platform.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stock.platform.annotation.Nullable;
import com.stock.platform.dto.StockDTO;
import com.stock.platform.dto.StockDetailDTO;
import com.stock.platform.service.StockDataService;
import com.stock.platform.service.StockRealtimePushService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 股票实时数据 WebSocket 处理器
 * 处理客户端连接、断开、消息接收，并推送实时股票数据
 */
@Slf4j
@Component
public class StockWebSocketHandler extends TextWebSocketHandler implements InitializingBean {

    @Autowired
    private StockDataService stockDataService;

    @Autowired
    private StockRealtimePushService pushService;

    @Autowired
    private ObjectMapper objectMapper;

    // 存储所有连接的客户端会话
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    // 推送线程
    private Thread pushThread;
    private volatile boolean running = true;

    /**
     * Bean 初始化后启动推送线程
     */
    @Override
    public void afterPropertiesSet() throws Exception {
        startPushThread();
        log.info("WebSocket stock data push service started");
    }

    /**
     * 启动推送线程
     */
    private void startPushThread() {
        pushThread = new Thread(() -> {
            while (running) {
                try {
                    // 每2秒推送一次
                    Thread.sleep(2000);
                    pushRealtimeData();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    log.error("推送数据Failed: {}", e.getMessage());
                }
            }
        });
        pushThread.setDaemon(true);
        pushThread.setName("StockPushThread");
        pushThread.start();
    }

    /**
     * 客户端连接建立时触发
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessions.put(session.getId(), session);
        log.info("WebSocket client connected: {}, Current connections: {}", session.getId(), sessions.size());

        // 发送欢迎消息
        sendMessage(session, createMessage("connected", "成功连接到 MarketPulse 实时数据服务"));
    }

    /**
     * 客户端断开连接时触发
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        sessions.remove(session.getId());
        pushService.clientDisconnected(session.getId());
        log.info("WebSocket client disconnected: {}, Current connections: {}", session.getId(), sessions.size());
    }

    /**
     * 接收到客户端消息时触发
     */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        log.debug("Received client message: {}", payload);

        try {
            // 解析客户端消息
            Map<String, Object> msg = objectMapper.readValue(payload, Map.class);
            String action = (String) msg.get("action");

            // 处理 action 为 null 的情况
            if (action == null) {
                log.warn("Received message without action field: {}", payload);
                sendMessage(session, createMessage("error", "消息缺少 action 字段"));
                return;
            }

            switch (action) {
                case "subscribe":
                    // 订阅股票列表
                    handleSubscribe(session, msg);
                    break;
                case "unsubscribe":
                    // 取消订阅
                    handleUnsubscribe(session, msg);
                    break;
                case "subscribeDetail":
                    // 订阅个股详情
                    handleSubscribeDetail(session, msg);
                    break;
                case "ping":
                    // 心跳响应
                    sendMessage(session, createMessage("pong", System.currentTimeMillis()));
                    break;
                default:
                    sendMessage(session, createMessage("error", "未知操作: " + action));
            }
        } catch (Exception e) {
            log.error("ProcessClient消息Failed: {}", e.getMessage());
            sendMessage(session, createMessage("error", "消息格式错误"));
        }
    }

    /**
     * 处理订阅请求
     */
    @SuppressWarnings("unchecked")
    private void handleSubscribe(WebSocketSession session, Map<String, Object> msg) {
        java.util.List<String> symbols = (java.util.List<String>) msg.get("symbols");
        if (symbols != null && !symbols.isEmpty()) {
            Set<String> symbolSet = symbols.stream().collect(Collectors.toSet());
            pushService.subscribe(session.getId(), symbolSet);
            sendMessage(session, createMessage("subscribed", "订阅成功: " + symbols));

            // 立即推送一次数据
            pushClientData(session);
        }
    }

    /**
     * 处理取消订阅请求
     */
    @SuppressWarnings("unchecked")
    private void handleUnsubscribe(WebSocketSession session, Map<String, Object> msg) {
        java.util.List<String> symbols = (java.util.List<String>) msg.get("symbols");
        if (symbols != null && !symbols.isEmpty()) {
            Set<String> symbolSet = symbols.stream().collect(Collectors.toSet());
            pushService.unsubscribe(session.getId(), symbolSet);
            sendMessage(session, createMessage("unsubscribed", "取消订阅成功: " + symbols));
        }
    }

    /**
     * 处理个股详情订阅
     */
    private void handleSubscribeDetail(WebSocketSession session, Map<String, Object> msg) {
        String symbol = (String) msg.get("symbol");
        if (symbol != null && !symbol.isEmpty()) {
            // 订阅单只股票
            pushService.subscribe(session.getId(), Set.of(symbol));
            log.info("Client {} Subscribe stock detail: {}", session.getId(), symbol);

            // 立即推送一次详情
            pushSingleStockDetail(session, symbol);
        }
    }

    /**
     * 推送实时数据给所有客户端
     */
    private void pushRealtimeData() {
        if (sessions.isEmpty()) {
            return;
        }

        for (WebSocketSession session : sessions.values()) {
            if (session.isOpen()) {
                pushClientData(session);
            }
        }
    }

    /**
     * 推送客户端订阅的股票数据
     */
    private void pushClientData(WebSocketSession session) {
        try {
            Map<String, StockDTO> stocks = pushService.getClientStockData(session.getId());
            if (!stocks.isEmpty()) {
                String message = createMessage("marketData", Map.of(
                        "stocks", stocks.values(),
                        "timestamp", System.currentTimeMillis()
                ));
                sendMessage(session, message);
            }
        } catch (Exception e) {
            log.error("Push to client {} 数据Failed: {}", session.getId(), e.getMessage());
        }
    }

    /**
     * 推送单只股票详情
     */
    private void pushSingleStockDetail(WebSocketSession session, @Nullable String symbol) {
        if (symbol == null || symbol.isEmpty()) {
            return;
        }
        try {
            StockDetailDTO detail = stockDataService.getStockDetail(symbol);
            if (detail != null) {
                String message = createMessage("stockDetail", Map.of(
                        "symbol", symbol,
                        "data", detail,
                        "timestamp", System.currentTimeMillis()
                ));
                sendMessage(session, message);
            }
        } catch (Exception e) {
            log.error("Push stock {} 详情Failed: {}", symbol, e.getMessage());
        }
    }

    /**
     * 发送消息到客户端
     */
    private void sendMessage(WebSocketSession session, String message) {
        try {
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(message));
            }
        } catch (IOException e) {
            log.error("发送消息Failed: {}", e.getMessage());
        }
    }

    /**
     * 创建标准格式的消息
     */
    private String createMessage(String type, Object data) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "type", type,
                    "data", data
            ));
        } catch (Exception e) {
            log.error("Create消息Failed: {}", e.getMessage());
            return "{}";
        }
    }

    /**
     * 销毁时清理资源
     */
    public void destroy() {
        running = false;
        if (pushThread != null) {
            pushThread.interrupt();
        }
        log.info("WebSocket stock data push service stopped");
    }
}
