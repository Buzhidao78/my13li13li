import axios from 'axios'
import { getToken, clearAuth, userStore } from '../store/user'

/**
 * 统一封装的 axios 实例
 * 职责：
 * 1. 请求自动携带 token（Authorization: Bearer xxx）
 * 2. 响应解包后端 Result { code, message, data }
 * 3. 遇到 401（未登录/token 过期）自动清除登录态并弹出登录框
 */
const request = axios.create({
  baseURL: '/api',
  timeout: 10000
})

// 请求拦截器：每个请求自动带上 token
request.interceptors.request.use((config) => {
  const token = getToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// 响应拦截器：解包数据 + 统一处理错误
request.interceptors.response.use(
  (response) => {
    const res = response.data
    if (res.code === 200) {
      return res
    }
    // 业务错误：把后端提示 message 变成 Error，调用方用 try/catch 捕获
    return Promise.reject(new Error(res.message || '请求失败'))
  },
  (error) => {
    // HTTP 401：登录失效，清除登录态并弹出登录框
    if (error.response && error.response.status === 401) {
      clearAuth()
      userStore.loginVisible = true
    }
    return Promise.reject(error)
  }
)

export default request
