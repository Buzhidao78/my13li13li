<template>
  <div class="dm-page">
    <div class="dm-layout">
      <!-- ==================== 左侧:会话列表 ==================== -->
      <div class="conv-panel">
        <h2 class="panel-title">私信</h2>

        <div v-if="conversations.length === 0" class="conv-empty">
          还没有会话,去别人主页发一条私信吧
        </div>

        <div
          v-for="c in conversations"
          :key="c.peerId"
          class="conv-item"
          :class="{ active: activePeer && c.peerId === activePeer.peerId }"
          @click="openConversation(c)"
        >
          <!-- 头像:没有头像文件时用昵称首字兜底 -->
          <span class="conv-avatar">{{ firstChar(c.peerNickname) }}</span>
          <div class="conv-info">
            <div class="conv-top">
              <span class="conv-name">{{ c.peerNickname || `用户${c.peerId}` }}</span>
              <span class="conv-time">{{ shortTime(c.lastTime) }}</span>
            </div>
            <div class="conv-bottom">
              <!-- 文本插值防 XSS,绝不 v-html -->
              <span class="conv-last">{{ c.lastContent }}</span>
              <span v-if="c.unreadCount > 0" class="conv-badge">{{ c.unreadCount > 99 ? '99+' : c.unreadCount }}</span>
            </div>
          </div>
        </div>
      </div>

      <!-- ==================== 右侧:聊天窗口 ==================== -->
      <div class="chat-panel">
        <!-- 未选中会话 -->
        <div v-if="!activePeer" class="chat-empty">
          选择左侧会话开始聊天
        </div>

        <template v-else>
          <!-- 对方信息栏 -->
          <div class="chat-header">
            <span class="chat-peer">{{ activePeer.peerNickname || `用户${activePeer.peerId}` }}</span>
            <router-link :to="`/user/${activePeer.peerId}`" class="chat-home">主页 ›</router-link>
          </div>

          <!-- 消息气泡区 -->
          <div ref="msgBox" class="msg-box">
            <div v-if="hasMore" class="load-older">
              <button class="older-btn" :disabled="historyLoading" @click="loadOlder">
                {{ historyLoading ? '加载中…' : '查看更早的消息' }}
              </button>
            </div>

            <div
              v-for="m in messages"
              :key="m.id"
              class="msg-row"
              :class="{ mine: m.senderId === myId }"
            >
              <span class="msg-avatar">{{ firstChar(m.senderId === myId ? myNickname : activePeer.peerNickname) }}</span>
              <div class="msg-body">
                <p class="msg-text">{{ m.content }}</p>
                <p class="msg-time">{{ formatTime(m.createTime) }}</p>
              </div>
            </div>
          </div>

          <!-- 抖音式发送权限提示:非互关且对方未回复时禁用输入 -->
          <div v-if="!canSend" class="deny-bar">{{ denyReason || '对方回复你之前只能发送一条私信' }}</div>

          <!-- 输入区 -->
          <div class="input-bar" :class="{ disabled: !canSend }">
            <textarea
              v-model="input"
              class="input"
              rows="1"
              maxlength="500"
              :disabled="!canSend || sending"
              placeholder="发条私信吧~"
              @keydown.enter.exact.prevent="onSend"
            ></textarea>
            <button class="send-btn" :disabled="!canSend || sending || !input.trim()" @click="onSend">
              {{ sending ? '发送中' : '发送' }}
            </button>
          </div>
        </template>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, nextTick, watch } from 'vue'
import { useRoute } from 'vue-router'
import { getConversations, getMessages, sendDm, markRead, getDmPermission, getDmUnreadTotal } from '../api/dm'
import { getUserProfile } from '../api/follow'
import { userStore, getToken } from '../store/user'
import { messageStore } from '../store/message'

const route = useRoute()

// ==================== 页面状态 ====================
// 会话列表(左侧)
const conversations = ref([])
// 当前聊天的对方 { peerId, peerNickname, peerAvatar }
const activePeer = ref(null)
// 消息列表(正序渲染)
const messages = ref([])
// 双通道去重:HTTP 历史 + WS 推送都按 id 记到这里,防止同一消息渲染两次
const msgIds = new Set()
// 历史消息分页
const page = ref(1)
const total = ref(0)
const historyLoading = ref(false)
const hasMore = computed(() => messages.value.length < total.value)

// 输入与发送
const input = ref('')
const sending = ref(false)
// 抖音规则:能否给当前对方发送
const canSend = ref(true)
const denyReason = ref('')

// 消息区 DOM 引用(滚动到底用)
const msgBox = ref(null)

// 当前登录用户 id/昵称(气泡左右判定 + 头像字)
const myId = computed(() => userStore.user?.id)
const myNickname = computed(() => userStore.user?.nickname || '我')

