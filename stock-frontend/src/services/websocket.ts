/**
 * WebSocket 服务
 * 用于实时接收股票数据推送
 */

import { ElMessage } from 'element-plus'

// WebSocket 连接状态
export enum WebSocketStatus {
  CONNECTING = 'CONNECTING',
  CONNECTED = 'CONNECTED',
  DISCONNECTED = 'DISCONNECTED',
  RECONNECTING = 'RECONNECTING',
  ERROR = 'ERROR'
}

// WebSocket 配置
const WS_CONFIG = {
  url: import.meta.env.VITE_WS_URL || 'ws://localhost:9090/api/ws/stock',
  reconnectInterval: 5000,  // 重连间隔 5秒
  maxReconnectAttempts: 10, // 最大重连次数
  heartbeatInterval: 30000  // 心跳间隔 30秒
}

// 消息回调函数类型
type MessageCallback = (data: any) => void

class WebSocketService {
  private ws: WebSocket | null = null
  private status: WebSocketStatus = WebSocketStatus.DISCONNECTED
  private reconnectAttempts = 0
  private reconnectTimer: number | null = null
  private heartbeatTimer: number | null = null
  private messageCallbacks: Map<string, Set<MessageCallback>> = new Map()
  private subscribedSymbols: Set<string> = new Set()

  // 获取当前状态
  getStatus(): WebSocketStatus {
    return this.status
  }

  // 连接 WebSocket
  connect(): void {
    if (this.ws?.readyState === WebSocket.OPEN) {
      console.log('WebSocket 已连接')
      return
    }

    this.status = WebSocketStatus.CONNECTING
    console.log('正在连接 WebSocket...')

    try {
      this.ws = new WebSocket(WS_CONFIG.url)

      this.ws.onopen = this.handleOpen.bind(this)
      this.ws.onmessage = this.handleMessage.bind(this)
      this.ws.onclose = this.handleClose.bind(this)
      this.ws.onerror = this.handleError.bind(this)
    } catch (error) {
      console.error('WebSocket 连接失败:', error)
      this.status = WebSocketStatus.ERROR
      this.scheduleReconnect()
    }
  }

  // 断开连接
  disconnect(): void {
    this.clearTimers()
    if (this.ws) {
      this.ws.close()
      this.ws = null
    }
    this.status = WebSocketStatus.DISCONNECTED
    this.reconnectAttempts = 0
    console.log('WebSocket 已断开')
  }

  // 发送消息
  send(message: any): void {
    if (this.ws?.readyState === WebSocket.OPEN) {
      const msg = typeof message === 'string' ? message : JSON.stringify(message)
      this.ws.send(msg)
    } else {
      console.warn('WebSocket 未连接，无法发送消息')
    }
  }

  // 订阅股票列表
  subscribeStocks(symbols: string[]): void {
    if (symbols.length === 0) return
    
    symbols.forEach(symbol => this.subscribedSymbols.add(symbol))
    
    this.send({
      action: 'subscribe',
      symbols: symbols
    })
    console.log('订阅股票:', symbols)
  }

  // 取消订阅股票
  unsubscribeStocks(symbols: string[]): void {
    symbols.forEach(symbol => this.subscribedSymbols.delete(symbol))
    
    this.send({
      action: 'unsubscribe',
      symbols: symbols
    })
    console.log('取消订阅股票:', symbols)
  }

  // 订阅个股详情
  subscribeStockDetail(symbol: string): void {
    // 清除之前的订阅，只关注当前个股
    this.subscribedSymbols.clear()
    this.subscribedSymbols.add(symbol)
    
    this.send({
      action: 'subscribeDetail',
      symbol: symbol
    })
    console.log('订阅个股详情:', symbol)
  }

  // 注册消息回调
  onMessage(type: string, callback: MessageCallback): void {
    if (!this.messageCallbacks.has(type)) {
      this.messageCallbacks.set(type, new Set())
    }
    this.messageCallbacks.get(type)!.add(callback)
  }

  // 移除消息回调
  offMessage(type: string, callback: MessageCallback): void {
    const callbacks = this.messageCallbacks.get(type)
    if (callbacks) {
      callbacks.delete(callback)
    }
  }

