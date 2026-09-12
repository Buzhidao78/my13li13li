<template>
  <!-- 会员充值页：B站风格，深色 + 粉色主题 -->
  <div class="recharge-page">
    <!-- 顶部会员状态横幅 -->
    <div class="member-banner">
      <div class="banner-left">
        <div class="banner-title">{{ memberInfo.levelName }}</div>
        <div class="banner-desc">
          <template v-if="memberInfo.isMember">
            有效期至 {{ formatTime(memberInfo.memberExpire) }} · 剩余 {{ memberInfo.remainDays }} 天
          </template>
          <template v-else>开通会员，尊享专属权益</template>
        </div>
      </div>
      <div class="banner-tag">会员中心</div>
    </div>

    <!-- 会员等级选择卡片 -->
    <div class="section-title">选择会员等级</div>
    <div class="level-cards">
      <div
        v-for="item in levels"
        :key="item.level"
        class="level-card"
        :class="{ active: selectedLevel === item.level }"
        @click="selectedLevel = item.level"
      >
        <div class="level-name">{{ item.name }}</div>
        <div class="level-price">
          <span class="currency">¥</span>{{ item.amount }}
        </div>
        <div class="level-days">{{ item.days }} 天</div>
        <div class="level-remark">{{ item.remark }}</div>
      </div>
    </div>

    <!-- 下单 / 模拟支付操作区 -->
    <div class="action-bar">
      <button class="primary-btn" :disabled="!selectedLevel" @click="handleCreate">
        {{ submitting ? '处理中...' : '下单' }}
      </button>
      <label class="demo-switch">
        <input type="checkbox" v-model="simulateError" />
        演示：消费者模拟报错（消息重回队列）
      </label>
    </div>

    <!-- 操作结果提示：展示后端业务消息（如"正在处理中，请勿重复提交"） -->
    <div v-if="message" class="msg-box" :class="messageType">{{ message }}</div>

    <!-- 当前待支付订单提示：页面刷新后依然能继续"模拟支付" -->
    <div v-if="currentOrder" class="order-tip">
      当前订单：<b>{{ currentOrder.orderId }}</b>（{{ currentOrder.statusText }}）
      <button class="pay-btn" :disabled="paying" @click="handlePay">
        {{ paying ? '支付中...' : '模拟支付' }}
      </button>
      <button class="refresh-btn" @click="handleRefresh">刷新状态</button>
    </div>

    <!-- 订单列表 -->
    <div class="section-title">我的订单</div>
    <div class="order-table">
      <table>
        <thead>
          <tr>
            <th>订单号</th>
            <th>等级</th>
            <th>金额</th>
            <th>天数</th>
            <th>状态</th>
            <th>创建时间</th>
            <th>支付/关闭时间</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="o in orders" :key="o.orderId">
            <td class="mono">{{ o.orderId }}</td>
            <td>{{ o.levelName }}</td>
            <td>¥{{ o.amount }}</td>
            <td>{{ o.days }} 天</td>
            <td><span class="status" :class="'s' + o.status">{{ o.statusText }}</span></td>
            <td>{{ formatTime(o.createTime) }}</td>
            <td>{{ formatTime(o.payTime || o.closeTime) }}</td>
          </tr>
          <tr v-if="!orders.length">
            <td colspan="7" class="empty">还没有订单，先选一个等级下单吧</td>
          </tr>
        </tbody>
      </table>
      <!-- 分页条：上一页 / 页码信息 / 下一页 -->
      <div v-if="total > 0" class="pagination">
        <button class="page-btn" :disabled="page <= 1" @click="changePage(page - 1)">上一页</button>
        <span class="page-info">第 {{ page }} / {{ totalPages }} 页，共 {{ total }} 条</span>
        <button class="page-btn" :disabled="page >= totalPages" @click="changePage(page + 1)">下一页</button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { createOrder, payOrder, getMyOrders, getPendingOrder, getMemberLevels, getMemberInfo } from '../api/order'

// 会员等级列表（后端返回）
const levels = ref([])
// 当前选中的等级
const selectedLevel = ref(null)
// 订单列表
const orders = ref([])
// 会员信息
const memberInfo = ref({ levelName: '普通会员', isMember: false })
// 是否提交中（下单按钮防重复点击）
const submitting = ref(false)
// 是否支付中（模拟支付按钮防重复点击）
const paying = ref(false)
// 操作结果提示消息（错误/成功）
const message = ref('')
// 消息类型：error / success，用于不同配色
const messageType = ref('error')
// 演示开关：支付时让消费者模拟报错
const simulateError = ref(false)
// 当前待支付订单（从独立的 /order/pending 接口查询，见 loadAll）
// 注意：不再从当前页订单列表推导 —— 因为订单已经分页，待支付订单可能不在当前页，
// 单独查询能保证页面顶部"继续支付"入口永远有效
const currentOrder = ref(null)

