import request from './request'

/**
 * 用户相关接口
 */

// 测试接口：验证前后端联调
export function getHello() {
  return request.get('/user/hello')
}

// 获取当前登录用户信息（需要 token）
export function getMe() {
  return request.get('/user/me')
}

// 获取用户列表（需要 token）
export function getUserList() {
  return request.get('/user/list')
}

// 新增用户（需要 token）
export function addUser(data) {
  return request.post('/user', data)
}