// ==================== WebSocket ====================
let ws = null
let heartbeatTimer = null
let reconnectDelay = 1000        // 重连退避:1s 起,每次翻倍,封顶 30s
let wsClosedByMe = false        // 组件卸载主动断开时不重连

/** 建立 WS 连接(带指数退避重连 + 25s 心跳) */
function connectWs() {
  const token = getToken()
  if (!token) return
  // 走当前域名:开发环境由 Vite 代理 /ws → 8080,生产环境同源部署直连
  const proto = location.protocol === 'https:' ? 'wss' : 'ws'
  ws = new WebSocket(`${proto}://${location.host}/ws?token=${token}`)

  ws.onopen = () => {
    reconnectDelay = 1000 // 连上后重置退避
    startHeartbeat()
  }

  ws.onmessage = (e) => {
    // 心跳回包直接忽略
    if (e.data === 'PONG') return
    try {
      const msg = JSON.parse(e.data)
      if (msg.type === 'DM_NEW') onDmNew(msg.data)
    } catch { /* 非 JSON 帧忽略 */ }
  }

  ws.onclose = () => {
    stopHeartbeat()
    // 意外断开才重连(组件卸载/路由离开时 wsClosedByMe=true)
    if (!wsClosedByMe) {
      setTimeout(connectWs, reconnectDelay)
      reconnectDelay = Math.min(reconnectDelay * 2, 30000)
    }
  }

  ws.onerror = () => {
    // onerror 后必然 onclose,重连逻辑收敛在 onclose 里
  }
}

/** 25s 心跳:防止 Vite 代理/网关掐掉空闲连接 */
function startHeartbeat() {
  stopHeartbeat()
  heartbeatTimer = setInterval(() => {
    if (ws && ws.readyState === WebSocket.OPEN) ws.send('PING')
  }, 25000)
}

function stopHeartbeat() {
  if (heartbeatTimer) { clearInterval(heartbeatTimer); heartbeatTimer = null }
}

/**
 * 收到新私信推送(WS)
 * - 正在跟这个人聊:上屏 + 已读回执 + 本地未读不变
 * - 其他会话来的:会话列表未读 +1 + 全局红点 +1,并把会话提到列表顶部
 */
function onDmNew(vo) {
  // 列表里找到这条会话:没有则造一条(第一次私聊就收到回复的场景)
  let conv = conversations.value.find(c => c.peerId === vo.senderId)
  if (!conv) {
    conv = { peerId: vo.senderId, peerNickname: `用户${vo.senderId}`, peerAvatar: null, unreadCount: 0 }
    conversations.value.unshift(conv)
  }
  // 更新会话摘要并置顶
  conv.lastContent = vo.content
  conv.lastTime = vo.createTime

  if (activePeer.value && activePeer.value.peerId === vo.senderId) {
    appendMessage(vo)          // 上屏(带去重)
    markRead(vo.senderId)       // 正在看 → 直接已读
    conv.unreadCount = 0
    refreshPermission()        // 对方回复了 → 解锁我的输入框
  } else {
    conv.unreadCount = (conv.unreadCount || 0) + 1
    // 全局红点增量修正(NavBar 轮询的权威值会周期性对齐)
    messageStore.dmUnread++
  }
}

/** 消息上屏(带 msgIds 去重),并滚动到底部 */
function appendMessage(vo) {
  if (msgIds.has(vo.id)) return
  msgIds.add(vo.id)
  messages.value.push(vo)
  total.value++
  scrollToBottom()
}

// ==================== 数据加载 ====================

/** 拉会话列表;成功后顺带把全局未读总数对齐一次 */
async function loadConversations() {
  try {
    const res = await getConversations({ page: 1, size: 50 })
    conversations.value = res.data.records || []
  } catch { /* 静默:保留旧数据 */ }
}

/** 打开某个会话:加载历史 + 已读回执 + 权限预判 */
async function openConversation(c) {
  if (activePeer.value && activePeer.value.peerId === c.peerId) return
  activePeer.value = { peerId: c.peerId, peerNickname: c.peerNickname, peerAvatar: c.peerAvatar }
  messages.value = []
  msgIds.clear()
  page.value = 1
  total.value = 0
  await Promise.all([loadMessages(true), refreshPermission()])
  // 进来看过就清未读:本地 + 服务端 + 全局红点
  if (c.unreadCount > 0) {
    messageStore.dmUnread = Math.max(0, messageStore.dmUnread - c.unreadCount)
    c.unreadCount = 0
  }
  markRead(c.peerId).catch(() => {})
  scrollToBottom()
}

