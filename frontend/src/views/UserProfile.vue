<template>
  <div class="profile-page">
    <div v-if="loading" class="center-tip">加载中…</div>
    <div v-else-if="!profile" class="center-tip">用户不存在</div>

    <div v-else class="profile-card">
      <!-- 头部：头像 + 昵称 + 签名 + 统计 + 关注按钮 -->
      <div class="header">
        <img v-if="profile.avatar" :src="profile.avatar" class="avatar" alt="头像" />
        <span v-else class="avatar avatar-text">{{ firstChar }}</span>
        <div class="info">
          <div class="nickname">
            {{ profile.nickname }}
            <!-- 互相关注标识:后端 mutualFollowed 字段(游客/非互关时为 false 不渲染) -->
            <span v-if="profile.mutualFollowed" class="mutual-tag">互相关注</span>
          </div>
          <div v-if="profile.sign" class="sign">{{ profile.sign }}</div>
          <div class="stats">
            <!-- 点击"视频"数字/文字 → 切换到视频 Tab（与粉丝/关注统计一致） -->
            <span @click="switchTab('videos')"><b>{{ profile.videoCount || 0 }}</b> 视频</span>
            <span @click="switchTab('followers')"><b>{{ profile.followerCount || 0 }}</b> 粉丝</span>
            <span @click="switchTab('following')"><b>{{ profile.followingCount || 0 }}</b> 关注</span>
          </div>
        </div>
        <!-- 动作按钮:关注 + 发私信(都不是自己的主页才显示) -->
        <div class="btn-area">
          <button v-if="canFollow" class="follow-btn" :class="{ followed }" @click="onFollow">
            {{ followed ? '已关注' : '+ 关注' }}
          </button>
          <button v-if="canFollow" class="dm-btn" @click="goMessage">发私信</button>
        </div>
      </div>

      <!-- Tab：视频 / 粉丝 / 关注 -->
      <div class="tabs">
        <span class="tab" :class="{ active: activeTab === 'videos' }" @click="switchTab('videos')">视频</span>
        <span class="tab" :class="{ active: activeTab === 'followers' }" @click="switchTab('followers')">粉丝</span>
        <span class="tab" :class="{ active: activeTab === 'following' }" @click="switchTab('following')">关注</span>
      </div>

      <!-- 视频 Tab：卡片网格 -->
      <div v-if="activeTab === 'videos'" class="video-grid">
        <VideoCard v-for="video in videos" :key="video.id" :video="video" @click="goDetail(video.id)" />
        <div v-if="videos.length === 0" class="empty">TA 还没有发布视频</div>
      </div>

      <!-- 粉丝/关注 Tab：用户列表（含互相关注标识） -->
      <div v-else class="user-list">
        <div v-if="userList.length === 0" class="empty">还没有人</div>
        <div v-for="u in userList" :key="u.id" class="user-item" @click="goProfile(u.id)">
          <img v-if="u.avatar" :src="u.avatar" class="u-avatar" alt="头像" />
          <span v-else class="u-avatar avatar-text">{{ (u.nickname || '用').charAt(0) }}</span>
          <span class="u-nickname">{{ u.nickname || '匿名用户' }}</span>
          <span v-if="u.mutual" class="mutual-tag">互相关注</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import VideoCard from '../components/VideoCard.vue'
import { getUserProfile, toggleFollow, getFollowers, getFollowing } from '../api/follow'
import { getUserVideos } from '../api/video'
import { userStore } from '../store/user'

const route = useRoute()
const router = useRouter()

const profile = ref(null)
const loading = ref(true)
const activeTab = ref('videos')
const videos = ref([])
const userList = ref([])
const followed = ref(false)

const userId = computed(() => Number(route.params.id))
/** 是否显示关注按钮：已登录 且 不是看自己的主页 */
const canFollow = computed(() => !!userStore.user && userStore.user.id !== userId.value)
const firstChar = computed(() => profile.value?.nickname ? profile.value.nickname.charAt(0) : '用')

/** 加载用户主页信息 + 关注状态 */
async function load() {
  loading.value = true
  try {
    const res = await getUserProfile(userId.value)
    profile.value = res.data
    followed.value = !!res.data.followed
    switchTab('videos')
  } catch {
    profile.value = null
  } finally {
    loading.value = false
  }
}

/** 切换 Tab 并加载对应数据 */
async function switchTab(tab) {
  activeTab.value = tab
  if (tab === 'videos') {
    videos.value = []
    try {
      const res = await getUserVideos(userId.value, { page: 1, size: 12 })
      videos.value = res.data.records
    } catch { videos.value = [] }
  } else {
    userList.value = []
    const api = tab === 'followers' ? getFollowers : getFollowing
    try {
      const res = await api(userId.value, { page: 1, size: 50 })
      userList.value = res.data.records
    } catch { userList.value = [] }
  }
}

