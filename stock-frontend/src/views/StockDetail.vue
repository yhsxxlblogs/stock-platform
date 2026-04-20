<template>
  <div class="stock-detail">
    <div class="stock-header">
      <div class="stock-info">
        <h1>{{ stockDetail?.name }} <span class="symbol">{{ stockDetail?.symbol }}</span></h1>
        <div class="stock-tags">
          <el-tag size="small">{{ stockDetail?.exchange }}</el-tag>
          <el-tag size="small" type="info">{{ stockDetail?.industry }}</el-tag>
        </div>
      </div>
      <div class="stock-price">
        <div class="current-price" :class="[priceClass, { 'price-flash-up': isPriceFlashing && priceFlashDirection === 'up', 'price-flash-down': isPriceFlashing && priceFlashDirection === 'down' }]">
          {{ stockDetail?.currentPrice?.toFixed(2) || '-' }}
        </div>
        <div class="price-change" :class="priceClass">
          <span>{{ stockDetail?.changePrice > 0 ? '+' : '' }}{{ stockDetail?.changePrice?.toFixed(2) || '0.00' }}</span>
          <span>{{ stockDetail?.changePercent > 0 ? '+' : '' }}{{ stockDetail?.changePercent?.toFixed(2) || '0.00' }}%</span>
        </div>
      </div>
      <div class="stock-actions">
        <el-button type="primary" size="large" @click="addToFavorites">
          <el-icon><Star /></el-icon> 加入自选
        </el-button>
      </div>
    </div>

    <el-row :gutter="20" class="detail-content">
      <el-col :span="16">
        <el-card class="chart-card">
          <template #header>
            <div class="chart-header">
              <el-radio-group v-model="chartType" size="small">
                <el-radio-button value="kline">K线图</el-radio-button>
                <el-radio-button value="minute">分时图</el-radio-button>
              </el-radio-group>
              <el-radio-group v-if="chartType === 'kline'" v-model="klinePeriod" size="small" @change="fetchChartData">
                <el-radio-button value="day">日线</el-radio-button>
                <el-radio-button value="week">周线</el-radio-button>
                <el-radio-button value="month">月线</el-radio-button>
              </el-radio-group>
            </div>
          </template>
          <div ref="chartRef" class="stock-chart"></div>
        </el-card>

        <!-- 技术指标 -->
        <el-card class="indicator-card">
          <template #header>
            <span>技术指标</span>
          </template>
          <el-row :gutter="20">
            <el-col :span="6">
              <div class="indicator-item">
                <span class="label">MACD</span>
                <span class="value" :class="getMacdClass()">{{ macdValue }}</span>
              </div>
            </el-col>
            <el-col :span="6">
              <div class="indicator-item">
                <span class="label">KDJ</span>
                <span class="value">{{ kdjValue }}</span>
              </div>
            </el-col>
            <el-col :span="6">
              <div class="indicator-item">
                <span class="label">RSI</span>
                <span class="value">{{ rsiValue }}</span>
              </div>
            </el-col>
            <el-col :span="6">
              <div class="indicator-item">
                <span class="label">成交量</span>
                <span class="value">{{ stockDetail?.volumeDisplay || formatVolume(stockDetail?.volume) }}</span>
              </div>
            </el-col>
          </el-row>
        </el-card>
      </el-col>

      <el-col :span="8">
        <el-card class="data-card">
          <template #header>
            <span>股票数据</span>
          </template>
          <div class="data-grid">
            <div class="data-item">
              <span class="label">今开</span>
              <span class="value">{{ stockDetail?.openPrice?.toFixed(2) || '-' }}</span>
            </div>
            <div class="data-item">
              <span class="label">昨收</span>
              <span class="value">{{ stockDetail?.preClose?.toFixed(2) || '-' }}</span>
            </div>
            <div class="data-item">
              <span class="label">最高</span>
              <span class="value" :class="priceClass">{{ stockDetail?.highPrice?.toFixed(2) || '-' }}</span>
            </div>
            <div class="data-item">
              <span class="label">最低</span>
              <span class="value" :class="priceClass">{{ stockDetail?.lowPrice?.toFixed(2) || '-' }}</span>
            </div>
            <div class="data-item">
              <span class="label">成交量</span>
              <span class="value">{{ stockDetail?.volumeDisplay || formatVolume(stockDetail?.volume) }}</span>
            </div>
            <div class="data-item">
              <span class="label">成交额</span>
              <span class="value">{{ stockDetail?.amountDisplay || formatAmount(stockDetail?.amount) }}</span>
            </div>
            <div class="data-item">
              <span class="label">换手率</span>
              <span class="value">{{ stockDetail?.turnoverRate?.toFixed(2) || '-' }}%</span>
            </div>
            <div class="data-item">
              <span class="label">市值</span>
              <span class="value">{{ stockDetail?.marketCapDisplay || formatAmount(stockDetail?.marketCap) }}</span>
            </div>
          </div>
        </el-card>

        <!-- 买卖五档 -->
        <el-card class="trade-card">
          <template #header>
            <span>买卖盘口</span>
          </template>
          <div class="trade-list">
            <div class="trade-header">
              <span class="trade-label">档位</span>
              <span class="trade-price">价格</span>
              <span class="trade-volume">数量</span>
            </div>
            <!-- 卖五到卖一 -->
            <div v-for="level in askLevels" :key="'ask'+level.level" class="trade-item ask">
              <span class="trade-label">卖{{ level.level }}</span>
              <span class="trade-price" :class="getPriceClass(level.price)">{{ level.price?.toFixed(2) || '-' }}</span>
              <span class="trade-volume">{{ level.volumeDisplay || formatTradeVolume(level.volume) }}</span>
            </div>
            <el-divider />
            <!-- 买一到买五 -->
            <div v-for="level in bidLevels" :key="'bid'+level.level" class="trade-item bid">
              <span class="trade-label">买{{ level.level }}</span>
              <span class="trade-price" :class="getPriceClass(level.price)">{{ level.price?.toFixed(2) || '-' }}</span>
              <span class="trade-volume">{{ level.volumeDisplay || formatTradeVolume(level.volume) }}</span>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Star } from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import { getStockDetail, getKlineData, getMinuteData } from '@/api/stock'
