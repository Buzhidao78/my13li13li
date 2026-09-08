import { reactive } from 'vue'

/**
 * 全局登录态管理：token 和用户信息存在 localStorage（刷新不丢失）
 * 任何组件都能通过 userStore 读取当前登录状态
 */

const TOKEN_KEY = 'practice2_token'
const USER_KEY = 'practice2_user'

// 从 localStorage 读取缓存的用户信息
function readUser() {
  try {
    return JSON.parse(localStorage.getItem(USER_KEY)) || null
  } catch {
    return null
  }
}

// 全局响应式状态：user 变化时所有组件自动更新
export const userStore = reactive({
  // 当前登录用户（null 表示未登录）
  user: readUser(),
  // 是否显示登录弹窗（全局控制）
  loginVisible: false,
  // 登录成功后要回跳的地址（路由守卫里被拦截时记录，如 /upload、/recharge）
  redirectTo: null
})

/** 获取 token */
export function getToken() {
  return localStorage.getItem(TOKEN_KEY)
}

/** 保存 token */
export function setToken(token) {
  localStorage.setItem(TOKEN_KEY, token)
}

/** 保存用户信息并更新全局状态 */
export function setUser(user) {
  userStore.user = user
  localStorage.setItem(USER_KEY, JSON.stringify(user))
}

/** 清除登录态（退出登录 / token 过期时调用） */
export function clearAuth() {
  userStore.user = null
  userStore.loginVisible = false
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
}

/** 是否已登录 */
export function isLogin() {
  return !!getToken()
}