// 分页状态：当前页码、每页条数、总条数
const page = ref(1)
const pageSize = ref(5)
const total = ref(0)

// 总页数：至少 1 页（total 为 0 时显示第 1 页）
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize.value)))

/** 显示提示消息，3 秒后自动消失 */
function showMsg(text, type = 'error') {
  message.value = text
  messageType.value = type
  setTimeout(() => {
    message.value = ''
  }, 3000)
}

/** 加载页面数据：等级列表 + 会员信息 + 待支付订单 + 当前页订单列表 */
async function loadAll() {
  // 四个请求并行（Promise.all），一次刷新全部数据
  const [lv, info, pending, list] = await Promise.all([
    getMemberLevels(),
    getMemberInfo(),
    getPendingOrder(),
    getMyOrders(page.value, pageSize.value)
  ])
  levels.value = lv.data
  if (lv.data.length) selectedLevel.value = lv.data[0].level
  memberInfo.value = info.data
  // 待支付订单单独查：翻页时它不受影响，顶部"继续支付"入口始终有效
  currentOrder.value = pending.data
  // 分页结果：records 是当前页数据，total 是总条数（翻页按钮靠它算总页数）
  orders.value = list.data.records
  total.value = list.data.total
}

/** 翻页：切到指定页并重新加载 */
function changePage(p) {
  if (p < 1 || p > totalPages.value || p === page.value) return
  page.value = p
  loadAll()
}

/**
 * 下单：调后端创建订单（触发 Redis 分布式锁 + 缓存 + 延时关单消息）
 * 防重复：提交中再次点击，前端先拦截并提示；
 * 若请求真的并发到达后端，分布式锁会兜底返回"正在处理中，请勿重复提交"
 */
async function handleCreate() {
  // 提交中再次点击：直接提示，避免重复下单（后端锁是第二道保险）
  if (submitting.value) {
    showMsg('正在处理中，请勿重复提交')
    return
  }
  submitting.value = true
  message.value = ''
  try {
    const res = await createOrder({ level: selectedLevel.value })
    await loadAll()
    showMsg('下单成功：' + res.data.orderId, 'success')
  } catch (e) {
    // 展示后端业务提示（如"您有待支付的订单，请先完成支付"）
    showMsg(e.message || '下单失败')
  } finally {
    submitting.value = false
  }
}

/** 模拟支付：调后端支付接口（触发 MQ 会员升级消息） */
async function handlePay() {
  if (!currentOrder.value || paying.value) return
  paying.value = true
  message.value = ''
  try {
    await payOrder(currentOrder.value.orderId, simulateError.value)
    await loadAll()
    showMsg('支付成功，会员升级消息已发送，稍后刷新页面可看到会员等级变化', 'success')
  } catch (e) {
    showMsg(e.message || '支付失败')
  } finally {
    paying.value = false
  }
}

/** 刷新状态：重新拉取订单列表（观察延时关单：TTL 到期后状态自动变已过期） */
async function handleRefresh() {
  try {
    await loadAll()
    if (currentOrder.value) {
      showMsg('当前待支付订单：' + currentOrder.value.orderId)
    } else {
      showMsg('没有待支付订单', 'success')
    }
  } catch (e) {
    showMsg(e.message || '刷新失败')
  }
}

/** 时间格式化：去掉毫秒的 T */
function formatTime(t) {
  return t ? t.replace('T', ' ') : '—'
}

onMounted(loadAll)
</script>

<style scoped>
/* 页面整体：米白背景，居中窄栏 */
.recharge-page {
  max-width: 1080px;
  margin: 0 auto;
  padding: 88px 20px 40px;
}

/* 会员横幅 */
.member-banner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: linear-gradient(120deg, #fb7299, #fc8bab);
  border-radius: 12px;
  padding: 24px 28px;
  color: #fff;
  box-shadow: 0 6px 16px rgba(251, 114, 153, 0.35);
}

.banner-title {
  font-size: 26px;
  font-weight: bold;
}

.banner-desc {
  margin-top: 6px;
  font-size: 14px;
  opacity: 0.92;
}

.banner-tag {
  background: rgba(255, 255, 255, 0.22);
  padding: 6px 16px;
  border-radius: 20px;
  font-size: 13px;
}