  // 处理连接打开
  private handleOpen(): void {
    console.log('WebSocket 连接成功')
    this.status = WebSocketStatus.CONNECTED
    this.reconnectAttempts = 0
    
    // 重新订阅之前的股票
    if (this.subscribedSymbols.size > 0) {
      this.subscribeStocks(Array.from(this.subscribedSymbols))
    }
    
    // 启动心跳
    this.startHeartbeat()
  }

  // 处理消息接收
  private handleMessage(event: MessageEvent): void {
    try {
      const message = JSON.parse(event.data)
      const { type, data } = message
      
      console.log('收到 WebSocket 消息:', type, data)
      
      // 触发注册的回调
      const callbacks = this.messageCallbacks.get(type)
      if (callbacks) {
        callbacks.forEach(callback => {
          try {
            callback(data)
          } catch (error) {
            console.error('消息回调执行失败:', error)
          }
        })
      }
      
      // 触发通用回调
      const allCallbacks = this.messageCallbacks.get('*')
      if (allCallbacks) {
        allCallbacks.forEach(callback => {
          try {
            callback(message)
          } catch (error) {
            console.error('通用回调执行失败:', error)
          }
        })
      }
    } catch (error) {
      console.error('解析 WebSocket 消息失败:', error)
    }
  }

  // 处理连接关闭
  private handleClose(event: CloseEvent): void {
    console.log('WebSocket 连接关闭:', event.code, event.reason)
    this.status = WebSocketStatus.DISCONNECTED
    this.clearTimers()
    
    // 非正常关闭时尝试重连
    if (!event.wasClean) {
      this.scheduleReconnect()
    }
  }

  // 处理错误
  private handleError(error: Event): void {
    console.error('WebSocket 错误:', error)
    this.status = WebSocketStatus.ERROR
    
    if (this.reconnectAttempts === 0) {
      ElMessage.error('实时数据连接失败，正在尝试重连...')
    }
  }

  // 计划重连
  private scheduleReconnect(): void {
    if (this.reconnectAttempts >= WS_CONFIG.maxReconnectAttempts) {
      console.error('WebSocket 重连次数已达上限')
      this.status = WebSocketStatus.ERROR
      ElMessage.error('实时数据服务连接失败，请刷新页面重试')
      return
    }

    this.status = WebSocketStatus.RECONNECTING
    this.reconnectAttempts++
    
    console.log(`计划 ${WS_CONFIG.reconnectInterval}ms 后重连 (第 ${this.reconnectAttempts} 次)`)
    
    this.reconnectTimer = window.setTimeout(() => {
      console.log('正在重连 WebSocket...')
      this.connect()
    }, WS_CONFIG.reconnectInterval)
  }

  // 启动心跳
  private startHeartbeat(): void {
    this.heartbeatTimer = window.setInterval(() => {
      if (this.ws?.readyState === WebSocket.OPEN) {
        this.send({ action: 'ping' })
      }
    }, WS_CONFIG.heartbeatInterval)
  }

  // 清除定时器
  private clearTimers(): void {
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer)
      this.reconnectTimer = null
    }
    if (this.heartbeatTimer) {
      clearInterval(this.heartbeatTimer)
      this.heartbeatTimer = null
    }
  }
}

// 创建单例实例
export const wsService = new WebSocketService()

// 组合式函数，用于在 Vue 组件中使用
export function useWebSocket() {
  return {
    connect: () => wsService.connect(),
    disconnect: () => wsService.disconnect(),
    subscribeStocks: (symbols: string[]) => wsService.subscribeStocks(symbols),
    unsubscribeStocks: (symbols: string[]) => wsService.unsubscribeStocks(symbols),
    subscribeStockDetail: (symbol: string) => wsService.subscribeStockDetail(symbol),
    onMessage: (type: string, callback: MessageCallback) => wsService.onMessage(type, callback),
    offMessage: (type: string, callback: MessageCallback) => wsService.offMessage(type, callback),
    getStatus: () => wsService.getStatus()
  }
}
