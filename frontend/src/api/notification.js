import request from './request'

/**
 * 站内通知相关接口封装（全部需登录）
 */

/** 分页查询我的通知 */
export function getNotifications(params) {
  return request.get('/notification/list', { params })
}

/** 未读通知数（导航栏红点用） */
export function getUnreadCount() {
  return request.get('/notification/unread-count')
}

/** 全部标记已读 */
export function readAllNotifications() {
  return request.post('/notification/read-all')
}

/** 单条标记已读 */
export function readNotification(id) {
  return request.post(`/notification/read/${id}`)
}
