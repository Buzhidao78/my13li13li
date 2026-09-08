import request from './request'

// ============ 会员充值订单相关接口 ============

/** 创建订单（充值下单）：body = { level: 1|2|3 } */
export function createOrder(data) {
  return request.post('/order/create', data)
}

/** 模拟支付：simulateError=true 时消费者会故意报错（演示消息重回队列） */
export function payOrder(orderId, simulateError = false) {
  return request.post(`/order/pay/${orderId}`, null, { params: { simulateError } })
}

/** 查询订单详情（后端优先走 Redis 缓存） */
export function getOrder(orderId) {
  return request.get(`/order/${orderId}`)
}

/** 分页查询当前用户的订单列表：page 从 1 开始，size 每页条数 */
export function getMyOrders(page = 1, size = 5) {
  return request.get('/order/my', { params: { page, size } })
}

/** 查询当前用户最新的"待支付"订单（无则返回 null），用于页面顶部"继续支付"入口 */
export function getPendingOrder() {
  return request.get('/order/pending')
}

/** 查询可购买的会员等级列表 */
export function getMemberLevels() {
  return request.get('/member/levels')
}

/** 查询当前用户的会员信息 */
export function getMemberInfo() {
  return request.get('/member/info')
}
