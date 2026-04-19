<template>
  <el-container class="main-layout">
    <el-header class="header">
      <div class="logo">
        <el-icon size="28" color="#409EFF"><TrendCharts /></el-icon>
        <span class="title">MarketPulse</span>
      </div>
      <div class="nav-menu">
        <el-menu
          :default-active="activeMenu"
          mode="horizontal"
          router
          background-color="#fff"
          text-color="#333"
          active-text-color="#409EFF"
        >
          <el-menu-item index="/">首页</el-menu-item>
          <el-menu-item index="/market">行情中心</el-menu-item>
          <el-menu-item index="/favorites">自选股</el-menu-item>
        </el-menu>
      </div>
      <div class="user-info">
        <template v-if="userStore.isLoggedIn">
          <el-dropdown @command="handleCommand">
            <span class="user-dropdown">
              <el-avatar :size="32" :src="avatarUrl" />
              <span class="username">{{ userStore.user?.username }}</span>
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile">个人中心</el-dropdown-item>
                <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </template>
        <template v-else>
          <el-button type="primary" @click="$router.push('/login')">登录</el-button>
          <el-button @click="$router.push('/register')">注册</el-button>
        </template>
      </div>
    </el-header>
    
    <el-main class="main-content">
      <router-view />
    </el-main>
    
    <el-footer class="footer">
      <p>© 2024 MarketPulse - 专业股票行情分析平台</p>
    </el-footer>
  </el-container>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'
import { ElMessageBox } from 'element-plus'
import { TrendCharts, ArrowDown } from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const activeMenu = computed(() => route.path)

// 默认头像
const defaultAvatar = 'https://cube.elemecdn.com/3/7c/3ea6beec64369c2642b92c6726f1epng.png'

// 头像URL（处理空值情况）
const avatarUrl = computed(() => {
  // 如果用户未登录或没有用户信息，返回默认头像
  if (!userStore.user) return defaultAvatar
  
  const avatar = userStore.user.avatar
  if (!avatar) return defaultAvatar
  
  // 如果头像URL已经是完整URL，直接返回
  if (avatar.startsWith('http')) return avatar
  
  // 后端返回的路径是 /files/...，需要添加 /api 前缀
  // Nginx 会将 /api/... 代理到后端
  if (avatar.startsWith('/files/')) {
    return '/api' + avatar
  }
  
  return avatar
})

const handleCommand = (command: string) => {
  if (command === 'profile') {
    router.push('/profile')
  } else if (command === 'logout') {
    ElMessageBox.confirm('确定要退出登录吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    }).then(() => {
      userStore.logout()
      router.push('/login')
    })
  }
}
</script>

<style scoped>
.main-layout {
  min-height: 100vh;
}

.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background-color: #fff;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
  padding: 0 40px;
}

.logo {
  display: flex;
  align-items: center;
  gap: 10px;
}

.logo .title {
  font-size: 20px;
  font-weight: bold;
  color: #333;
}

.nav-menu {
  flex: 1;
  margin-left: 40px;
}

.nav-menu :deep(.el-menu) {
  border-bottom: none;
}

.nav-menu :deep(.el-menu-item:hover) {
  background-color: #f5f7fa !important;
}

.nav-menu :deep(.el-menu-item.is-active) {
  background-color: transparent !important;
}

/* 修复点击后的黑色阴影问题 */
.nav-menu :deep(.el-menu-item:focus) {
  background-color: transparent !important;
  outline: none !important;
}

.nav-menu :deep(.el-menu-item:active) {
  background-color: #f5f7fa !important;
  outline: none !important;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 10px;
}

.user-dropdown {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  padding: 5px 10px;
  border-radius: 4px;
  transition: background-color 0.3s;
}

.user-dropdown:hover {
  background-color: #f5f7fa;
}

.username {
  font-size: 14px;
  color: #333;
}

.main-content {
  padding: 20px 40px;
  background-color: #f5f7fa;
  min-height: calc(100vh - 120px);
}

.footer {
  text-align: center;
  background-color: #fff;
  color: #999;
  font-size: 14px;
}
</style>
