<template>
  <div class="profile">
    <el-row :gutter="20">
      <el-col :span="8">
        <el-card class="user-card">
          <div class="user-avatar">
            <el-avatar :size="100" :src="avatarUrl" />
            <el-upload
              class="avatar-uploader"
              action="/api/files/avatar"
              :headers="uploadHeaders"
              :show-file-list="false"
              :before-upload="beforeAvatarUpload"
              :on-success="handleAvatarSuccess"
              :on-error="handleAvatarError"
              accept="image/*"
            >
              <el-button type="primary" size="small" class="upload-btn">
                <el-icon><Upload /></el-icon>
                更换头像
              </el-button>
            </el-upload>
          </div>
          <div class="user-name">{{ userStore.user?.username }}</div>
          <div class="user-email">{{ userStore.user?.email }}</div>
          <div class="user-stats">
            <div class="stat-item">
              <div class="stat-value">{{ favoritesCount }}</div>
              <div class="stat-label">自选股</div>
            </div>
            <div class="stat-item">
              <div class="stat-value">{{ tradeCount }}</div>
              <div class="stat-label">交易记录</div>
            </div>
          </div>
        </el-card>
      </el-col>

      <el-col :span="16">
        <el-card>
          <template #header>
            <span>个人信息</span>
          </template>

          <el-form :model="profileForm" label-width="100px" class="profile-form" :rules="profileRules" ref="profileFormRef">
            <el-form-item label="用户名">
              <el-input v-model="profileForm.username" disabled />
            </el-form-item>
            <el-form-item label="邮箱" prop="email">
              <el-input v-model="profileForm.email" />
            </el-form-item>
            <el-form-item label="手机号" prop="phone">
              <el-input v-model="profileForm.phone" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="saveProfile" :loading="saving">保存修改</el-button>
            </el-form-item>
          </el-form>
        </el-card>

        <el-card style="margin-top: 20px;">
          <template #header>
            <span>修改密码</span>
          </template>

          <el-form :model="passwordForm" label-width="100px" class="password-form" :rules="passwordRules" ref="passwordFormRef">
            <el-form-item label="当前密码" prop="oldPassword">
              <el-input v-model="passwordForm.oldPassword" type="password" show-password />
            </el-form-item>
            <el-form-item label="新密码" prop="newPassword">
              <el-input v-model="passwordForm.newPassword" type="password" show-password />
            </el-form-item>
            <el-form-item label="确认密码" prop="confirmPassword">
              <el-input v-model="passwordForm.confirmPassword" type="password" show-password />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="changePassword" :loading="changingPassword">修改密码</el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { Upload } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'
import { getCurrentUser, updateProfile, changePassword } from '@/api/auth'
import { getFavorites } from '@/api/favorites'
import type { FormInstance, FormRules } from 'element-plus'

const userStore = useUserStore()
const favoritesCount = ref(0)
const tradeCount = ref(0) // 暂时为0，后续可以从交易记录API获取
const saving = ref(false)
const changingPassword = ref(false)
const profileFormRef = ref<FormInstance>()
const passwordFormRef = ref<FormInstance>()
const avatarTimestamp = ref(Date.now()) // 用于强制刷新头像

// 默认头像
const defaultAvatar = 'https://cube.elemecdn.com/3/7c/3ea6beec64369c2642b92c6726f1epng.png'

// 头像URL（添加时间戳避免缓存）
const avatarUrl = computed(() => {
  if (!userStore.user) return defaultAvatar
  
  const avatar = userStore.user.avatar
  if (!avatar) return defaultAvatar
  
  // 如果是默认头像，直接返回
  if (avatar === defaultAvatar) return avatar
  
  // 处理头像URL
  let finalUrl = avatar
  
  // 如果是完整URL，直接使用
  if (avatar.startsWith('http')) {
    finalUrl = avatar
  } else if (avatar.startsWith('/files/')) {
    // 如果是 /files/ 开头，添加 /api 前缀
    finalUrl = '/api' + avatar
  } else if (avatar.startsWith('/api/')) {
    // 已经是 /api/ 开头，直接使用
    finalUrl = avatar
  }
  
  // 添加时间戳避免缓存
  const separator = finalUrl.includes('?') ? '&' : '?'
  const urlWithTimestamp = `${finalUrl}${separator}t=${avatarTimestamp.value}`
  
  console.log('头像URL:', avatar, '最终URL:', urlWithTimestamp)
  return urlWithTimestamp
})

// 上传请求头
const uploadHeaders = computed(() => ({
  Authorization: `Bearer ${userStore.token}`
}))

const profileForm = ref({
  username: '',
  email: '',
  phone: ''
})

const passwordForm = ref({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

// 表单验证规则
const profileRules: FormRules = {
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '请输入正确的邮箱格式', trigger: 'blur' }
  ],
  phone: [
    { pattern: /^1[3-9]\d{9}$/, message: '请输入正确的手机号', trigger: 'blur' }
  ]
}