/**
 * 拉历史消息(接口返回 id 倒序,渲染前 reverse 成正序)
 * replace=true 首次加载;false 加载更早一页(插到顶部)
 */
async function loadMessages(replace) {
  historyLoading.value = true
  try {
    const res = await getMessages(activePeer.value.peerId, { page: page.value, size: 20 })
    const records = (res.data.records || []).slice().reverse()
    // 去重后插入(WS 先到、历史后到时可能重叠)
    const fresh = records.filter(m => !msgIds.has(m.id))
    fresh.forEach(m => msgIds.add(m.id))
    if (replace) {
      messages.value = fresh
    } else {
      messages.value.unshift(...fresh)
    }
    total.value = res.data.total
    page.value++
  } catch { /* 忽略 */ } finally {
    historyLoading.value = false
  }
}

/** 查看更早的消息 */
function loadOlder() {
  loadMessages(false)
}

/** 抖音规则权限预判:非互关且我已发一条未获回复 → 禁用输入 */
async function refreshPermission() {
  if (!activePeer.value) return
  try {
    const res = await getDmPermission(activePeer.value.peerId)
    canSend.value = res.data?.canSend !== false
    denyReason.value = res.data?.reason || ''
  } catch {
    canSend.value = true // 查询失败不拦截,发送接口会兜底校验
  }
}

/** 发送私信(走 HTTP 保证事务与规则校验,WS 只做接收) */
async function onSend() {
  const content = input.value.trim()
  if (!content || !canSend.value || sending.value) return
  sending.value = true
  try {
    const res = await sendDm({ receiverId: activePeer.value.peerId, content })
    input.value = ''
    appendMessage(res.data)
    // 更新左侧会话摘要并置顶
    const conv = conversations.value.find(c => c.peerId === activePeer.value.peerId)
    if (conv) {
      conv.lastContent = res.data.content
      conv.lastTime = res.data.createTime
      const idx = conversations.value.indexOf(conv)
      if (idx > 0) {
        conversations.value.splice(idx, 1)
        conversations.value.unshift(conv)
      }
    }
    // 非互关场景:我发出去后对方未回复 → 重新锁定输入框
    refreshPermission()
  } catch { /* 错误提示由 axios 拦截器统一弹出 */ } finally {
    sending.value = false
  }
}

// ==================== 工具函数 ====================

/** 头像兜底:取昵称首字 */
function firstChar(name) {
  return (name || '').charAt(0) || '?'
}

/** 完整时间:2026-09-11 21:18 */
function formatTime(t) {
  if (!t) return ''
  return String(t).replace('T', ' ').slice(5, 16)
}

/** 会话列表短时间:今天显示时:分,更早显示月-日 */
function shortTime(t) {
  if (!t) return ''
  const s = String(t).replace('T', ' ')
  const today = new Date().toISOString().slice(0, 10)
  return s.startsWith(today) ? s.slice(11, 16) : s.slice(5, 10)
}

/** 滚动到底部(等 DOM 更新完) */
function scrollToBottom() {
  nextTick(() => {
    if (msgBox.value) msgBox.value.scrollTop = msgBox.value.scrollHeight
  })
}

// ==================== 生命周期 ====================

onMounted(async () => {
  loadConversations()
  connectWs()
  // 全局红点对齐一次(从别的页面进来时可能带着旧值)
  getDmUnreadTotal().then(res => { messageStore.dmUnread = res.data || 0 }).catch(() => {})

  // 入口二:从用户主页"发私信"按钮跳进来 /message?userId=xx
  const userId = Number(route.query.userId)
  if (userId) {
    // 会话列表里已有这个会话 → 直接打开
    // 注意列表是异步的:等一下再找,找不到再走 profile 接口
    setTimeout(async () => {
      const conv = conversations.value.find(c => c.peerId === userId)
      if (conv) {
        openConversation(conv)
      } else {
        // 从没聊过:拉对方资料补昵称,造一个会话占位
        try {
          const res = await getUserProfile(userId)
          const peer = { peerId: userId, peerNickname: res.data?.nickname, peerAvatar: res.data?.avatar }
          conversations.value.unshift({ ...peer, lastContent: '', lastTime: '', unreadCount: 0 })
          openConversation(conversations.value[0])
        } catch { /* 拉不到就仍展示 id 兜底 */ }
      }
    }, 400)
  }
})

onBeforeUnmount(() => {
  wsClosedByMe = true
  stopHeartbeat()
  if (ws) { try { ws.close() } catch { /* 忽略 */ } }
})

// 组件复用场景:/message?userId=1 → /message?userId=2(同一路由不同 query)
watch(() => route.query.userId, (val) => {
  if (!val) return
  const conv = conversations.value.find(c => c.peerId === Number(val))
  if (conv) openConversation(conv)
})
</script>

