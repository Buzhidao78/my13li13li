import request from './request'

/**
 * 私信（DM）相关接口封装：全部需要登录
 * 发送走 HTTP POST（保证事务/抖音规则校验），实时接收走 WebSocket（/ws）
 */

/** 我的会话列表（消息中心左侧），按最后活跃时间倒序 */
export function getConversations(params) {
  return request.get('/dm/conversations', { params })
}

/** 单会话消息历史（倒序分页，前端 reverse 后正序渲染） */
export function getMessages(peerId, params) {
  return request.get('/dm/messages', { params: { peerId, ...params } })
}

/** 发送私信，返回落库后的消息 VO（含 id，前端立即上屏） */
export function sendDm(data) {
  return request.post('/dm/send', data)
}

/** 标记某会话已读（进入聊天窗时调用） */
export function markRead(peerId) {
  return request.post(`/dm/read/${peerId}`)
}

/** 我的私信未读总数（导航栏红点） */
export function getDmUnreadTotal() {
  return request.get('/dm/unread-total')
}

/** 抖音式发送权限预判：能否给对方发私信（禁用输入框/提示用） */
export function getDmPermission(peerId) {
  return request.get('/dm/permission', { params: { peerId } })
}