import { addFavorite } from '@/api/favorites'
import { wsService } from '@/services/websocket'
import type { StockDetail, KlineData, MinuteData, TradeLevel } from '@/api/stock'

const route = useRoute()
const symbol = route.params.symbol as string

const stockDetail = ref<StockDetail | null>(null)
const klineData = ref<KlineData[]>([])
const minuteData = ref<MinuteData[]>([])
const chartType = ref('kline')
const klinePeriod = ref('day')
const chartRef = ref<HTMLElement>()
let chart: echarts.ECharts | null = null

// 价格跳动动画状态
const isPriceFlashing = ref(false)
const priceFlashDirection = ref<'up' | 'down'>('up')

const bidLevels = computed<TradeLevel[]>(() => {
  return stockDetail.value?.bidLevels || []
})

const askLevels = computed<TradeLevel[]>(() => {
  // 卖盘倒序显示（卖五在上，卖一在下）
  const levels = stockDetail.value?.askLevels || []
  return [...levels].reverse()
})

const priceClass = computed(() => {
  if (!stockDetail.value?.changePercent) return ''
  return stockDetail.value.changePercent > 0 ? 'up' : stockDetail.value.changePercent < 0 ? 'down' : ''
})

// 技术指标（简化计算）
const macdValue = computed(() => {
  if (klineData.value.length < 26) return '-'
  const close = klineData.value[klineData.value.length - 1].close
  const prevClose = klineData.value[klineData.value.length - 2]?.close || close
  const diff = close - prevClose
  return diff > 0 ? `+${diff.toFixed(2)}` : diff.toFixed(2)
})