const passwordRules: FormRules = {
  oldPassword: [
    { required: true, message: '请输入当前密码', trigger: 'blur' },
    { min: 6, message: '密码长度至少6位', trigger: 'blur' }
  ],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, message: '密码长度至少6位', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请确认新密码', trigger: 'blur' },
    {
      validator: (rule, value, callback) => {
        if (value !== passwordForm.value.newPassword) {
          callback(new Error('两次输入的密码不一致'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ]
}

// 获取用户信息
const fetchUserInfo = async () => {
  try {
    const res: any = await getCurrentUser()
    const userData = res.data
    profileForm.value = {
      username: userData.username,
      email: userData.email,
      phone: userData.phone || ''
    }
    // 同时更新 userStore，确保头像等信息正确
    userStore.setUser({
      id: userData.id,
      username: userData.username,
      email: userData.email,
      avatar: userData.avatar
    })
    console.log('获取用户信息成功:', userData)
  } catch (error) {
    console.error('获取用户信息失败:', error)
    ElMessage.error('获取用户信息失败')
  }
}

// 获取自选股数量
const fetchFavoritesCount = async () => {
  try {
    const res: any = await getFavorites()
    if (res.code === 200 && res.data) {
      favoritesCount.value = res.data.length
    }
  } catch (error) {
    console.error('获取自选股数量失败:', error)
  }
}

// 头像上传前的验证
const beforeAvatarUpload = (file: File) => {
  const isImage = file.type.startsWith('image/')
  const isLt2M = file.size / 1024 / 1024 < 2

  if (!isImage) {
    ElMessage.error('只能上传图片文件!')
    return false
  }
  if (!isLt2M) {
    ElMessage.error('头像图片大小不能超过 2MB!')
    return false
  }
  return true
}

// 头像上传成功
const handleAvatarSuccess = async (response: any) => {
  console.log('头像上传响应:', response)
  if (response.code === 200) {
    const newAvatarUrl = response.data
    console.log('新头像URL:', newAvatarUrl)
    
    // 先更新时间戳强制刷新头像显示
    avatarTimestamp.value = Date.now()
    
    // 更新本地用户信息 - 从localStorage获取或创建新对象
    const currentUser = userStore.user || JSON.parse(localStorage.getItem('user') || '{}')
    if (currentUser && currentUser.id) {
      const updatedUser = { ...currentUser, avatar: newAvatarUrl }
      userStore.setUser(updatedUser)
      console.log('更新后的用户信息:', updatedUser)
    } else {
      console.warn('无法更新用户信息：用户未登录')
    }
    
    // 更新服务器上的用户信息
    try {
      await updateProfile({ avatar: newAvatarUrl })
      // 再次刷新用户信息确保同步
      await fetchUserInfo()
      ElMessage.success('头像上传成功')
    } catch (error) {
      console.error('更新头像到用户信息失败:', error)
      ElMessage.warning('头像已上传但保存到用户信息失败')
    }
  } else {
    ElMessage.error(response.message || '头像上传失败')
  }
}

// 头像上传失败
const handleAvatarError = (error: any) => {
  console.error('头像上传失败:', error)
  ElMessage.error('头像上传失败')
}

// 保存个人信息
const saveProfile = async () => {
  if (!profileFormRef.value) return

  await profileFormRef.value.validate(async (valid) => {
    if (valid) {
      saving.value = true
      try {
        const res: any = await updateProfile({
          email: profileForm.value.email,
          phone: profileForm.value.phone
        })
        if (res.code === 200) {
          // 更新本地用户信息
          userStore.setUser({
            ...userStore.user!,
            email: profileForm.value.email,
            phone: profileForm.value.phone
          })
          ElMessage.success('个人信息保存成功')
        } else {
          ElMessage.error(res.message || '保存失败')
        }
      } catch (error: any) {
        console.error('保存个人信息失败:', error)
        ElMessage.error(error.response?.data?.message || '保存失败')
      } finally {
        saving.value = false
      }
    }
  })
}

// 修改密码
const changeUserPassword = async () => {
  if (!passwordFormRef.value) return

  await passwordFormRef.value.validate(async (valid) => {
    if (valid) {
      changingPassword.value = true
      try {
        const res: any = await changePassword({
          oldPassword: passwordForm.value.oldPassword,
          newPassword: passwordForm.value.newPassword
        })
        if (res.code === 200) {
          ElMessage.success('密码修改成功')
          // 清空密码表单
          passwordForm.value = {
            oldPassword: '',
            newPassword: '',
            confirmPassword: ''
          }
        } else {
          ElMessage.error(res.message || '密码修改失败')
        }
      } catch (error: any) {
        console.error('修改密码失败:', error)
        ElMessage.error(error.response?.data?.message || '修改密码失败')
      } finally {
        changingPassword.value = false
      }
    }
  })
}

onMounted(() => {
  fetchUserInfo()
  fetchFavoritesCount()
})
</script>

<style scoped>
.profile {
  max-width: 1000px;
  margin: 0 auto;
}

.user-card {
  text-align: center;
  padding: 20px;
}

.user-avatar {
  margin-bottom: 15px;
  position: relative;
  display: inline-block;
}

.avatar-uploader {
  margin-top: 10px;
}

.upload-btn {
  display: flex;
  align-items: center;
  gap: 5px;
}

.user-name {
  font-size: 20px;
  font-weight: bold;
  margin-bottom: 5px;
}

.user-email {
  color: #666;
  margin-bottom: 20px;
}

.user-stats {
  display: flex;
  justify-content: center;
  gap: 40px;
  padding-top: 20px;
  border-top: 1px solid #eee;
}

.stat-item {
  text-align: center;
}

.stat-value {
  font-size: 24px;
  font-weight: bold;
  color: #409EFF;
}

.stat-label {
  color: #666;
  font-size: 14px;
  margin-top: 5px;
}

.profile-form, .password-form {
  max-width: 500px;
}
</style>
