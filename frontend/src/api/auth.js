import request from './request'

/**
 * 认证相关接口
 */

// 用户注册
export function register(data) {
  return request.post('/auth/register', data)
}

// 账号密码登录
export function login(data) {
  return request.post('/auth/login', data)
}

// 手机号验证码登录
export function phoneLogin(data) {
  return request.post('/auth/login/phone', data)
}

// 发送短信验证码（练习环境验证码打印在后端控制台）
export function sendCode(phone) {
  return request.post('/auth/code', { phone })
}