const kdjValue = computed(() => {
  if (klineData.value.length < 9) return '-'
  return 'K:50 D:50 J:50'
})

const rsiValue = computed(() => {
  if (klineData.value.length < 14) return '-'
  return '50'
})

const getMacdClass = () => {
  const val = parseFloat(macdValue.value)
  return val > 0 ? 'up' : val < 0 ? 'down' : ''
}

const fetchStockDetail = async () => {
  try {
    const res: any = await getStockDetail(symbol)
    stockDetail.value = res.data
  } catch (error) {
    console.error('获取股票详情失败:', error)
  }
}

const fetchChartData = async () => {
  if (chartType.value === 'kline') {
    await fetchKlineData()
  } else {
    await fetchMinuteData()
  }
}

const fetchKlineData = async () => {
  try {
    const res: any = await getKlineData(symbol, klinePeriod.value)
    klineData.value = res.data
    updateKlineChart()
  } catch (error) {
    console.error('获取K线数据失败:', error)
  }
}

const fetchMinuteData = async () => {
  try {
    const res: any = await getMinuteData(symbol)
    minuteData.value = res.data
    updateMinuteChart()
  } catch (error) {
    console.error('获取分时数据失败:', error)
  }
}

const initChart = () => {
  if (!chartRef.value) return
  chart = echarts.init(chartRef.value)
  window.addEventListener('resize', handleResize)
}

const updateKlineChart = () => {
  if (!chart || klineData.value.length === 0) return

  const dates = klineData.value.map(item => item.date)
  const data = klineData.value.map(item => [
    item.open,
    item.close,
    item.low,
    item.high
  ])
  const volumes = klineData.value.map((item, index) => [index, item.volume, item.close > item.open ? 1 : -1])

  // 计算MA5, MA10, MA20
  const calculateMA = (dayCount: number) => {
    const result = []
    for (let i = 0; i < klineData.value.length; i++) {
      if (i < dayCount - 1) {
        result.push('-')
        continue
      }
      let sum = 0
      for (let j = 0; j < dayCount; j++) {
        sum += klineData.value[i - j].close
      }
      result.push((sum / dayCount).toFixed(2))
    }
    return result
  }

  const option = {
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'cross' },
      formatter: (params: any) => {
        const data = params[0]
        const kline = klineData.value[data.dataIndex]
        return `
          <div style="font-weight:bold">${kline.date}</div>
          <div>开盘: ${kline.open.toFixed(2)}</div>
          <div>收盘: ${kline.close.toFixed(2)}</div>
          <div>最高: ${kline.high.toFixed(2)}</div>
          <div>最低: ${kline.low.toFixed(2)}</div>
          <div>涨跌: ${kline.changePercent.toFixed(2)}%</div>
          <div>成交量: ${formatVolume(kline.volume)}</div>
        `
      }
    },
    legend: {
      data: ['日K', 'MA5', 'MA10', 'MA20'],
      top: 0
    },
    grid: [
      { left: '10%', right: '8%', height: '55%', top: '10%' },
      { left: '10%', right: '8%', top: '70%', height: '16%' }
    ],
    xAxis: [
      {
        type: 'category',
        data: dates,
        scale: true,
        boundaryGap: false,
        axisLine: { onZero: false },
        splitLine: { show: false },
        min: 'dataMin',
        max: 'dataMax'
      },
      {
        type: 'category',
        gridIndex: 1,
        data: dates,
        scale: true,
        boundaryGap: false,
        axisLine: { onZero: false },
        axisTick: { show: false },
        splitLine: { show: false },
        axisLabel: { show: false },
        min: 'dataMin',
        max: 'dataMax'
      }
    ],
    yAxis: [
      {
        scale: true,
        splitArea: { show: true }
      },
      {
        scale: true,
        gridIndex: 1,
        splitNumber: 2,
        axisLabel: { show: false },
        axisLine: { show: false },
        axisTick: { show: false },
        splitLine: { show: false }
      }
    ],
    dataZoom: [
      { type: 'inside', xAxisIndex: [0, 1], start: 50, end: 100 },
      { show: true, xAxisIndex: [0, 1], type: 'slider', top: '90%', start: 50, end: 100 }
    ],
    series: [
      {
        name: '日K',
        type: 'candlestick',
        data: data,
        itemStyle: {
          color: '#ef232a',
          color0: '#14b143',
          borderColor: '#ef232a',
          borderColor0: '#14b143'
        }
      },
      {
        name: 'MA5',
        type: 'line',
        data: calculateMA(5),
        smooth: true,
        lineStyle: { opacity: 0.5, width: 1 }
      },
      {
        name: 'MA10',
        type: 'line',
        data: calculateMA(10),
        smooth: true,
        lineStyle: { opacity: 0.5, width: 1 }
      },
      {
        name: 'MA20',
        type: 'line',
        data: calculateMA(20),
        smooth: true,
        lineStyle: { opacity: 0.5, width: 1 }
      },
      {
        name: '成交量',
        type: 'bar',
        xAxisIndex: 1,
        yAxisIndex: 1,
        data: volumes,
        itemStyle: {
          color: (params: any) => params.value[2] > 0 ? '#ef232a' : '#14b143'
        }
      }
    ]
  }

  chart.setOption(option, true)
}

