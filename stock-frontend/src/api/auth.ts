import request from '@/utils/request'

export interface LoginData {
  username: string
  password: string
}

export interface RegisterData {
  username: string
  email: string
  password: string
  phone?: string
}

export interface AuthResponse {
  token: string
  type: string
  userId: number
  username: string
  email: string
  avatar?: string
}

export const login = (data: LoginData) => {
  return request.post('/auth/login', data)
}

export const register = (data: RegisterData) => {
  return request.post('/auth/register', data)
}

export const getCurrentUser = () => {
  return request.get('/auth/me')
}

export interface UpdateProfileData {
  email?: string
  phone?: string
  avatar?: string
}

export interface ChangePasswordData {
  oldPassword: string
  newPassword: string
}

export const updateProfile = (data: UpdateProfileData) => {
  return request.put('/auth/profile', data)
}

export const changePassword = (data: ChangePasswordData) => {
  return request.put('/auth/password', data)
}
