import request from '@/utils/request'

export interface Stock {
  id: number
  symbol: string
  name: string
  exchange: string
  industry: string
  marketCap: number
  currentPrice?: number
  changePrice?: number
  changePercent?: number
  volume?: number
  amount?: number
  highPrice?: number
  lowPrice?: number
  openPrice?: number
  preClose?: number
  // 格式化展示字段
  volumeDisplay?: string  // 如 "299.18万手"
  amountDisplay?: string  // 如 "105.98亿"
}

export interface KlineData {
  date: string
  open: number
  high: number
  low: number
  close: number
  volume: number
  amount: number
  changePercent: number
}

export interface MinuteData {
  time: string
  price: number
  volume: number
  avgPrice?: number
}

export interface TradeLevel {
  level: number
  price: number
  volume: number
  volumeDisplay?: string  // 如 "1637手"
}

export interface StockDetail {
  id: number
  symbol: string
  name: string
  exchange: string
  industry: string
  marketCap: number
  currentPrice?: number
  changePrice?: number
  changePercent?: number
  openPrice?: number
  highPrice?: number
  lowPrice?: number
  preClose?: number
  volume?: number
  amount?: number
  turnoverRate?: number
  peRatio?: number
  pbRatio?: number
  bidLevels?: TradeLevel[]
  askLevels?: TradeLevel[]
  // 格式化展示字段
  volumeDisplay?: string  // 如 "299.18万手"
  amountDisplay?: string  // 如 "105.98亿"
  marketCapDisplay?: string  // 如 "9282.82亿"
  floatMarketCapDisplay?: string  // 如 "7192.09亿"
  commissionRatio?: number  // 委比
}

export interface MarketIndex {
  name: string
  currentPrice?: number
  changePrice?: number
  changePercent?: number
  preClose?: number
}

export const getStockList = () => {
  return request.get('/stocks/public/list')
}

export const getStockBySymbol = (symbol: string) => {
  return request.get(`/stocks/public/${symbol}`)
}

export const getStockDetail = (symbol: string) => {
  return request.get(`/stocks/public/${symbol}/detail`)
}

export const searchStocks = (keyword: string) => {
  return request.get('/stocks/public/search', { params: { keyword } })
}

export const getKlineData = (symbol: string, period: string = '1m', limit: number = 100) => {
  return request.get(`/stocks/public/${symbol}/kline`, {
    params: { period, limit }
  })
}

export const getMinuteData = (symbol: string) => {
  return request.get(`/stocks/public/${symbol}/minute`)
}

export const getRealtimeData = () => {
  return request.get('/stocks/public/realtime')
}

export const getMarketIndex = () => {
  return request.get('/stocks/public/market-index')
}

// 股票自动补全建议
export const suggestStocks = (keyword: string, limit: number = 10) => {
  return request.get('/stocks/public/suggest', { params: { keyword, limit } })
}

export interface StockSuggestion {
  symbol: string
  name: string
  exchange: string
  fullName: string
}

// 市场状态接口
export interface MarketStatus {
  isTradingTime: boolean
  isTradingDay: boolean
  status: string
  statusCode: number
  nextOpenTime: string
  currentTime: string
}

// 获取市场状态
export const getMarketStatus = () => {
  return request.get('/stocks/public/market-status')
}