/* 区块标题 */
.section-title {
  margin: 28px 0 14px;
  font-size: 18px;
  font-weight: bold;
  color: #333;
  border-left: 4px solid #fb7299;
  padding-left: 10px;
}

/* 等级卡片 */
.level-cards {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
}

.level-card {
  background: #fff;
  border: 2px solid #eee;
  border-radius: 12px;
  padding: 22px 18px;
  text-align: center;
  cursor: pointer;
  transition: all 0.2s;
}

.level-card:hover {
  transform: translateY(-3px);
  box-shadow: 0 8px 20px rgba(0, 0, 0, 0.08);
}

/* 选中态：粉色边框 + 浅粉背景 */
.level-card.active {
  border-color: #fb7299;
  background: #fff5f8;
  box-shadow: 0 8px 20px rgba(251, 114, 153, 0.25);
}

.level-name {
  font-size: 18px;
  font-weight: bold;
  color: #333;
}

.level-price {
  margin: 14px 0 6px;
  font-size: 30px;
  font-weight: bold;
  color: #fb7299;
}

.level-price .currency {
  font-size: 16px;
}

.level-days {
  color: #999;
  font-size: 14px;
}

.level-remark {
  margin-top: 10px;
  font-size: 12px;
  color: #aaa;
}

/* 操作区 */
.action-bar {
  display: flex;
  align-items: center;
  gap: 20px;
  margin-top: 20px;
}

.primary-btn {
  background: #fb7299;
  color: #fff;
  border: none;
  border-radius: 8px;
  padding: 12px 40px;
  font-size: 16px;
  font-weight: bold;
  cursor: pointer;
  transition: all 0.2s;
}

.primary-btn:hover:not(:disabled) {
  background: #f7598c;
  box-shadow: 0 6px 16px rgba(251, 114, 153, 0.4);
}

.primary-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

/* 演示开关 */
.demo-switch {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: #888;
  cursor: pointer;
}

/* 操作结果提示框 */
.msg-box {
  margin-top: 16px;
  padding: 10px 16px;
  border-radius: 8px;
  font-size: 14px;
}

.msg-box.error {
  background: #fff1f0;
  color: #ff4d4f;
  border: 1px solid #ffccc7;
}

.msg-box.success {
  background: #f6ffed;
  color: #52c41a;
  border: 1px solid #b7eb8f;
}

/* 当前订单提示 */
.order-tip {
  margin-top: 18px;
  padding: 14px 18px;
  background: #fff;
  border-radius: 8px;
  border: 1px dashed #fb7299;
  font-size: 14px;
  color: #555;
}

.pay-btn {
  margin-left: 12px;
  background: #fb7299;
  color: #fff;
  border: none;
  border-radius: 6px;
  padding: 6px 18px;
  font-size: 13px;
  cursor: pointer;
}

.refresh-btn {
  margin-left: 8px;
  background: #fff;
  color: #fb7299;
  border: 1px solid #fb7299;
  border-radius: 6px;
  padding: 5px 16px;
  font-size: 13px;
  cursor: pointer;
}

/* 订单表格 */
.order-table {
  background: #fff;
  border-radius: 10px;
  overflow: hidden;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.05);
}

.order-table table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}

.order-table th {
  background: #fafafa;
  color: #666;
  text-align: left;
  padding: 12px 14px;
  font-weight: normal;
}

.order-table td {
  padding: 12px 14px;
  border-top: 1px solid #f5f5f5;
  color: #333;
}

.mono {
  font-family: Consolas, monospace;
}

/* 状态徽标：不同颜色区分 */
.status {
  display: inline-block;
  padding: 2px 10px;
  border-radius: 12px;
  font-size: 12px;
}

.status.s0 {
  background: #fff7e6;
  color: #d48806;
}

.status.s1 {
  background: #f6ffed;
  color: #52c41a;
}

.status.s2 {
  background: #f5f5f5;
  color: #999;
}

.status.s3 {
  background: #fff1f0;
  color: #ff4d4f;
}

.empty {
  text-align: center;
  color: #aaa;
  padding: 30px 0 !important;
}

/* 分页条 */
.pagination {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16px;
  padding: 14px 0;
}

.page-btn {
  background: #fff;
  color: #fb7299;
  border: 1px solid #fb7299;
  border-radius: 6px;
  padding: 6px 18px;
  font-size: 13px;
  cursor: pointer;
}

.page-btn:hover:not(:disabled) {
  background: #fb7299;
  color: #fff;
}

.page-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.page-info {
  font-size: 13px;
  color: #666;
}
</style>