const updateMinuteChart = () => {
  if (!chart) return

  // 如果没有数据（闭市时），显示空白图表
  if (minuteData.value.length === 0) {
    const option = {
      title: {
        text: '非交易时间，暂无分时数据',
        left: 'center',
        top: 'center',
        textStyle: {
          color: '#999',
          fontSize: 16
        }
      },
      grid: [
        { left: '10%', right: '8%', height: '60%', top: '10%' },
        { left: '10%', right: '8%', top: '75%', height: '15%' }
      ],
      xAxis: [
        { type: 'category', data: [], boundaryGap: false },
        { type: 'category', gridIndex: 1, data: [], boundaryGap: false }
      ],
      yAxis: [
        { scale: true, min: 0, max: 100 },
        { scale: true, gridIndex: 1, splitNumber: 2 }
      ],
      series: [
        { name: '价格', type: 'line', data: [] },
        { name: '成交量', type: 'bar', xAxisIndex: 1, yAxisIndex: 1, data: [] }
      ]
    }
    chart.setOption(option, true)
    return
  }

  const times = minuteData.value.map(item => item.time)
  const prices = minuteData.value.map(item => item.price)
  const volumes = minuteData.value.map((item, index) => [index, item.volume])

  const option = {
    tooltip: {
      trigger: 'axis',
      formatter: (params: any) => {
        const data = minuteData.value[params[0].dataIndex]
        return `
          <div style="font-weight:bold">${data.time}</div>
          <div>价格: ${data.price.toFixed(2)}</div>
          <div>成交量: ${formatVolume(data.volume)}</div>
        `
      }
    },
    grid: [
      { left: '10%', right: '8%', height: '60%', top: '10%' },
      { left: '10%', right: '8%', top: '75%', height: '15%' }
    ],
    xAxis: [
      {
        type: 'category',
        data: times,
        boundaryGap: false,
        axisLine: { onZero: false },
        splitLine: { show: true, lineStyle: { type: 'dashed' } }
      },
      {
        type: 'category',
        gridIndex: 1,
        data: times,
        boundaryGap: false,
        axisLine: { onZero: false },
        axisTick: { show: false },
        splitLine: { show: false },
        axisLabel: { show: false }
      }
    ],
    yAxis: [
      {
        scale: true,
        splitArea: { show: true }
      },
      {
        scale: true,
        gridIndex: 1,
        splitNumber: 2,
        axisLabel: { show: false },
        axisLine: { show: false },
        axisTick: { show: false },
        splitLine: { show: false }
      }
    ],
    series: [
      {
        name: '价格',
        type: 'line',
        data: prices,
        smooth: true,
        symbol: 'none',
        lineStyle: { width: 1 },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(64, 158, 255, 0.3)' },
            { offset: 1, color: 'rgba(64, 158, 255, 0.05)' }
          ])
        }
      },
      {
        name: '成交量',
        type: 'bar',
        xAxisIndex: 1,
        yAxisIndex: 1,
        data: volumes,
        itemStyle: { color: '#409EFF' }
      }
    ]
  }

  chart.setOption(option, true)
}