/** 关注/取关（需登录，未登录弹登录框） */
async function onFollow() {
  if (!userStore.user) {
    userStore.loginVisible = true
    return
  }
  try {
    const res = await toggleFollow(userId.value)
    followed.value = res.data
    // 本地同步粉丝数（取消关注 -1，新关注 +1）
    profile.value.followerCount = Math.max(0, (profile.value.followerCount || 0) + (followed.value ? 1 : -1))
    // 关注关系变了,重拉主页刷新互关标识(对方是否也关注我)
    refreshMutual()
  } catch { /* 失败保持现状 */ }
}

/** 只刷新互关标识,不动其他展示数据 */
async function refreshMutual() {
  try {
    const res = await getUserProfile(userId.value)
    if (res.data) profile.value.mutualFollowed = !!res.data.mutualFollowed
  } catch { /* 忽略 */ }
}

/** 跳到私信消息中心,并直开与 TA 的聊天窗(未登录时路由守卫会先拦登录) */
function goMessage() {
  router.push(`/message?userId=${userId.value}`)
}

function goDetail(id) {
  router.push(`/video/detail/${id}`)
}

function goProfile(id) {
  // 点自己名字进个人中心，否则进他人主页
  if (userStore.user && userStore.user.id === id) {
    router.push('/user/center')
  } else {
    router.push(`/user/${id}`)
  }
}

// 路由参数变化（同一个组件复用时）重新加载
watch(userId, load)
onMounted(load)
</script>

<style scoped>
.profile-page {
  max-width: 1000px;
  margin: 0 auto;
  padding: 90px 24px 40px;
  min-height: 100vh;
}

.center-tip {
  padding: 80px 0;
  text-align: center;
  color: #999;
  font-size: 15px;
}

.profile-card {
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 8px 30px rgba(0, 0, 0, 0.06);
  padding: 28px;
}

/* 头部 */
.header {
  display: flex;
  align-items: center;
  gap: 18px;
}

.avatar {
  width: 72px;
  height: 72px;
  border-radius: 50%;
  object-fit: cover;
  border: 2px solid #fb7299;
}

.avatar-text {
  display: flex;
  align-items: center;
  justify-content: center;
  background: #fb7299;
  color: #fff;
  font-size: 26px;
  font-weight: bold;
}

.info {
  flex: 1;
}

.nickname {
  font-size: 20px;
  font-weight: bold;
  color: #333;
}

.sign {
  margin-top: 4px;
  font-size: 13px;
  color: #999;
}

.stats {
  margin-top: 10px;
  display: flex;
  gap: 24px;
  font-size: 13px;
  color: #888;
}

.stats b {
  color: #333;
}

.stats span {
  cursor: pointer;
}

.stats span:hover {
  color: #fb7299;
}

/* 关注按钮 */
.follow-btn {
  padding: 8px 26px;
  border: 1px solid #fb7299;
  border-radius: 20px;
  background: #fb7299;
  color: #fff;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.2s;
}

.follow-btn:hover {
  opacity: 0.85;
}

.follow-btn.followed {
  background: #fff;
  color: #999;
  border-color: #ddd;
}

/* 动作按钮区:关注 + 发私信并排 */
.btn-area {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}

/* 发私信按钮:描边蓝,与关注按钮(粉色实底)区分 */
.dm-btn {
  padding: 8px 22px;
  border: 1px solid #58b7ff;
  border-radius: 20px;
  background: #fff;
  color: #58b7ff;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.2s;
}

.dm-btn:hover {
  background: #58b7ff;
  color: #fff;
}

/* 互相关注标识 */
.mutual-tag {
  display: inline-block;
  margin-left: 8px;
  padding: 1px 8px;
  border-radius: 8px;
  background: #fff1f5;
  color: #fb7299;
  font-size: 11px;
  font-weight: normal;
  vertical-align: middle;
}

/* Tab */
.tabs {
  display: flex;
  gap: 24px;
  margin: 24px 0 18px;
  padding-bottom: 10px;
  border-bottom: 1px solid #f0f0f3;
}

.tab {
  font-size: 15px;
  color: #666;
  cursor: pointer;
}

.tab:hover,
.tab.active {
  color: #fb7299;
  font-weight: bold;
}

/* 视频网格 */
.video-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 20px;
}

@media (max-width: 768px) {
  .video-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

/* 用户列表 */
.user-list {
  display: flex;
  flex-direction: column;
}

.user-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 8px;
  border-bottom: 1px solid #f7f7f9;
  cursor: pointer;
}

.user-item:hover {
  background: #fafafc;
}

.u-avatar {
  width: 42px;
  height: 42px;
  border-radius: 50%;
  object-fit: cover;
  background: #fb7299;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
  font-weight: bold;
}

.u-nickname {
  font-size: 14px;
  color: #333;
}

.empty {
  padding: 60px 0;
  text-align: center;
  color: #bbb;
  font-size: 14px;
  grid-column: 1 / -1;
}
</style>
