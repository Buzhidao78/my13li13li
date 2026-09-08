<template>
  <div class="center-page">
    <div class="center-card">
      <!-- 用户信息 -->
      <div class="user-info">
        <span class="avatar">{{ firstChar }}</span>
        <div>
          <div class="uname">{{ userStore.user?.nickname || '未登录' }}</div>
          <div class="uphone">{{ userStore.user?.phone }}</div>
        </div>
      </div>

      <!-- 三个 tab：我的视频 / 我的收藏 / 观看历史 -->
      <div class="tabs">
        <span
          v-for="t in tabs"
          :key="t.key"
          class="tab"
          :class="{ active: activeTab === t.key }"
          @click="switchTab(t.key)"
        >
          {{ t.label }}
        </span>
      </div>

      <!-- 列表区 -->
      <div v-if="list.length === 0" class="empty">{{ emptyText }}</div>
      <div v-else class="video-list">
        <div v-for="v in list" :key="v.id" class="video-row" @click="goDetail(v.id)">
          <!-- 缩略图：有封面显示，没有用渐变占位 -->
          <div class="thumb">
            <img v-if="v.coverUrl" :src="v.coverUrl" class="thumb-img" alt="" />
            <div v-else class="thumb-placeholder"></div>
          </div>
          <div class="row-main">
            <div class="row-title">{{ v.title }}</div>
            <div class="row-meta">
              <span v-if="activeTab === 'videos'" class="status-badge" :class="'status-' + v.status">
                {{ statusText(v.status) }}
              </span>
              <span v-if="v.interactTime">
                {{ activeTab === 'favorites' ? '收藏于' : '观看于' }} {{ formatTime(v.interactTime) }}
              </span>
              <span>{{ formatViews(v.playCount) }}播放</span>
            </div>
          </div>
        </div>
      </div>

      <!-- 分页 -->
      <div v-if="totalPages > 1" class="pagination">
        <button class="page-btn" :disabled="page <= 1" @click="changePage(page - 1)">上一页</button>
        <span class="page-info">第 {{ page }} / {{ totalPages }} 页</span>
        <button class="page-btn" :disabled="page >= totalPages" @click="changePage(page + 1)">下一页</button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { userStore } from '../store/user'
import { getMyVideos, getFavorites, getHistory } from '../api/video'

const router = useRouter()

const tabs = [
  { key: 'videos', label: '我的视频' },
  { key: 'favorites', label: '我的收藏' },
  { key: 'history', label: '观看历史' }
]

const activeTab = ref('videos')
const list = ref([])
const page = ref(1)
const total = ref(0)
const pageSize = 8

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize)))
const emptyText = computed(() => ({ videos: '还没有投稿，去首页点"投稿"上传吧', favorites: '还没有收藏，看到喜欢的视频点"收藏"', history: '还没有观看记录' }[activeTab.value]))

const firstChar = computed(() =>
  userStore.user?.nickname ? userStore.user.nickname.charAt(0) : '用'
)

/** 切换 tab：先清空列表（避免展示上一个 tab 的残留），再加载 */
function switchTab(key) {
  activeTab.value = key
  list.value = []
  load()
}

/** 加载当前 tab 的一页数据 */
async function load() {
  page.value = 1
  total.value = 0
  await loadPage()
}

async function loadPage() {
  try {
    const api = { videos: getMyVideos, favorites: getFavorites, history: getHistory }[activeTab.value]
    const res = await api({ page: page.value, size: pageSize })
    list.value = res.data.records
    total.value = res.data.total
  } catch {
    // 加载失败保留已有数据（翻页失败不把已展示内容清空；首次加载失败则是空列表，页面有空态兜底）
  }
}

/** 翻页 */
function changePage(p) {
  if (p < 1 || p > totalPages.value || p === page.value) return
  page.value = p
  loadPage()
}

/** 跳详情页 */
function goDetail(id) {
  router.push(`/video/detail/${id}`)
}

function statusText(status) {
  return { 0: '待审核', 1: '已发布', 2: '已驳回', 3: '已下架' }[status] || '未知'
}

function formatViews(count) {
  const n = Number(count) || 0
  return n >= 10000 ? (n / 10000).toFixed(1) + '万' : n
}

function formatTime(t) {
  if (!t) return ''
  return String(t).replace('T', ' ').slice(0, 16)
}

load()
</script>

<style scoped>
.center-page {
  min-height: 100vh;
  padding: 90px 16px 40px;
  box-sizing: border-box;
}

.center-card {
  max-width: 760px;
  margin: 0 auto;
  padding: 28px;
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 8px 30px rgba(0, 0, 0, 0.08);
}

.user-info {
  display: flex;
  align-items: center;
  gap: 16px;
  padding-bottom: 20px;
  border-bottom: 1px solid #f0f0f3;
}

.avatar {
  width: 56px;
  height: 56px;
  border-radius: 50%;
  background: #fb7299;
  color: #fff;
  font-size: 22px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: bold;
}

.uname {
  font-size: 18px;
  font-weight: bold;
  color: #333;
}

.uphone {
  font-size: 13px;
  color: #999;
  margin-top: 4px;
}

.tabs {
  display: flex;
  gap: 24px;
  padding: 16px 0 12px;
  border-bottom: 1px solid #f0f0f3;
  margin-bottom: 16px;
}

.tab {
  font-size: 15px;
  color: #666;
  cursor: pointer;
  padding-bottom: 10px;
  border-bottom: 2px solid transparent;
}

.tab:hover {
  color: #fb7299;
}

.tab.active {
  color: #fb7299;
  font-weight: bold;
  border-bottom-color: #fb7299;
}

.empty {
  padding: 40px 0;
  text-align: center;
  color: #bbb;
  font-size: 14px;
}

.video-row {
  display: flex;
  gap: 14px;
  padding: 10px 0;
  cursor: pointer;
  border-bottom: 1px solid #f7f7f9;
}

.video-row:hover .row-title {
  color: #fb7299;
}

.thumb {
  width: 160px;
  aspect-ratio: 16 / 9;
  border-radius: 6px;
  overflow: hidden;
  background: #1f1f23;
  flex-shrink: 0;
}

.thumb-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.thumb-placeholder {
  width: 100%;
  height: 100%;
  background: linear-gradient(135deg, #3a3a46, #1f1f23);
}

.row-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 8px;
  min-width: 0;
}

.row-title {
  font-size: 14px;
  color: #333;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.row-meta {
  display: flex;
  gap: 14px;
  font-size: 12px;
  color: #999;
}

.status-badge {
  padding: 1px 8px;
  border-radius: 8px;
}

.status-0 { background: #fff7e6; color: #d48806; }
.status-1 { background: #e8f7ee; color: #1a8a4a; }
.status-2 { background: #fdecec; color: #e34d59; }
.status-3 { background: #f0f0f3; color: #888; }

.pagination {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16px;
  margin-top: 20px;
}

.page-btn {
  padding: 6px 18px;
  border: 1px solid #e3e3e8;
  border-radius: 16px;
  background: #fff;
  color: #555;
  font-size: 13px;
  cursor: pointer;
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
