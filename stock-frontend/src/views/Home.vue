<template>
  <div class="home">
    <!-- 市场状态栏 -->
    <div class="market-status-bar" :class="marketStatusClass">
      <div class="market-status-content">
        <div class="market-status-left">
          <el-tag :type="marketStatusTagType" effect="dark" size="large" class="market-status-tag">
            <el-icon v-if="marketStatus?.isTradingTime" class="is-loading"><Loading /></el-icon>
            <el-icon v-else><Timer /></el-icon>
            {{ marketStatus?.status || '加载中...' }}
          </el-tag>
          <span v-if="marketStatus?.isTradingTime" class="trading-time-hint">
            交易中 {{ currentTime }}
          </span>
          <span v-else-if="marketStatus?.nextOpenTime" class="next-open-hint">
            下次开盘: {{ formatNextOpenTime(marketStatus.nextOpenTime) }}
          </span>
        </div>
        <div class="market-status-right">
          <span class="data-source-hint" v-if="hasCacheData">
            <el-icon><Warning /></el-icon>
            数据有延迟
          </span>
          <el-tag v-if="marketStatus?.isTradingTime" type="success" effect="plain" size="small">
            实时推送中
          </el-tag>
        </div>
      </div>
    </div>

    <!-- Hero Section -->
    <div class="hero-section">
      <div class="hero-content">
        <div class="hero-text">
          <h1>MarketPulse</h1>
          <p class="hero-subtitle">专业的股票行情分析平台</p>
          <p class="hero-description">实时行情数据、智能分析工具、自选股管理，助您把握每一个投资机会</p>
          <div class="hero-stats">
            <div class="stat-item">
              <div class="stat-number" :class="{ 'number-flash': isNumberFlashing }">{{ stockCount }}</div>
              <div class="stat-label">股票覆盖</div>
            </div>
            <div class="stat-item">
              <div class="stat-number" :class="{ 'pulse': marketStatus?.isTradingTime }">实时</div>
              <div class="stat-label">行情更新</div>
            </div>
            <div class="stat-item">
              <div class="stat-number">智能</div>
              <div class="stat-label">分析工具</div>
            </div>
          </div>
        </div>
        <div class="hero-search">
          <div class="search-card">
            <h3>搜索股票</h3>
            <el-autocomplete
              v-model="searchKeyword"
              :fetch-suggestions="querySearch"
              placeholder="输入股票代码或名称，如：600519"
              size="large"
              :prefix-icon="Search"
              clearable
              @select="handleSelect"
              @keyup.enter="handleSearch"
              style="width: 100%"
            >
              <template #default="{ item }">
                <div class="suggestion-item">
                  <span class="suggestion-name" v-html="highlightText(item.name, searchKeyword)"></span>
                  <span class="suggestion-symbol" v-html="highlightText(item.symbol, searchKeyword)"></span>
                  <span class="suggestion-exchange">{{ item.exchange }}</span>
                </div>
              </template>
              <template #append>
                <el-button type="primary" @click="handleSearch" :loading="searchLoading">
                  <el-icon><Search /></el-icon>
                </el-button>
              </template>
            </el-autocomplete>
            <div class="hot-search">
              <span class="hot-label">热门搜索：</span>
              <el-tag v-for="tag in hotSearchTags" :key="tag" size="small" class="hot-tag" @click="quickSearch(tag)">
                {{ tag }}
              </el-tag>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 搜索结果 -->
    <div v-if="searchResults.length > 0" class="search-results">
      <h3>搜索结果</h3>
      <el-table :data="searchResults" style="width: 100%" v-loading="searchLoading">
        <el-table-column prop="symbol" label="代码" width="100">
          <template #default="{ row }">
            <el-link type="primary" @click="goToStockDetail(row.symbol)">{{ row.symbol }}</el-link>
          </template>
        </el-table-column>
        <el-table-column prop="name" label="名称" width="150" />
        <el-table-column prop="currentPrice" label="最新价">
          <template #default="{ row }">
            <span :class="getPriceClass(row.changePercent)">
              {{ row.currentPrice?.toFixed(2) || '-' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="changePercent" label="涨跌幅">
          <template #default="{ row }">
            <span :class="getPriceClass(row.changePercent)">
              {{ formatChangePercent(row.changePercent) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="volume" label="成交量">
          <template #default="{ row }">
            {{ row.volumeDisplay || formatVolume(row.volume) }}
          </template>
        </el-table-column>
        <el-table-column prop="amount" label="成交额">
          <template #default="{ row }">
            {{ row.amountDisplay || formatAmount(row.amount) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button type="primary" size="small" @click="goToStockDetail(row.symbol)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <div v-else-if="hasSearched && !searchLoading" class="no-results">
      <el-empty description="未找到相关股票" />
    </div>

    <div class="market-overview">
      <el-row :gutter="20">
        <el-col :span="6" v-for="item in marketIndices" :key="item.name">
          <el-card class="index-card" :class="[getPriceClass(item.changePercent), getIndexFlashClass(item.name)]">
            <div class="index-name">{{ item.name }}</div>
            <div class="index-value" :class="{ 'number-flash': flashingIndices.has(item.name) }">{{ item.currentPrice?.toFixed(2) || '-' }}</div>
            <div class="index-change">
              <span>{{ formatIndexChange(item.changePrice) }}</span>
              <span>({{ formatChangePercent(item.changePercent) }})</span>
            </div>
          </el-card>
        </el-col>
      </el-row>
    </div>

    <!-- 热门股票 -->
    <div class="hot-stocks-section">
      <h3>热门股票</h3>
      <el-table :data="hotStocks" style="width: 100%" v-loading="loading" :row-class-name="getRowFlashClass">
        <el-table-column prop="symbol" label="代码" width="100">
          <template #default="{ row }">
            <el-link type="primary" @click="goToStockDetail(row.symbol)">{{ row.symbol }}</el-link>
          </template>
        </el-table-column>
        <el-table-column prop="name" label="名称" width="150" />
        <el-table-column prop="currentPrice" label="最新价">
          <template #default="{ row }">
            <span :class="getPriceClass(row.changePercent)">
              {{ row.currentPrice?.toFixed(2) || '-' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="changePercent" label="涨跌幅">
          <template #default="{ row }">
            <span :class="getPriceClass(row.changePercent)">
              {{ formatChangePercent(row.changePercent) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="volume" label="成交量">
          <template #default="{ row }">
            {{ row.volumeDisplay || formatVolume(row.volume) }}
          </template>
        </el-table-column>
        <el-table-column prop="amount" label="成交额">
          <template #default="{ row }">
            {{ row.amountDisplay || formatAmount(row.amount) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button type="primary" size="small" @click="goToStockDetail(row.symbol)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 分隔区域 -->
    <div class="section-divider"></div>

    <!-- 自选股行情 -->
    <div class="hot-stocks-section">
      <div class="section-header">
        <h3>我的自选股</h3>
        <el-tag v-if="!isLoggedIn" type="warning" effect="dark" class="login-tip">
          <el-icon><Lock /></el-icon>
          登录后查看
        </el-tag>
      </div>
      
      <!-- 未登录提示 -->
      <div v-if="!isLoggedIn" class="login-required-notice">
        <el-icon size="48" color="#909399"><Lock /></el-icon>
        <p class="notice-title">🔒 该功能需要登录后才能使用</p>
        <p class="notice-desc">登录后可以添加自选股，实时追踪您关注的股票行情</p>
        <el-button type="primary" size="large" @click="$router.push('/login')">
          <el-icon><User /></el-icon>
          立即登录
        </el-button>
        <p class="notice-sub">
          还没有账号？<el-link type="primary" @click="$router.push('/register')">立即注册</el-link>
        </p>
      </div>
      
      <el-table v-else :data="favoriteStocks" style="width: 100%" v-loading="favoritesLoading" empty-text="暂无自选股，请在股票详情页添加">
        <el-table-column prop="symbol" label="代码" width="100">
          <template #default="{ row }">
            <el-link type="primary" @click="goToStockDetail(row.symbol)">{{ row.symbol }}</el-link>
          </template>
        </el-table-column>
        <el-table-column prop="name" label="名称" width="150" />
        <el-table-column prop="currentPrice" label="最新价">
          <template #default="{ row }">
            <span :class="getPriceClass(row.changePercent)">
              {{ row.currentPrice?.toFixed(2) || '-' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="changePercent" label="涨跌幅">
          <template #default="{ row }">
            <span :class="getPriceClass(row.changePercent)">
              {{ formatChangePercent(row.changePercent) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="volume" label="成交量">
          <template #default="{ row }">
            {{ row.volumeDisplay || formatVolume(row.volume) }}
          </template>
        </el-table-column>
        <el-table-column prop="amount" label="成交额">
          <template #default="{ row }">
            {{ row.amountDisplay || formatAmount(row.amount) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button type="primary" size="small" @click="goToStockDetail(row.symbol)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { Search, Lock, User, Loading, Timer, Warning } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'
import { getStockList, getMarketIndex, searchStocks, suggestStocks, getMarketStatus } from '@/api/stock'
import { getFavorites } from '@/api/favorites'
import { wsService } from '@/services/websocket'
import type { Stock, MarketIndex, StockSuggestion, MarketStatus } from '@/api/stock'
import type { FavoriteStock } from '@/api/favorites'

const router = useRouter()
const userStore = useUserStore()
const isLoggedIn = computed(() => userStore.isLoggedIn)
const searchKeyword = ref('')
const loading = ref(false)
const searchLoading = ref(false)
const hotStocks = ref<Stock[]>([])
const searchResults = ref<Stock[]>([])
const hasSearched = ref(false)
const favoriteStocks = ref<FavoriteStock[]>([])
const favoritesLoading = ref(false)

const marketIndices = ref<MarketIndex[]>([])

// 市场状态
const marketStatus = ref<MarketStatus | null>(null)
const currentTime = ref('')
const stockCount = ref('5000+')
const isNumberFlashing = ref(false)
const hasCacheData = ref(false)

// 定时器
let marketStatusTimer: number | null = null
let timeTimer: number | null = null
let stockCountTimer: number | null = null

// 热门搜索标签
const hotSearchTags = ['贵州茅台', '腾讯控股', '宁德时代', '比亚迪', '阿里巴巴']

// 市场状态样式
const marketStatusClass = computed(() => {
  if (!marketStatus.value) return ''
  if (marketStatus.value.isTradingTime) return 'status-trading'
  if (marketStatus.value.status === '午休') return 'status-lunch'
  return 'status-closed'
})

const marketStatusTagType = computed(() => {
  if (!marketStatus.value) return 'info'
  if (marketStatus.value.isTradingTime) return 'success'
  if (marketStatus.value.status === '午休') return 'warning'
  return 'info'
})

const fetchStocks = async () => {
  loading.value = true
  try {
    const res: any = await getStockList()
    // 按涨跌幅绝对值排序，获取最热门的10只股票
    const sortedStocks = res.data
      .filter((stock: Stock) => stock.changePercent !== undefined)
      .sort((a: Stock, b: Stock) => Math.abs(b.changePercent || 0) - Math.abs(a.changePercent || 0))
    hotStocks.value = sortedStocks.slice(0, 10)
    
    // 获取真实大盘指数
    await fetchMarketIndices()
  } catch (error) {
    console.error('获取股票列表失败:', error)
  } finally {
    loading.value = false
  }
}

const fetchMarketIndices = async () => {
  try {
    const res: any = await getMarketIndex()
    console.log('大盘指数API返回:', res)
    // request拦截器已经返回了response.data，所以res就是ApiResponse对象
    const newIndices = res.data || []
    
    // 检查价格变化并触发闪烁动画
    if (marketIndices.value.length > 0) {
      newIndices.forEach((newIndex: MarketIndex) => {
        const oldIndex = marketIndices.value.find(i => i.name === newIndex.name)
        if (oldIndex && oldIndex.currentPrice !== newIndex.currentPrice) {
          const direction = (newIndex.currentPrice || 0) > (oldIndex.currentPrice || 0) ? 'up' : 'down'
          triggerIndexFlash(newIndex.name, direction)
        }
      })
    }
    
    marketIndices.value = newIndices
    console.log('设置后的marketIndices:', marketIndices.value)
  } catch (error) {
    console.error('获取大盘指数失败:', error)
  }
}

const fetchFavorites = async () => {
  favoritesLoading.value = true
  try {
    const res: any = await getFavorites()
    console.log('自选股API返回:', res)
    if (res.code === 200 && res.data) {
      favoriteStocks.value = res.data
      console.log(`获取到 ${res.data.length} 只自选股`)
    } else {
      favoriteStocks.value = []
    }
  } catch (error) {
    console.error('获取自选股失败:', error)
    favoriteStocks.value = []
  } finally {
    favoritesLoading.value = false
  }
}

const handleSearch = async () => {
  const keyword = searchKeyword.value.trim()
  if (!keyword) {
    searchResults.value = []
    hasSearched.value = false
    return
  }

  searchLoading.value = true
  hasSearched.value = true

  try {
    console.log('搜索股票:', keyword)
    const res: any = await searchStocks(keyword)
    console.log('搜索结果:', res)

    if (res.code === 200 && res.data) {
      searchResults.value = res.data
      console.log(`找到 ${res.data.length} 只股票`)
    } else {
      searchResults.value = []
      console.log('未找到股票')
    }
  } catch (error) {
    console.error('搜索股票失败:', error)
    searchResults.value = []
  } finally {
    searchLoading.value = false
  }
}

const goToStockDetail = (symbol: string) => {
  router.push(`/stock/${symbol}`)
}

// 快速搜索
const quickSearch = (tag: string) => {
  searchKeyword.value = tag
  handleSearch()
}

// 自动补全搜索
const querySearch = async (queryString: string, cb: any) => {
  if (!queryString || queryString.trim().length < 1) {
    cb([])
    return
  }

  try {
    const res: any = await suggestStocks(queryString, 10)
    if (res.code === 200 && res.data) {
      cb(res.data)
    } else {
      cb([])
    }
  } catch (error) {
    console.error('自动补全搜索失败:', error)
    cb([])
  }
}

// 选择自动补全项
const handleSelect = (item: StockSuggestion) => {
  searchKeyword.value = item.name
  handleSearch()
}

// 高亮匹配的关键字
const highlightText = (text: string, keyword: string) => {
  if (!keyword || !text) return text
  const regex = new RegExp(`(${keyword.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')})`, 'gi')
  return text.replace(regex, '<span class="highlight">$1</span>')
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

// 获取价格样式类
const getPriceClass = (changePercent?: number) => {
  if (changePercent === undefined || changePercent === null) return ''
  if (changePercent > 0) return 'up'
  if (changePercent < 0) return 'down'
  return 'flat'
}

// 格式化涨跌幅显示
const formatChangePercent = (changePercent?: number) => {
  if (changePercent === undefined || changePercent === null) return '0.00%'
  const sign = changePercent > 0 ? '+' : ''
  return `${sign}${changePercent.toFixed(2)}%`
}

// 格式化大盘指数涨跌额
const formatIndexChange = (changePrice?: number) => {
  if (changePrice === undefined || changePrice === null) return '0.00'
  const sign = changePrice > 0 ? '+' : ''
  return `${sign}${changePrice.toFixed(2)}`
}

// WebSocket 实时数据更新
const setupWebSocket = () => {
  // 连接 WebSocket
  wsService.connect()

  // 监听市场行情数据
  wsService.onMessage('marketData', (data) => {
    if (data.stocks && data.stocks.length > 0) {
      // 更新热门股票列表（最小化更新，只更新价格变化的）
      updateHotStocksMinimally(data.stocks)
      console.log('收到实时行情数据更新:', data.stocks.length, '只股票')
    }
    // 检查是否有缓存数据标记
    if (data.fromCache) {
      hasCacheData.value = true
    } else {
      hasCacheData.value = false
    }
  })

  // 监听连接状态
  wsService.onMessage('connected', (data) => {
    console.log('WebSocket 连接成功:', data)
  })
}

// 最小化更新热门股票列表
const updateHotStocksMinimally = (newStocks: Stock[]) => {
  newStocks.forEach((newStock: Stock) => {
    const index = hotStocks.value.findIndex(s => s.symbol === newStock.symbol)
    if (index !== -1) {
      // 只更新价格相关字段，保持其他数据不变
      const oldPrice = hotStocks.value[index].currentPrice
      hotStocks.value[index].currentPrice = newStock.currentPrice
      hotStocks.value[index].changePrice = newStock.changePrice
      hotStocks.value[index].changePercent = newStock.changePercent
      hotStocks.value[index].volume = newStock.volume
      hotStocks.value[index].amount = newStock.amount
      hotStocks.value[index].volumeDisplay = newStock.volumeDisplay
      hotStocks.value[index].amountDisplay = newStock.amountDisplay

      // 如果价格变化，触发闪烁动画
      if (oldPrice !== newStock.currentPrice) {
        const direction = (newStock.currentPrice || 0) > (oldPrice || 0) ? 'up' : 'down'
        triggerPriceFlash(hotStocks.value[index].symbol, direction)
      }
    }
  })
}

// 触发价格闪烁动画
const flashingStocks = ref<Set<string>>(new Set())
const flashDirections = ref<Map<string, 'up' | 'down'>>(new Map())
const flashingIndices = ref<Set<string>>(new Set())
const flashIndexDirections = ref<Map<string, 'up' | 'down'>>(new Map())

const triggerPriceFlash = (symbol: string, direction: 'up' | 'down') => {
  flashingStocks.value.add(symbol)
  flashDirections.value.set(symbol, direction)
  setTimeout(() => {
    flashingStocks.value.delete(symbol)
    flashDirections.value.delete(symbol)
  }, 500)
}

const triggerIndexFlash = (name: string, direction: 'up' | 'down') => {
  flashingIndices.value.add(name)
  flashIndexDirections.value.set(name, direction)
  setTimeout(() => {
    flashingIndices.value.delete(name)
    flashIndexDirections.value.delete(name)
  }, 500)
}

// 获取表格行的闪烁类名
const getRowFlashClass = (row: Stock) => {
  if (flashingStocks.value.has(row.symbol)) {
    const direction = flashDirections.value.get(row.symbol)
    return direction === 'up' ? 'price-up-flash' : 'price-down-flash'
  }
  return ''
}

// 获取大盘指数卡片的闪烁类名
const getIndexFlashClass = (name: string) => {
  if (flashingIndices.value.has(name)) {
    const direction = flashIndexDirections.value.get(name)
    return direction === 'up' ? 'index-up-flash' : 'index-down-flash'
  }
  return ''
}

// 获取市场状态
const fetchMarketStatus = async () => {
  try {
    const res: any = await getMarketStatus()
    if (res.code === 200 && res.data) {
      marketStatus.value = res.data
    }
  } catch (error) {
    console.error('获取市场状态失败:', error)
  }
}

// 格式化下次开盘时间
const formatNextOpenTime = (timeStr: string) => {
  const date = new Date(timeStr)
  const now = new Date()
  const isToday = date.toDateString() === now.toDateString()

  if (isToday) {
    return `今天 ${date.getHours().toString().padStart(2, '0')}:${date.getMinutes().toString().padStart(2, '0')}`
  } else {
    return `${date.getMonth() + 1}月${date.getDate()}日 ${date.getHours().toString().padStart(2, '0')}:${date.getMinutes().toString().padStart(2, '0')}`
  }
}

// 更新时间显示
const updateCurrentTime = () => {
  const now = new Date()
  currentTime.value = now.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', second: '2-digit' })
}

// 股票数量跳动动画
const triggerStockCountFlash = () => {
  isNumberFlashing.value = true
  setTimeout(() => {
    isNumberFlashing.value = false
  }, 300)
}

onMounted(() => {
  fetchStocks()
  fetchFavorites()  // 获取自选股
  fetchMarketStatus()  // 获取市场状态
  setupWebSocket()  // 启动 WebSocket 连接

  // 定时更新市场状态（每30秒）
  marketStatusTimer = window.setInterval(() => {
    fetchMarketStatus()
  }, 30000)

  // 定时更新时间（每秒）
  updateCurrentTime()
  timeTimer = window.setInterval(() => {
    updateCurrentTime()
  }, 1000)

  // 定时触发股票数量跳动（每5秒）
  stockCountTimer = window.setInterval(() => {
    triggerStockCountFlash()
  }, 5000)
})

onUnmounted(() => {
  wsService.disconnect()  // 断开 WebSocket 连接

  // 清理定时器
  if (marketStatusTimer) {
    clearInterval(marketStatusTimer)
  }
  if (timeTimer) {
    clearInterval(timeTimer)
  }
  if (stockCountTimer) {
    clearInterval(stockCountTimer)
  }
})
</script>

<style scoped>
.home {
  max-width: 1200px;
  margin: 0 auto;
}

.hero-section {
  background: linear-gradient(135deg, #1a5fb4 0%, #3584e4 100%);
  border-radius: 16px;
  margin-bottom: 30px;
  color: #fff;
  padding: 60px 40px;
  position: relative;
  overflow: hidden;
}

.hero-section::before {
  content: '';
  position: absolute;
  top: -50%;
  right: -20%;
  width: 600px;
  height: 600px;
  background: radial-gradient(circle, rgba(255,255,255,0.1) 0%, transparent 70%);
  border-radius: 50%;
}

.hero-content {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 60px;
  position: relative;
  z-index: 1;
}

.hero-text {
  flex: 1;
}

.hero-text h1 {
  font-size: 48px;
  font-weight: 700;
  margin-bottom: 16px;
  letter-spacing: -1px;
}

.hero-subtitle {
  font-size: 24px;
  font-weight: 500;
  margin-bottom: 12px;
  opacity: 0.95;
}

.hero-description {
  font-size: 16px;
  opacity: 0.8;
  margin-bottom: 32px;
  line-height: 1.6;
}

.hero-stats {
  display: flex;
  gap: 40px;
}

.stat-item {
  text-align: center;
}

.stat-number {
  font-size: 28px;
  font-weight: 700;
  margin-bottom: 4px;
}

.stat-label {
  font-size: 14px;
  opacity: 0.8;
}

.hero-search {
  width: 400px;
}

.search-card {
  background: #fff;
  border-radius: 12px;
  padding: 30px;
  box-shadow: 0 10px 40px rgba(0, 0, 0, 0.2);
}

.search-card h3 {
  color: #333;
  font-size: 20px;
  margin-bottom: 20px;
  font-weight: 600;
}

.hot-search {
  margin-top: 16px;
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.hot-label {
  color: #666;
  font-size: 13px;
}

.hot-tag {
  cursor: pointer;
  transition: all 0.3s;
}

.hot-tag:hover {
  transform: translateY(-2px);
}

.market-overview {
  margin-bottom: 30px;
}

.index-card {
  text-align: center;
  transition: transform 0.3s;
}

.index-card:hover {
  transform: translateY(-5px);
}

.index-name {
  font-size: 14px;
  color: #666;
  margin-bottom: 10px;
}

.index-value {
  font-size: 24px;
  font-weight: bold;
  margin-bottom: 5px;
}

.index-change {
  font-size: 14px;
}

.up {
  color: #f56c6c;
}

.down {
  color: #67c23a;
}

.flat {
  color: #999;
}

.market-widgets {
  margin-bottom: 30px;
}

.market-widgets .el-col {
  margin-bottom: 20px;
}

.hot-stocks-section {
  background: #fff;
  padding: 20px;
  border-radius: 12px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
}

.hot-stocks-section h3 {
  margin-bottom: 20px;
  font-size: 18px;
  color: #333;
}

.search-results {
  background: #fff;
  padding: 20px;
  border-radius: 12px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
  margin-bottom: 30px;
}

.search-results h3 {
  margin-bottom: 20px;
  font-size: 18px;
  color: #333;
}

.no-results {
  margin-bottom: 30px;
}

.section-divider {
  height: 20px;
  background: linear-gradient(to right, transparent, #e4e7ed, transparent);
  margin: 30px 0;
  border-radius: 10px;
}

/* 搜索建议下拉列表样式 */
.suggestion-item {
  display: flex;
  align-items: center;
  padding: 8px 0;
}

.suggestion-name {
  flex: 1;
  font-weight: 500;
}

.suggestion-symbol {
  width: 100px;
  color: #666;
  font-size: 14px;
}

.suggestion-exchange {
  width: 60px;
  color: #999;
  font-size: 12px;
  text-align: right;
}

.highlight {
  color: #409eff;
  font-weight: bold;
  background-color: #ecf5ff;
  padding: 0 2px;
  border-radius: 3px;
}

/* 分区头部 */
.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.section-header h3 {
  margin: 0;
  font-size: 18px;
  color: #333;
}

.login-tip {
  display: flex;
  align-items: center;
  gap: 4px;
}

/* 登录提示区域 */
.login-required-notice {
  text-align: center;
  padding: 60px 20px;
  background: linear-gradient(135deg, #f5f7fa 0%, #e4e7ed 100%);
  border-radius: 12px;
  border: 2px dashed #c0c4cc;
}

.notice-title {
  font-size: 20px;
  font-weight: 600;
  color: #303133;
  margin: 16px 0 8px;
}

.notice-desc {
  font-size: 14px;
  color: #606266;
  margin-bottom: 24px;
}

.notice-sub {
  font-size: 14px;
  color: #909399;
  margin-top: 16px;
}

/* ========== 市场状态栏样式 ========== */
.market-status-bar {
  margin-bottom: 20px;
  padding: 12px 20px;
  border-radius: 8px;
  transition: all 0.3s ease;
}

.market-status-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.market-status-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.market-status-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

/* 交易中状态 - 绿色渐变 */
.status-trading {
  background: linear-gradient(135deg, #67c23a 0%, #85ce61 100%);
  box-shadow: 0 2px 12px rgba(103, 194, 58, 0.3);
  animation: pulse-glow 2s ease-in-out infinite;
}

/* 午休状态 - 橙色渐变 */
.status-lunch {
  background: linear-gradient(135deg, #e6a23c 0%, #ebb563 100%);
  box-shadow: 0 2px 12px rgba(230, 162, 60, 0.3);
}

/* 闭市状态 - 灰色渐变 */
.status-closed {
  background: linear-gradient(135deg, #909399 0%, #a6a9ad 100%);
  box-shadow: 0 2px 8px rgba(144, 147, 153, 0.2);
}

.market-status-tag {
  font-size: 14px;
  font-weight: 600;
}

.trading-time-hint {
  color: #fff;
  font-size: 14px;
  font-weight: 500;
  text-shadow: 0 1px 2px rgba(0, 0, 0, 0.1);
  font-family: 'Courier New', monospace;
}

.next-open-hint {
  color: #fff;
  font-size: 13px;
  opacity: 0.9;
}

.data-source-hint {
  color: #fff;
  font-size: 12px;
  display: flex;
  align-items: center;
  gap: 4px;
  opacity: 0.9;
  animation: blink 2s ease-in-out infinite;
}

/* ========== 数据跳动动画 ========== */
/* 数字闪烁效果 - 价格变化时使用 */
.number-flash {
  animation: number-flash 0.5s ease-out;
}

@keyframes number-flash {
  0% {
    transform: scale(1);
    text-shadow: 0 0 0 transparent;
  }
  25% {
    transform: scale(1.2);
    text-shadow: 0 0 10px rgba(255, 255, 255, 0.8);
  }
  50% {
    transform: scale(1.1);
    text-shadow: 0 0 5px rgba(255, 255, 255, 0.5);
  }
  100% {
    transform: scale(1);
    text-shadow: 0 0 0 transparent;
  }
}

/* 脉冲效果 - 实时推送指示 */
.pulse {
  animation: pulse 1.5s ease-in-out infinite;
}

@keyframes pulse {
  0%, 100% {
    opacity: 1;
    transform: scale(1);
  }
  50% {
    opacity: 0.7;
    transform: scale(1.05);
  }
}

/* 脉冲发光效果 - 市场状态栏 */
@keyframes pulse-glow {
  0%, 100% {
    box-shadow: 0 2px 12px rgba(103, 194, 58, 0.3);
  }
  50% {
    box-shadow: 0 4px 20px rgba(103, 194, 58, 0.5);
  }
}

/* 旋转动画 - 加载中图标 */
.is-loading {
  animation: rotating 1s linear infinite;
}

@keyframes rotating {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}

/* 闪烁动画 - 数据延迟提示 */
@keyframes blink {
  0%, 100% {
    opacity: 1;
  }
  50% {
    opacity: 0.5;
  }
}

/* 价格变化时的背景闪烁 - 用于表格行 */
.price-up-flash {
  animation: price-up-bg 0.5s ease-out;
}

.price-down-flash {
  animation: price-down-bg 0.5s ease-out;
}

@keyframes price-up-bg {
  0% {
    background-color: transparent;
  }
  50% {
    background-color: rgba(245, 108, 108, 0.2);
  }
  100% {
    background-color: transparent;
  }
}

@keyframes price-down-bg {
  0% {
    background-color: transparent;
  }
  50% {
    background-color: rgba(103, 194, 58, 0.2);
  }
  100% {
    background-color: transparent;
  }
}

/* 大盘指数卡片闪烁效果 */
.index-up-flash {
  animation: index-up-flash 0.5s ease-out;
}

.index-down-flash {
  animation: index-down-flash 0.5s ease-out;
}

@keyframes index-up-flash {
  0% {
    box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
    transform: scale(1);
  }
  50% {
    box-shadow: 0 4px 20px rgba(245, 108, 108, 0.4);
    transform: scale(1.02);
  }
  100% {
    box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
    transform: scale(1);
  }
}

@keyframes index-down-flash {
  0% {
    box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
    transform: scale(1);
  }
  50% {
    box-shadow: 0 4px 20px rgba(103, 194, 58, 0.4);
    transform: scale(1.02);
  }
  100% {
    box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
    transform: scale(1);
  }
}

/* ========== 响应式适配 ========== */
@media (max-width: 768px) {
  .market-status-content {
    flex-direction: column;
    gap: 10px;
    align-items: flex-start;
  }

  .market-status-left {
    flex-wrap: wrap;
  }

  .trading-time-hint,
  .next-open-hint {
    font-size: 12px;
  }
}
</style>