<style scoped>
.dm-page {
  max-width: 1000px;
  margin: 0 auto;
  padding: 84px 24px 24px;
  min-height: 100vh;
  box-sizing: border-box;
}

.dm-layout {
  display: flex;
  gap: 16px;
  height: calc(100vh - 108px);
}

/* ==================== 左侧会话列表 ==================== */
.conv-panel {
  width: 300px;
  flex-shrink: 0;
  background: #fff;
  border-radius: 14px;
  box-shadow: 0 6px 24px rgba(0, 0, 0, 0.05);
  overflow-y: auto;
}

.panel-title {
  margin: 0;
  padding: 18px 20px 12px;
  font-size: 18px;
  color: #333;
}

.conv-empty {
  padding: 60px 20px;
  text-align: center;
  color: #bbb;
  font-size: 13px;
}

.conv-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 18px;
  cursor: pointer;
  transition: background 0.15s;
}

.conv-item:hover {
  background: #fafafc;
}

.conv-item.active {
  background: #fff1f5;
}

.conv-avatar {
  width: 44px;
  height: 44px;
  border-radius: 50%;
  background: #fb7299;
  color: #fff;
  font-size: 18px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.conv-info {
  flex: 1;
  min-width: 0;
}

.conv-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.conv-name {
  font-size: 14px;
  font-weight: bold;
  color: #333;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.conv-time {
  font-size: 11px;
  color: #bbb;
  flex-shrink: 0;
}

.conv-bottom {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 4px;
}

.conv-last {
  font-size: 12px;
  color: #999;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.conv-badge {
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border-radius: 9px;
  background: #fb7299;
  color: #fff;
  font-size: 11px;
  line-height: 18px;
  text-align: center;
  flex-shrink: 0;
}

/* ==================== 右侧聊天窗 ==================== */
.chat-panel {
  flex: 1;
  background: #fff;
  border-radius: 14px;
  box-shadow: 0 6px 24px rgba(0, 0, 0, 0.05);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.chat-empty {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #bbb;
  font-size: 14px;
}

.chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid #f5f5f7;
}

.chat-peer {
  font-size: 16px;
  font-weight: bold;
  color: #333;
}

.chat-home {
  font-size: 12px;
  color: #fb7299;
  text-decoration: none;
}

/* 消息气泡区 */
.msg-box {
  flex: 1;
  overflow-y: auto;
  padding: 18px 20px;
}

.load-older {
  text-align: center;
  margin-bottom: 12px;
}

.older-btn {
  padding: 5px 18px;
  border: 1px solid #ddd;
  border-radius: 14px;
  background: transparent;
  color: #999;
  font-size: 12px;
  cursor: pointer;
}

.msg-row {
  display: flex;
  gap: 10px;
  margin-bottom: 16px;
}

/* 我的消息靠右 */
.msg-row.mine {
  flex-direction: row-reverse;
}

.msg-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: #e8e8ed;
  color: #666;
  font-size: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.msg-row.mine .msg-avatar {
  background: #fb7299;
  color: #fff;
}

.msg-body {
  max-width: 65%;
}

.msg-text {
  margin: 0;
  padding: 10px 14px;
  border-radius: 12px;
  background: #f4f4f6;
  color: #333;
  font-size: 14px;
  line-height: 1.6;
  word-break: break-word;
  white-space: pre-wrap;
}

.msg-row.mine .msg-text {
  background: #fb7299;
  color: #fff;
}

.msg-time {
  margin: 4px 4px 0;
  font-size: 11px;
  color: #c3c3c9;
}

.msg-row.mine .msg-time {
  text-align: right;
}

/* 抖音规则提示条 */
.deny-bar {
  padding: 10px 20px;
  background: #fff7e6;
  color: #b8860b;
  font-size: 13px;
  text-align: center;
}

/* 输入区 */
.input-bar {
  display: flex;
  gap: 12px;
  padding: 14px 20px;
  border-top: 1px solid #f5f5f7;
  align-items: flex-end;
}

.input-bar.disabled {
  opacity: 0.5;
}

.input {
  flex: 1;
  resize: none;
  border: 1px solid #e5e5ea;
  border-radius: 10px;
  padding: 10px 14px;
  font-size: 14px;
  line-height: 1.5;
  font-family: inherit;
  outline: none;
  max-height: 110px;
}

.input:focus {
  border-color: #fb7299;
}

.send-btn {
  padding: 10px 26px;
  border: none;
  border-radius: 10px;
  background: #fb7299;
  color: #fff;
  font-size: 14px;
  cursor: pointer;
  flex-shrink: 0;
}

.send-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}
</style>