const handleResize = () => {
  chart?.resize()
}

const addToFavorites = async () => {
  if (!stockDetail.value) return
  
  try {
    await addFavorite(stockDetail.value.id)
    ElMessage.success(`已将 ${stockDetail.value.name} 添加到自选股`)
  } catch (error) {
    console.error('添加自选股失败:', error)
    ElMessage.error('添加失败，请检查是否已登录')
  }
}

const formatVolume = (volume?: number) => {
  if (!volume) return '-'
  if (volume >= 100000000) {
    return (volume / 100000000).toFixed(2) + '亿'
  } else if (volume >= 10000) {
    return (volume / 10000).toFixed(2) + '万'
  }
  return volume.toString()
}

const formatAmount = (amount?: number) => {
  if (!amount) return '-'
  if (amount >= 100000000) {
    return (amount / 100000000).toFixed(2) + '亿'
  } else if (amount >= 10000) {
    return (amount / 10000).toFixed(2) + '万'
  }
  return amount.toFixed(2)
}

const formatTradeVolume = (volume?: number) => {
  if (!volume) return '-'
  if (volume >= 10000) {
    return (volume / 10000).toFixed(1) + '万'
  }
  return volume.toString()
}

const getPriceClass = (price?: number) => {
  if (!price || !stockDetail.value?.preClose) return ''
  const change = price - stockDetail.value.preClose
  return change > 0 ? 'up' : change < 0 ? 'down' : ''
}

watch(chartType, () => {
  fetchChartData()
})

// WebSocket 实时数据更新
const setupWebSocket = () => {
  // 连接 WebSocket
  wsService.connect()

  // 订阅当前股票
  wsService.subscribeStocks([symbol])

  // 监听市场行情数据
  wsService.onMessage('marketData', (data) => {
    if (data.stocks && data.stocks.length > 0) {
      const stock = data.stocks.find((s: any) => s.symbol === symbol)
      if (stock && stockDetail.value) {
        const oldPrice = stockDetail.value.currentPrice
        const newPrice = stock.currentPrice

        // 更新股票详情
        stockDetail.value.currentPrice = stock.currentPrice
        stockDetail.value.changePrice = stock.changePrice
        stockDetail.value.changePercent = stock.changePercent
        stockDetail.value.volume = stock.volume
        stockDetail.value.amount = stock.amount
        stockDetail.value.highPrice = stock.highPrice
        stockDetail.value.lowPrice = stock.lowPrice
        stockDetail.value.openPrice = stock.openPrice
        stockDetail.value.preClose = stock.preClose

        // 触发价格跳动动画
        if (oldPrice !== newPrice) {
          priceFlashDirection.value = newPrice > oldPrice ? 'up' : 'down'
          isPriceFlashing.value = true
          setTimeout(() => {
            isPriceFlashing.value = false
          }, 500)
        }

        console.log('收到实时数据更新:', newPrice)
      }
    }
  })

  // 监听连接状态
  wsService.onMessage('connected', (data) => {
    console.log('WebSocket 连接成功:', data)
  })

  // 监听错误
  wsService.onMessage('error', (data) => {
    console.error('WebSocket 错误:', data)
  })
}

