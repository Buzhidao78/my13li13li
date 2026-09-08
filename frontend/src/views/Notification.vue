<template>
  <div class="notify-page">
    <div class="header">
      <h2 class="title">消息通知</h2>
      <button class="read-all-btn" :disabled="unread === 0" @click="onReadAll">
        {{ unread > 0 ? `全部已读（${unread}）` : '全部已读' }}
      </button>
    </div>

    <div v-if="list.length === 0" class="empty">还没有通知</div>

    <div v-else class="list">
      <div
        v-for="n in list"
        :key="n.id"
        class="item"
        :class="{ unread: n.isRead === 0 }"
        @click="onClick(n)"
      >
        <span class="dot" v-if="n.isRead === 0"></span>
        <div class="content">
          <p class="text">{{ n.content }}</p>
          <p class="meta">
            {{ typeText(n.type) }} · {{ formatTime(n.createTime) }}
            <span v-if="n.videoTitle" class="video-link">《{{ n.videoTitle }}》</span>
          </p>
        </div>
        <span v-if="n.videoId" class="go">查看 ›</span>
      </div>
    </div>

    <div class="load-more">
      <button v-if="hasMore" class="more-btn" :disabled="loading" @click="loadMore">
        {{ loading ? '加载中…' : '加载更多' }}
      </button>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getNotifications, getUnreadCount, readAllNotifications, readNotification } from '../api/notification'

const router = useRouter()

const list = ref([])
const unread = ref(0)
const page = ref(1)
const total = ref(0)
const loading = ref(false)

const hasMore = computed(() => list.value.length < total.value)

/** 拉通知列表（第一页时重置） */
async function loadPage(replace) {
  if (loading.value) return
  loading.value = true
  try {
    const res = await getNotifications({ page: page.value, size: 15 })
    list.value = replace ? res.data.records : [...list.value, ...res.data.records]
    total.value = res.data.total
    page.value++
  } catch {
    // 加载失败保留已有数据（首次进入即失败则维持空列表，页面展示空态/可手动重试）
  } finally {
    loading.value = false
  }
}

/** 刷新未读数 */
async function loadUnread() {
  try {
    const res = await getUnreadCount()
    unread.value = res.data || 0
  } catch { unread.value = 0 }
}

/** 点击通知：标记已读 + 有关联视频则跳转详情页 */
async function onClick(n) {
  if (n.isRead === 0) {
    n.isRead = 1
    unread.value = Math.max(0, unread.value - 1)
    try { await readNotification(n.id) } catch { /* 忽略 */ }
  }
  if (n.videoId) {
    router.push(`/video/detail/${n.videoId}`)
  }
}

/** 全部已读 */
async function onReadAll() {
  try {
    await readAllNotifications()
    unread.value = 0
    list.value.forEach(n => (n.isRead = 1))
  } catch { /* 忽略 */ }
}

function loadMore() {
  loadPage(false)
}

/** 通知类型文字 */
function typeText(type) {
  return { 1: '点赞', 2: '收藏', 3: '投币', 4: '评论', 5: '回复', 6: '关注' }[type] || '互动'
}

function formatTime(t) {
  if (!t) return ''
  return String(t).replace('T', ' ').slice(0, 16)
}

onMounted(() => {
  loadPage(true)
  loadUnread()
})
</script>

<style scoped>
.notify-page {
  max-width: 760px;
  margin: 0 auto;
  padding: 90px 24px 40px;
  min-height: 100vh;
}

.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 18px;
}

.title {
  margin: 0;
  font-size: 20px;
  color: #333;
}

.read-all-btn {
  padding: 6px 16px;
  border: 1px solid #fb7299;
  border-radius: 16px;
  background: transparent;
  color: #fb7299;
  font-size: 13px;
  cursor: pointer;
}

.read-all-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.empty {
  padding: 80px 0;
  text-align: center;
  color: #bbb;
  font-size: 14px;
}

.list {
  background: #fff;
  border-radius: 14px;
  box-shadow: 0 6px 24px rgba(0, 0, 0, 0.05);
  overflow: hidden;
}

.item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px 18px;
  border-bottom: 1px solid #f5f5f7;
  cursor: pointer;
  transition: background 0.15s;
}

.item:last-child {
  border-bottom: none;
}

.item:hover {
  background: #fafafc;
}

/* 未读：左侧红点 + 加粗文字 */
.item.unread .text {
  font-weight: bold;
  color: #333;
}

.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #fb7299;
  flex-shrink: 0;
}

.content {
  flex: 1;
}

.text {
  margin: 0;
  font-size: 14px;
  color: #555;
  line-height: 1.5;
}

.meta {
  margin: 4px 0 0;
  font-size: 12px;
  color: #bbb;
}

.video-link {
  color: #fb7299;
}

.go {
  font-size: 12px;
  color: #999;
  flex-shrink: 0;
}

.load-more {
  margin-top: 24px;
  text-align: center;
}

.more-btn {
  padding: 10px 40px;
  border: 1px solid #fb7299;
  border-radius: 20px;
  background: transparent;
  color: #fb7299;
  font-size: 14px;
  cursor: pointer;
}

.more-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
</style>
