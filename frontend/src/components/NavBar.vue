<template>
  <!-- 顶部导航栏：B站风格，深色半透明背景，固定在页面顶部 -->
  <header class="navbar">
    <div class="nav-left">
      <!-- Logo：点击回首页 -->
      <div class="logo" @click="goHome">my13站</div>
      <!-- 真实可用的导航：每个入口都有对应页面（此前的"直播/番剧/游戏"等假频道已移除） -->
      <nav class="nav-links">
        <span class="nav-link" :class="{ active: isHome }" @click="goHome">首页</span>
        <!-- 投稿入口：上传视频（需登录，登录后自动回跳）；进入投稿页时粉色高亮 -->
        <span class="nav-link nav-upload" :class="{ active: isUpload }" @click="goUpload">投稿</span>
        <!-- 审核台入口：仅管理员可见，紧跟"投稿"之后；括号内为待审核视频数 -->
        <span v-if="userStore.user?.role === 1" class="nav-link" :class="{ active: isAudit }" @click="goAudit">
          审核台<template v-if="auditPending > 0">（{{ auditPending }}）</template>
        </span>
      </nav>
    </div>

    <div class="nav-right">
      <!-- 搜索框（带下拉浮层）：永远显示；点击/聚焦时下拉展示「搜索历史 + 全站热搜」 -->
      <div class="search-wrap">
        <div class="search-box">
          <input
            v-model="keyword"
            class="search-input"
            placeholder="搜索你感兴趣的视频"
            @focus="onSearchFocus"
            @blur="onSearchBlur"
            @keyup.enter="onSearch"
          />
          <button class="search-btn" @click="onSearch">
            <!-- 放大镜图标（内联 SVG，避免用 emoji） -->
            <svg viewBox="0 0 24 24" width="16" height="16" fill="currentColor">
              <path d="M15.5 14h-.79l-.28-.27a6.5 6.5 0 1 0-.7.7l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0A4.5 4.5 0 1 1 14 9.5 4.5 4.5 0 0 1 9.5 14z" />
            </svg>
          </button>
        </div>
        <!-- 下拉浮层：仅在聚焦时显示；下拉项用 @mousedown.prevent 避免 blur 抢先收起 -->
        <div v-if="searchDropdownOpen" class="search-dropdown">
          <div v-if="userStore.user && searchHistory.length > 0" class="sd-section">
            <div class="sd-title">
              <span>搜索历史</span>
              <span class="sd-clear" @mousedown.prevent @click="clearHistory">清空</span>
            </div>
            <div class="sd-chips">
              <span v-for="w in searchHistory" :key="w" class="sd-chip" @mousedown.prevent @click="pickWord(w)">{{ w }}</span>
            </div>
          </div>
          <div class="sd-section">
            <div class="sd-title">全站热搜 Top10</div>
            <ul class="sd-hot-list">
              <li v-for="(w, idx) in hotWords.slice(0, 10)" :key="w" @mousedown.prevent @click="pickWord(w)">
                <span class="sd-num" :class="{ top: idx < 3 }">{{ idx + 1 }}</span>
                <span class="sd-word">{{ w }}</span>
              </li>
              <li v-if="hotWords.length === 0" class="sd-empty">暂无热搜</li>
            </ul>
          </div>
        </div>
      </div>

      <!-- 私信信封:登录后显示,红点 = 私信未读总数(messageStore.dmUnread) -->
      <div v-if="userStore.user" class="bell" @click="goMessage">
        <!-- 信封图标(内联 SVG) -->
        <svg viewBox="0 0 24 24" width="21" height="21" fill="currentColor">
          <path d="M20 4H4a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2V6a2 2 0 0 0-2-2zm0 4-8 5-8-5V6l8 5 8-5v2z" />
        </svg>
        <span v-if="messageStore.dmUnread > 0" class="badge">{{ messageStore.dmUnread > 99 ? '99+' : messageStore.dmUnread }}</span>
      </div>

      <!-- 消息铃铛:登录后显示,未读数红点;未登录点击弹登录框 -->
      <div v-if="userStore.user" class="bell" @click="goNotification">
        <!-- 铃铛图标（内联 SVG） -->
        <svg viewBox="0 0 24 24" width="22" height="22" fill="currentColor">
          <path d="M12 22a2.98 2.98 0 0 0 2.82-2h-5.64A2.98 2.98 0 0 0 12 22zm7-6v-6a7 7 0 0 0-5-6.71V3a2 2 0 1 0-4 0v.29A7 7 0 0 0 5 10v6l-2 2v1h18v-1l-2-2z" />
        </svg>
        <span v-if="unread > 0" class="badge">{{ unread > 99 ? '99+' : unread }}</span>
      </div>

      <!-- 已登录：显示头像和昵称（点击进入个人中心） -->
      <div v-if="userStore.user" class="user-area">
        <img
          v-if="userStore.user.avatar"
          :src="userStore.user.avatar"
          class="avatar"
          alt="头像"
          title="个人中心"
          @click="goCenter"
        />
        <span v-else class="avatar avatar-text" title="个人中心" @click="goCenter">{{ firstChar }}</span>
        <span class="nickname" @click="goCenter">{{ userStore.user.nickname }}</span>
        <button class="logout-btn" @click="handleLogout">退出</button>
      </div>

      <!-- 未登录：显示登录按钮（重点） -->
      <button v-else class="login-btn" @click="openLogin">登录</button>
    </div>
  </header>