onMounted(() => {
  fetchStockDetail()
  initChart()  // 先初始化图表
  fetchChartData()  // 再获取数据
  setupWebSocket()  // 启动 WebSocket 连接
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  chart?.dispose()
  wsService.disconnect()  // 断开 WebSocket 连接
})
</script>

<style scoped>
.stock-detail {
  max-width: 1400px;
  margin: 0 auto;
}

.stock-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: #fff;
  padding: 20px 30px;
  border-radius: 12px;
  margin-bottom: 20px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
}

.stock-info h1 {
  font-size: 24px;
  margin-bottom: 10px;
}

.symbol {
  font-size: 16px;
  color: #999;
  font-weight: normal;
}

.stock-tags {
  display: flex;
  gap: 10px;
}

.stock-price {
  text-align: center;
}

.current-price {
  font-size: 36px;
  font-weight: bold;
  transition: all 0.3s ease;
}

.price-flash-up {
  animation: price-flash-up 0.5s ease-out;
}

.price-flash-down {
  animation: price-flash-down 0.5s ease-out;
}

@keyframes price-flash-up {
  0% {
    transform: scale(1);
    text-shadow: 0 0 0 transparent;
  }
  25% {
    transform: scale(1.15);
    text-shadow: 0 0 15px rgba(245, 108, 108, 0.6);
  }
  50% {
    transform: scale(1.08);
    text-shadow: 0 0 8px rgba(245, 108, 108, 0.4);
  }
  100% {
    transform: scale(1);
    text-shadow: 0 0 0 transparent;
  }
}

@keyframes price-flash-down {
  0% {
    transform: scale(1);
    text-shadow: 0 0 0 transparent;
  }
  25% {
    transform: scale(1.15);
    text-shadow: 0 0 15px rgba(103, 194, 58, 0.6);
  }
  50% {
    transform: scale(1.08);
    text-shadow: 0 0 8px rgba(103, 194, 58, 0.4);
  }
  100% {
    transform: scale(1);
    text-shadow: 0 0 0 transparent;
  }
}

.price-change {
  font-size: 16px;
  margin-top: 5px;
}

.price-change span {
  margin: 0 5px;
}

.up {
  color: #f56c6c;
}

.down {
  color: #67c23a;
}

.detail-content {
  margin-top: 20px;
}

.chart-card {
  margin-bottom: 20px;
}

.chart-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.stock-chart {
  width: 100%;
  height: 500px;
}

.indicator-card {
  margin-bottom: 20px;
}

.indicator-item {
  text-align: center;
  padding: 15px;
  background: #f5f7fa;
  border-radius: 8px;
}

.indicator-item .label {
  display: block;
  color: #666;
  font-size: 14px;
  margin-bottom: 5px;
}

.indicator-item .value {
  display: block;
  font-size: 18px;
  font-weight: bold;
  color: #333;
}

.data-card, .trade-card {
  margin-bottom: 20px;
}

.data-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 15px;
}

.data-item {
  display: flex;
  justify-content: space-between;
  padding: 10px 0;
  border-bottom: 1px solid #eee;
}

.data-item .label {
  color: #666;
}

.data-item .value {
  font-weight: bold;
}

.trade-list {
  padding: 10px 0;
}

.trade-header {
  display: flex;
  justify-content: space-between;
  padding: 8px 0;
  font-weight: bold;
  color: #666;
  border-bottom: 1px solid #eee;
  margin-bottom: 5px;
}

.trade-item {
  display: flex;
  justify-content: space-between;
  padding: 6px 0;
  font-size: 14px;
}

.trade-label {
  color: #666;
  width: 40px;
}

.trade-price {
  flex: 1;
  text-align: center;
  font-weight: bold;
}

.trade-volume {
  width: 80px;
  text-align: right;
  color: #666;
}

.trade-item.ask .trade-price {
  color: #67c23a;
}

.trade-item.bid .trade-price {
  color: #f56c6c;
}
</style>
