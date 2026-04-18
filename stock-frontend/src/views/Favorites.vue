<template>
  <div class="favorites">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>我的自选股</span>
          <el-button type="primary" @click="$router.push('/market')">添加股票</el-button>
        </div>
      </template>

      <el-table :data="favorites" style="width: 100%" v-loading="loading">
        <el-table-column prop="symbol" label="代码" width="100">
          <template #default="{ row }">
            <el-link type="primary" @click="goToDetail(row.symbol)">{{ row.symbol }}</el-link>
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
              {{ row.changePercent > 0 ? '+' : '' }}{{ row.changePercent?.toFixed(2) || '0.00' }}%
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="volume" label="成交量">
          <template #default="{ row }">
            {{ row.volumeDisplay || formatVolume(row.volume) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150">
          <template #default="{ row }">
            <el-button type="primary" size="small" @click="goToDetail(row.symbol)">详情</el-button>
            <el-button type="danger" size="small" @click="removeFavorite(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="favorites.length === 0 && !loading" description="暂无自选股，快去添加吧" />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getFavorites, removeFavorite as removeFavoriteApi } from '@/api/favorites'
import type { Stock } from '@/api/stock'

const router = useRouter()
const loading = ref(false)
const favorites = ref<Stock[]>([])

const fetchFavorites = async () => {
  loading.value = true
  try {
    const res: any = await getFavorites()
    favorites.value = res.data || []
  } catch (error) {
    console.error('获取自选股失败:', error)
    ElMessage.error('获取自选股失败，请检查是否已登录')
  } finally {
    loading.value = false
  }
}

const goToDetail = (symbol: string) => {
  router.push(`/stock/${symbol}`)
}

const removeFavorite = async (stock: Stock) => {
  try {
    await ElMessageBox.confirm(`确定要删除 ${stock.name} 吗？`, '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    
    console.log('删除自选股，stockId:', stock.id)
    const res: any = await removeFavoriteApi(stock.id)
    console.log('删除自选股响应:', res)
    
    if (res.code === 200) {
      favorites.value = favorites.value.filter(item => item.id !== stock.id)
      ElMessage.success('删除成功')
    } else {
      ElMessage.error(res.message || '删除失败')
    }
  } catch (error: any) {
    if (error.action === 'cancel') {
      return // 用户取消，不处理
    }
    console.error('删除自选股失败:', error)
    console.error('错误详情:', error.response?.data || error.message)
    ElMessage.error(error.response?.data?.message || '删除失败，请检查是否已登录')
  }
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

onMounted(() => {
  fetchFavorites()
})
</script>

<style scoped>
.favorites {
  max-width: 1200px;
  margin: 0 auto;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.up {
  color: #f56c6c;
}

.down {
  color: #67c23a;
}
</style>