</template>

<script setup>
import { computed, ref, onMounted, onBeforeUnmount, watch } from 'vue'
import { useRouter } from 'vue-router'
import { userStore, clearAuth } from '../store/user'
import { messageStore } from '../store/message'
import { getUnreadCount } from '../api/notification'
import { getDmUnreadTotal } from '../api/dm'
import { getHotSearch, getSearchHistory, clearSearchHistory } from '../api/search'
import { getPendingVideos } from '../api/video'

// 频道导航在重构后已无假链接：剩余均为真实页面入口。
// （原"直播/番剧/游戏/推荐"等假频道已移除，避免点击无响应的误导交互）

const keyword = ref('')
const unread = ref(0) // 未读通知数（消息铃铛红点）
const router = useRouter()

/** 是否在首页：控制"首页"链接的高亮态 */
const isHome = computed(() => router.currentRoute.value.path === '/')

/** 是否在投稿页：控制"投稿"链接的高亮态 */
const isUpload = computed(() => router.currentRoute.value.path === '/upload')

/** 是否在审核台：控制"审核"链接的高亮态 */
const isAudit = computed(() => router.currentRoute.value.path === '/admin/audit')

/** 待审核视频数（审核入口右上角红点，仅管理员） */
const auditPending = ref(0)

// ===== 搜索下拉浮层 =====
const searchDropdownOpen = ref(false)
const hotWords = ref([])
const searchHistory = ref([])

/** 加载搜索建议：全站热搜 + 登录用户的搜索历史 */
async function loadSearchSuggest() {
  try {
    const r = await getHotSearch()
    hotWords.value = r.data || []
  } catch {
    hotWords.value = []
  }
  if (userStore.user) {
    try {
      const r = await getSearchHistory()
      searchHistory.value = r.data || []
    } catch {
      searchHistory.value = []
    }
  } else {
    searchHistory.value = []
  }
}

/** 搜索框聚焦：打开下拉并刷新热词/历史（热词是实时累加的，挂载时那份数据会过期） */
function onSearchFocus() {
  loadSearchSuggest()
  searchDropdownOpen.value = true
}

/** 搜索框失焦：延迟关闭，给下拉项的 click 留出时间 */
function onSearchBlur() {
  setTimeout(() => {
    searchDropdownOpen.value = false
  }, 200)
}

/** 点击下拉项（热词 / 历史）：填入关键词并触发搜索 */
function pickWord(w) {
  keyword.value = w
  searchDropdownOpen.value = false
  onSearch()
}

/** 清空搜索历史（仅当前用户的服务器端历史） */
async function clearHistory() {
  try {
    await clearSearchHistory()
    searchHistory.value = []
  } catch {
    /* 忽略 */
  }
}

// 登录状态变化时刷新历史（已登录会拉历史，未登录清空）
watch(() => userStore.user, loadSearchSuggest)
onMounted(loadSearchSuggest)

// 路由跳转时关闭下拉，避免在其它页面下拉仍浮着
watch(() => router.currentRoute.value.path, () => {
  searchDropdownOpen.value = false
})

/** 返回首页 */
function goHome() {
  router.push('/')
}

/** 进入个人中心（仅登录后可见入口，路由守卫兜底） */
function goCenter() {
  router.push('/user/center')
}

/** 跳转到投稿页（路由守卫会自动判断是否登录） */
function goUpload() {
  router.push('/upload')
}

/** 跳转到审核台（入口仅管理员可见，路由守卫还会二次校验角色） */
function goAudit() {
  router.push('/admin/audit')
}

