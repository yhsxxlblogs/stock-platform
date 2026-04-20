<template>
  <div class="register-page">
    <!-- 左侧装饰区域 -->
    <div class="register-banner">
      <div class="banner-content">
        <el-icon size="64" color="#fff"><TrendCharts /></el-icon>
        <h1>MarketPulse</h1>
        <p>专业的股票行情分析平台</p>
        <div class="features">
          <div class="feature-item">
            <el-icon><DataLine /></el-icon>
            <span>实时行情数据</span>
          </div>
          <div class="feature-item">
            <el-icon><TrendCharts /></el-icon>
            <span>智能分析工具</span>
          </div>
          <div class="feature-item">
            <el-icon><Collection /></el-icon>
            <span>自选股管理</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 右侧注册表单 -->
    <div class="register-form-section">
      <div class="register-box">
        <div class="register-header">
          <h2>创建账户</h2>
          <p>加入 MarketPulse，开启专业投资之旅</p>
        </div>

        <el-form
          ref="registerFormRef"
          :model="registerForm"
          :rules="registerRules"
          class="register-form"
          @keyup.enter="handleRegister"
        >
          <el-form-item prop="username">
            <el-input
              v-model="registerForm.username"
              placeholder="用户名"
              size="large"
              :prefix-icon="User"
              clearable
            />
          </el-form-item>

          <el-form-item prop="email">
            <el-input
              v-model="registerForm.email"
              placeholder="邮箱"
              size="large"
              :prefix-icon="Message"
              clearable
            />
          </el-form-item>

          <el-form-item prop="password">
            <el-input
              v-model="registerForm.password"
              type="password"
              placeholder="密码"
              size="large"
              :prefix-icon="Lock"
              show-password
              clearable
            />
          </el-form-item>

          <el-form-item prop="confirmPassword">
            <el-input
              v-model="registerForm.confirmPassword"
              type="password"
              placeholder="确认密码"
              size="large"
              :prefix-icon="Lock"
              show-password
              clearable
            />
          </el-form-item>

          <el-form-item prop="phone">
            <el-input
              v-model="registerForm.phone"
              placeholder="手机号（选填）"
              size="large"
              :prefix-icon="Phone"
              clearable
            />
          </el-form-item>

          <el-form-item>
            <el-button
              type="primary"
              size="large"
              class="register-button"
              :loading="loading"
              @click="handleRegister"
            >
              注 册
            </el-button>
          </el-form-item>
        </el-form>

        <div class="register-footer">
          <span>已有账户？</span>
          <el-link type="primary" @click="$router.push('/login')">立即登录</el-link>
        </div>

        <div class="agreement">
          <el-checkbox v-model="agreeTerms" size="small">
            我已阅读并同意
            <el-link type="primary" :underline="false" size="small">用户协议</el-link>
            和
            <el-link type="primary" :underline="false" size="small">隐私政策</el-link>
          </el-checkbox>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock, Message, Phone, TrendCharts, DataLine, Collection } from '@element-plus/icons-vue'
import { register } from '@/api/auth'
import { useUserStore } from '@/store/user'

const router = useRouter()
const userStore = useUserStore()
const registerFormRef = ref()
const loading = ref(false)
const agreeTerms = ref(false)

const registerForm = reactive({
  username: '',
  email: '',
  password: '',
  confirmPassword: '',
  phone: ''
})

const validateConfirmPassword = (rule: any, value: string, callback: any) => {
  if (value !== registerForm.password) {
    callback(new Error('两次输入的密码不一致'))
  } else {
    callback()
  }
}

const registerRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 50, message: '用户名长度3-50个字符', trigger: 'blur' }
  ],
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '邮箱格式不正确', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码长度至少6位', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请确认密码', trigger: 'blur' },
    { validator: validateConfirmPassword, trigger: 'blur' }
  ]
}

const handleRegister = async () => {
  if (!agreeTerms.value) {
    ElMessage.warning('请阅读并同意用户协议和隐私政策')
    return
  }

  const valid = await registerFormRef.value?.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    const { confirmPassword, ...registerData } = registerForm
    const res: any = await register(registerData)
    const { token, userId, username, email } = res.data

    userStore.setToken(token)
    userStore.setUser({ id: userId, username, email })

    ElMessage.success('注册成功')
    router.push('/')
  } catch (error) {
    console.error('注册失败:', error)
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.register-page {
  min-height: 100vh;
  display: flex;
  background: #f5f7fa;
}

/* 左侧banner区域 */
.register-banner {
  flex: 1;
  background: linear-gradient(135deg, #1a5fb4 0%, #3584e4 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px;
  position: relative;
  overflow: hidden;
}

.register-banner::before {
  content: '';
  position: absolute;
  top: -50%;
  left: -50%;
  width: 200%;
  height: 200%;
  background: radial-gradient(circle, rgba(255,255,255,0.1) 1px, transparent 1px);
  background-size: 20px 20px;
  opacity: 0.3;
}

.banner-content {
  text-align: center;
  color: #fff;
  z-index: 1;
}

.banner-content h1 {
  font-size: 42px;
  margin: 20px 0 10px;
  font-weight: 600;
  letter-spacing: 2px;
}

.banner-content p {
  font-size: 18px;
  opacity: 0.9;
  margin-bottom: 40px;
}

.features {
  display: flex;
  flex-direction: column;
  gap: 20px;
  margin-top: 40px;
}

.feature-item {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  font-size: 16px;
  opacity: 0.9;
}

/* 右侧表单区域 */
.register-form-section {
  width: 480px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px;
  background: #fff;
}

.register-box {
  width: 100%;
  max-width: 360px;
}

.register-header {
  text-align: center;
  margin-bottom: 30px;
}

.register-header h2 {
  font-size: 28px;
  color: #333;
  margin-bottom: 8px;
  font-weight: 600;
}

.register-header p {
  color: #999;
  font-size: 14px;
}

.register-form {
  margin-top: 20px;
}

.register-button {
  width: 100%;
  height: 44px;
  font-size: 16px;
  letter-spacing: 2px;
}

/* 底部登录链接 */
.register-footer {
  margin-top: 24px;
  text-align: center;
  color: #666;
  font-size: 14px;
}

/* 用户协议 */
.agreement {
  margin-top: 20px;
  text-align: center;
}

.agreement :deep(.el-checkbox__label) {
  font-size: 13px;
  color: #666;
}

/* 响应式 */
@media (max-width: 768px) {
  .register-banner {
    display: none;
  }

  .register-form-section {
    width: 100%;
    padding: 20px;
  }
}
</style>
