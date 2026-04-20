<template>
  <div class="market">
    <div class="search-section">
      <el-autocomplete
        v-model="searchKeyword"
        :fetch-suggestions="querySearch"
        placeholder="输入股票代码或名称"
        size="large"
        :prefix-icon="Search"
        clearable
        @select="handleSelect"
        @keyup.enter="handleSearch"
        style="width: 100%"
      >
        <template #default="{ item }">
          <div class="suggestion-item">
            <span class="stock-name" v-html="highlightText(item.name, searchKeyword)"></span>
            <span class="stock-symbol" v-html="highlightText(item.symbol, searchKeyword)"></span>
            <span class="stock-exchange">{{ item.exchange }}</span>
          </div>
        </template>
        <template #append>
          <el-button type="primary" @click="handleSearch" :loading="searchLoading">搜索</el-button>
        </template>
      </el-autocomplete>
    </div>

    <!-- 搜索结果 -->
    <div v-if="searchResults.length > 0" class="search-results">
      <el-card class="stock-table-card">
        <template #header>
          <div class="card-header">
            <span>搜索结果</span>
            <el-radio-group v-model="sortBy" size="small" @change="handleSort">
              <el-radio-button value="changePercent">涨跌幅</el-radio-button>
              <el-radio-button value="volume">成交量</el-radio-button>
              <el-radio-button value="amount">成交额</el-radio-button>
            </el-radio-group>
          </div>
        </template>

        <el-table
          :data="sortedSearchResults"
          style="width: 100%"
          v-loading="searchLoading"
          @row-click="handleRowClick"
        >
          <el-table-column prop="symbol" label="代码" width="100">
            <template #default="{ row }">
              <el-link type="primary" @click="goToDetail(row.symbol)">{{ row.symbol }}</el-link>
            </template>
          </el-table-column>
          <el-table-column prop="name" label="名称" width="150" />
          <el-table-column prop="industry" label="行业" width="120" />
          <el-table-column prop="currentPrice" label="最新价" sortable>
            <template #default="{ row }">
              <span :class="getPriceClass(row.changePercent)">
                {{ row.currentPrice?.toFixed(2) || '-' }}
              </span>
            </template>
          </el-table-column>
          <el-table-column prop="changePrice" label="涨跌额">
            <template #default="{ row }">
              <span :class="getPriceClass(row.changePercent)">
                {{ row.changePrice > 0 ? '+' : '' }}{{ row.changePrice?.toFixed(2) || '0.00' }}
              </span>
            </template>
          </el-table-column>
          <el-table-column prop="changePercent" label="涨跌幅" sortable>
            <template #default="{ row }">
              <span :class="getPriceClass(row.changePercent)">
                {{ row.changePercent > 0 ? '+' : '' }}{{ row.changePercent?.toFixed(2) || '0.00' }}%
              </span>
            </template>
          </el-table-column>
          <el-table-column prop="volume" label="成交量" sortable>
            <template #default="{ row }">
              {{ row.volumeDisplay || formatVolume(row.volume) }}
            </template>
          </el-table-column>
          <el-table-column prop="amount" label="成交额" sortable>
            <template #default="{ row }">
              {{ row.amountDisplay || formatAmount(row.amount) }}
            </template>
          </el-table-column>
          <el-table-column prop="highPrice" label="最高">
            <template #default="{ row }">
              {{ row.highPrice?.toFixed(2) || '-' }}
            </template>
          </el-table-column>
          <el-table-column prop="lowPrice" label="最低">
            <template #default="{ row }">
              {{ row.lowPrice?.toFixed(2) || '-' }}
            </template>
          </el-table-column>
          <el-table-column label="操作" width="180" fixed="right">
            <template #default="{ row }">
              <el-button type="primary" size="small" @click.stop="goToDetail(row.symbol)">详情</el-button>
              <el-button 
                size="small" 
                :type="isFavorite(row) ? 'success' : 'default'"
                @click.stop="addToFavorites(row)"
              >
                {{ isFavorite(row) ? '已自选' : '+自选' }}
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-card>
    </div>

    <div v-else-if="hasSearched && !searchLoading" class="no-results">
      <el-empty description="未找到相关股票" />
    </div>

    <!-- 默认股票列表 -->
    <el-card v-else class="stock-table-card">
      <template #header>
        <div class="card-header">
          <span>热门股票</span>
          <el-radio-group v-model="sortBy" size="small" @change="handleSort">
            <el-radio-button value="changePercent">涨跌幅</el-radio-button>
            <el-radio-button value="volume">成交量</el-radio-button>
            <el-radio-button value="amount">成交额</el-radio-button>
          </el-radio-group>
        </div>
      </template>

      <el-table
        :data="pagedStocks"
        style="width: 100%"
        v-loading="loading"
        @row-click="handleRowClick"
      >
        <el-table-column prop="symbol" label="代码" width="100">
          <template #default="{ row }">
            <el-link type="primary" @click="goToDetail(row.symbol)">{{ row.symbol }}</el-link>
          </template>
        </el-table-column>
        <el-table-column prop="name" label="名称" width="150" />
        <el-table-column prop="industry" label="行业" width="120" />
        <el-table-column prop="currentPrice" label="最新价" sortable>
          <template #default="{ row }">
            <span :class="getPriceClass(row.changePercent)">
              {{ row.currentPrice?.toFixed(2) || '-' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="changePrice" label="涨跌额">
          <template #default="{ row }">
            <span :class="getPriceClass(row.changePercent)">
              {{ row.changePrice > 0 ? '+' : '' }}{{ row.changePrice?.toFixed(2) || '0.00' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="changePercent" label="涨跌幅" sortable>
          <template #default="{ row }">
            <span :class="getPriceClass(row.changePercent)">
              {{ row.changePercent > 0 ? '+' : '' }}{{ row.changePercent?.toFixed(2) || '0.00' }}%
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="volume" label="成交量" sortable>
          <template #default="{ row }">
            {{ row.volumeDisplay || formatVolume(row.volume) }}
          </template>
        </el-table-column>
        <el-table-column prop="amount" label="成交额" sortable>
          <template #default="{ row }">
            {{ row.amountDisplay || formatAmount(row.amount) }}
          </template>
        </el-table-column>
        <el-table-column prop="highPrice" label="最高">
          <template #default="{ row }">
            {{ row.highPrice?.toFixed(2) || '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="lowPrice" label="最低">
          <template #default="{ row }">
            {{ row.lowPrice?.toFixed(2) || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" size="small" @click.stop="goToDetail(row.symbol)">详情</el-button>
            <el-button 
              size="small" 
              :type="isFavorite(row) ? 'success' : 'default'"
              @click.stop="addToFavorites(row)"
            >
              {{ isFavorite(row) ? '已自选' : '+自选' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="total"
          layout="total, sizes, prev, pager, next"
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { Search } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { getStockList, searchStocks, suggestStocks } from '@/api/stock'
import { addFavorite, getFavorites } from '@/api/favorites'
import type { Stock, StockSuggestion } from '@/api/stock'

const router = useRouter()

const searchKeyword = ref('')
const loading = ref(false)
const searchLoading = ref(false)
const stocks = ref<Stock[]>([])
const searchResults = ref<Stock[]>([])
const hasSearched = ref(false)
const favorites = ref<FavoriteStock[]>([])
const sortBy = ref('changePercent')
const currentPage = ref(1)
const pageSize = ref(20)

const total = computed(() => stocks.value.length)

// 排序后的股票列表
const sortedStocks = computed(() => {
  let result = [...stocks.value]
  result.sort((a, b) => {
    const aVal = a[sortBy.value as keyof Stock] as number || 0
    const bVal = b[sortBy.value as keyof Stock] as number || 0
    return bVal - aVal
  })
  return result
})

// 分页后的股票列表
const pagedStocks = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  const end = start + pageSize.value
  return sortedStocks.value.slice(start, end)
})

// 排序后的搜索结果
const sortedSearchResults = computed(() => {
  let result = [...searchResults.value]
  result.sort((a, b) => {
    const aVal = a[sortBy.value as keyof Stock] as number || 0
    const bVal = b[sortBy.value as keyof Stock] as number || 0
    return bVal - aVal
  })
  return result
})

const fetchStocks = async () => {
  loading.value = true
  try {
    const res: any = await getStockList()
    stocks.value = res.data
  } catch (error) {
    console.error('获取股票列表失败:', error)
  } finally {
    loading.value = false
  }
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

// 搜索股票
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
    const res: any = await searchStocks(keyword)
    if (res.code === 200 && res.data) {
      searchResults.value = res.data
    } else {
      searchResults.value = []
    }
  } catch (error) {
    console.error('搜索失败:', error)
    searchResults.value = []
  } finally {
    searchLoading.value = false
  }
}

const handleSort = () => {
  currentPage.value = 1
}

const handleRowClick = (row: Stock) => {
  goToDetail(row.symbol)
}

const goToDetail = (symbol: string) => {
  router.push(`/stock/${symbol}`)
}

const fetchFavorites = async () => {
  try {
    const res: any = await getFavorites()
    favorites.value = res.data || []
  } catch (error) {
    console.error('获取自选股失败:', error)
  }
}

const addToFavorites = async (stock: Stock) => {
  const exists = favorites.value.some(f => f.symbol === stock.symbol)
  if (exists) {
    ElMessage.warning(`${stock.name} 已在自选股中`)
    return
  }
  
  try {
    await addFavorite(stock.id)
    ElMessage.success(`已将 ${stock.name} 添加到自选股`)
    await fetchFavorites()
  } catch (error: any) {
    if (error.response?.status === 401) {
      ElMessage.warning('🔒 该功能需要登录后才能使用，正在跳转登录页面...')
      setTimeout(() => {
        router.push('/login')
      }, 1500)
    } else {
      ElMessage.error('添加失败，请重试')
    }
  }
}

const isFavorite = (stock: Stock) => {
  return favorites.value.some(f => f.symbol === stock.symbol)
}

const handleSizeChange = (val: number) => {
  pageSize.value = val
  currentPage.value = 1
}

const handleCurrentChange = (val: number) => {
  currentPage.value = val
}

const getPriceClass = (changePercent?: number) => {
  if (!changePercent) return ''
  return changePercent > 0 ? 'up' : changePercent < 0 ? 'down' : ''
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

// 高亮匹配的关键字
const highlightText = (text: string, keyword: string) => {
  if (!keyword || !text) return text
  const regex = new RegExp(`(${keyword.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')})`, 'gi')
  return text.replace(regex, '<span class="highlight">$1</span>')
}

onMounted(() => {
  fetchFavorites()
  fetchStocks()
})
</script>

<style scoped>
.market {
  max-width: 1400px;
  margin: 0 auto;
}

.search-section {
  margin-bottom: 20px;
  max-width: 600px;
}

.suggestion-item {
  display: flex;
  align-items: center;
  padding: 8px 0;
}

.stock-name {
  flex: 1;
  font-weight: 500;
}

.stock-symbol {
  width: 100px;
  color: #666;
  font-size: 14px;
}

.stock-exchange {
  width: 60px;
  color: #999;
  font-size: 12px;
  text-align: right;
}

.search-results {
  margin-bottom: 20px;
}

.no-results {
  margin: 40px 0;
}

.stock-table-card {
  margin-bottom: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.up {
  color: #ff0000;
  font-weight: 600;
}

.down {
  color: #00aa00;
  font-weight: 600;
}

.pagination {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}

:deep(.el-table__row) {
  cursor: pointer;
}

:deep(.el-table__row:hover) {
  background-color: #f5f7fa;
}

.highlight {
  color: #409eff;
  font-weight: bold;
  background-color: #ecf5ff;
  padding: 0 2px;
  border-radius: 3px;
}
</style>