/** 跳转到消息通知中心（未登录点击则弹登录框） */
function goNotification() {
  if (!userStore.user) {
    userStore.loginVisible = true
    return
  }
  router.push('/notification')
}

/** 跳转到私信消息中心(信封图标;登录后入口才显示,无需再弹登录框) */
function goMessage() {
  router.push('/message')
}

/** 拉取待审核视频数（仅管理员；size=1 只取 total，用于审核入口红点） */
async function loadAuditPending() {
  if (userStore.user?.role !== 1) {
    auditPending.value = 0
    return
  }
  try {
    const res = await getPendingVideos({ page: 1, size: 1, status: 0 })
    auditPending.value = res.data.total || 0
  } catch {
    auditPending.value = 0
  }
}

/** 拉取未读通知数（登录后显示红点） */
async function loadUnread() {
  if (!userStore.user) return
  try {
    const res = await getUnreadCount()
    unread.value = res.data || 0
  } catch {
    unread.value = 0
  }
}

// 登录状态变化时刷新未读数（登录/退出都触发）
watch(() => userStore.user, loadUnread)
watch(() => userStore.user, loadAuditPending)
onMounted(loadUnread)
onMounted(loadAuditPending)

/** 拉取私信未读总数(写入全局 messageStore,信封红点读它) */
async function loadDmUnread() {
  if (!userStore.user) {
    messageStore.dmUnread = 0
    return
  }
  try {
    const res = await getDmUnreadTotal()
    messageStore.dmUnread = res.data || 0
  } catch {
    // 拉取失败保留旧值,等下一轮轮询自愈
  }
}

watch(() => userStore.user, loadDmUnread)
onMounted(loadDmUnread)

// ===== 30s 轮询:两个红点(通知 + 私信)自动更新 =====
// 说明:不做全局常驻 WebSocket(连接管理复杂,练习项目从简);
// Message.vue 页面内的实时性由页面自己的 WS 连接负责,页外红点靠这里轮询兜底
let pollTimer = null
onMounted(() => {
  pollTimer = setInterval(() => {
    loadUnread()
    loadDmUnread()
    loadAuditPending()
  }, 30000)
})
onBeforeUnmount(() => {
  if (pollTimer) clearInterval(pollTimer)
})

// 昵称第一个字，用作默认头像文字
const firstChar = computed(() =>
  userStore.user?.nickname ? userStore.user.nickname.charAt(0) : '用'
)

/** 打开登录弹窗（全局状态控制） */
function openLogin() {
  userStore.loginVisible = true
}

/** 搜索：带关键词跳到 /search 出结果；空关键词跳到 /search 出热词/历史引导页 */
function onSearch() {
  const kw = keyword.value.trim()
  if (kw) {
    router.push(`/search?kw=${encodeURIComponent(kw)}`)
  } else {
    router.push('/search')
  }
  keyword.value = ''
}

/**
 * 退出登录：清登录态并统一跳回首页。
 * 必须无条件回首页的原因（安全考虑）：
 * 若停在退出前页面（如审核台 /admin/audit、充值页、通知中心），
 * 换另一个账号登录时仍停留在该受限页面，可能造成普通用户看到管理界面的"越权视图"；
 * 统一回首页后，再访问受限页会重新走路由守卫的登录 + 角色校验。
 */
function handleLogout() {
  clearAuth()
  unread.value = 0
  messageStore.dmUnread = 0
  auditPending.value = 0
  // 已在首页则无需重复导航
  if (router.currentRoute.value.path !== '/') {
    router.push('/')
  }
}
</script>

<style scoped>
/* 顶部栏整体：深色半透明 + 毛玻璃蒙版，吸顶 */
/* backdrop-filter 让导航栏透出下方内容的模糊虚影，形成"蒙版"效果 */
.navbar {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  height: 64px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  background: rgba(16, 16, 20, 0.6);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  color: #fff;
  z-index: 100;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.3);
}

.nav-left {
  display: flex;
  align-items: center;
  gap: 28px;
}

/* Logo：粉色文字 + 圆角方块，贴合 B站品牌色 */
.logo {
  font-size: 22px;
  font-weight: bold;
  color: #fb7299;
  background: rgba(251, 114, 153, 0.15);
  padding: 4px 12px;
  border-radius: 8px;
  cursor: pointer;
}

/* 频道导航链接 */
.nav-links {
  display: flex;
  gap: 20px;
}

.nav-link {
  font-size: 15px;
  color: #ccc;
  cursor: pointer;
  transition: color 0.2s;
}

.nav-link:hover {
  color: #fb7299;
}

.nav-link.active {
  color: #fb7299;
  font-weight: bold;
}

/* ===== 搜索下拉浮层（NavBar 搜索框聚焦时显示） ===== */
.search-wrap {
  position: relative;
}

.search-dropdown {
  position: absolute;
  top: 44px;
  right: 0;
  width: 380px;
  max-height: 60vh;
  overflow-y: auto;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.22);
  color: #333;
  z-index: 200; /* 与登录弹窗同层，覆盖页面内容 */
  padding: 6px 0 8px;
}

.sd-section {
  padding: 10px 14px;
  border-bottom: 1px solid #f0f0f3;
}
.sd-section:last-child {
  border-bottom: none;
}

.sd-title {
  font-size: 13px;
  color: #666;
  margin-bottom: 8px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.sd-clear {
  font-size: 12px;
  color: #aaa;
  cursor: pointer;
}
.sd-clear:hover {
  color: #fb7299;
}

.sd-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.sd-chip {
  padding: 4px 12px;
  background: #f5f5f7;
  border-radius: 14px;
  font-size: 12px;
  color: #555;
  cursor: pointer;
  user-select: none;
}
.sd-chip:hover {
  background: #fdeef3;
  color: #fb7299;
}

.sd-hot-list {
  list-style: none;
  margin: 0;
  padding: 0;
}
.sd-hot-list li {
  padding: 6px 14px;
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 13px;
  cursor: pointer;
  color: #333;
}
.sd-hot-list li:hover {
  background: #f5f5f7;
}
.sd-num {
  width: 20px;
  text-align: center;
  color: #999;
  font-weight: bold;
  font-size: 13px;
}
.sd-num.top {
  color: #e04343;
}
.sd-word {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.sd-empty {
  color: #ccc;
  padding: 6px 14px;
  font-size: 12px;
}

.nav-right {
  display: flex;
  align-items: center;
  gap: 16px;
}

/* 搜索框 */
.search-box {
  display: flex;
  align-items: center;
  background: #fff;
  border-radius: 20px;
  overflow: hidden;
  height: 36px;
}

.search-box input {
  border: none;
  outline: none;
  padding: 0 14px;
  width: 220px;
  font-size: 14px;
  background: transparent;
}

.search-btn {
  border: none;
  background: #fb7299;
  color: #fff;
  width: 44px;
  height: 36px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
}

.search-btn:hover {
  background: #f7598c;
}

/* 登录按钮：白底粉字圆角，醒目 */
.login-btn {
  background: #fff;
  color: #fb7299;
  border: none;
  border-radius: 20px;
  padding: 8px 28px;
  font-size: 15px;
  font-weight: bold;
  cursor: pointer;
  transition: transform 0.15s, box-shadow 0.15s;
}

.login-btn:hover {
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(251, 114, 153, 0.5);
}

/* 已登录区域 */
.user-area {
  display: flex;
  align-items: center;
  gap: 10px;
}

.avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  object-fit: cover;
  border: 2px solid #fb7299;
  cursor: pointer;
}

.avatar-text {
  display: flex;
  align-items: center;
  justify-content: center;
  background: #fb7299;
  color: #fff;
  font-weight: bold;
  cursor: pointer;
}

.nickname {
  font-size: 14px;
  color: #eee;
  cursor: pointer;
}

.nickname:hover {
  color: #fb7299;
}

.logout-btn {
  border: 1px solid #666;
  background: transparent;
  color: #ccc;
  border-radius: 14px;
  padding: 4px 14px;
  font-size: 13px;
  cursor: pointer;
}

.logout-btn:hover {
  color: #fb7299;
  border-color: #fb7299;
}

/* 消息铃铛：图标 + 未读红点 */
.bell {
  position: relative;
  color: #ddd;
  cursor: pointer;
  display: flex;
  align-items: center;
  padding: 6px;
  border-radius: 50%;
  transition: color 0.2s;
}

.bell:hover {
  color: #fb7299;
}

.badge {
  position: absolute;
  top: 0;
  right: 0;
  min-width: 16px;
  height: 16px;
  padding: 0 4px;
  border-radius: 8px;
  background: #fb7299;
  color: #fff;
  font-size: 10px;
  line-height: 16px;
  text-align: center;
  box-sizing: border-box;
}
</style>
