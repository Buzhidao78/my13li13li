<template>
  <div class="detail-page">
    <div v-if="loading" class="center-tip">加载中…</div>
    <div v-else-if="!video" class="center-tip">视频不存在或未发布</div>

    <div v-else class="detail-card">
      <!-- 视频播放器：原生 <video> 直接播后端返回的文件路径 -->
      <video class="player" :src="video.videoUrl" controls preload="metadata" />

      <div class="info">
        <h1 class="title">{{ video.title }}</h1>
        <div class="meta">
          <span class="tag">{{ categoryText(video.category) }}</span>
          <span class="tag status-tag" :class="'status-' + video.status">{{ statusText(video.status) }}</span>
          <span class="play-count">播放 {{ video.playCount }} 次</span>
        </div>

        <div class="author">
          <span class="avatar" @click="goProfile">{{ firstChar }}</span>
          <span class="nickname" @click="goProfile">{{ video.authorNickname || '匿名用户' }}</span>
          <!-- 关注按钮：登录且不是看自己的视频才显示 -->
          <button v-if="canFollow" class="follow-btn" :class="{ followed }" @click="onFollow">
            {{ followed ? '已关注' : '+ 关注' }}
          </button>
        </div>

        <!-- ===== 互动区：点赞 / 收藏 / 投币 ===== -->
        <div class="actions">
          <button class="action-btn" :class="{ active: liked }" @click="onLike">
            👍 点赞 {{ video.likeCount || 0 }}
          </button>
          <button class="action-btn" :class="{ active: favorited }" @click="onFavorite">
            ⭐ 收藏 {{ video.favoriteCount || 0 }}
          </button>
          <button class="action-btn" @click="onCoin">
            🪙 投币 {{ video.coinCount || 0 }}
          </button>
          <span v-if="coinLeft !== null" class="coin-left">今日剩余可投币 {{ coinLeft }} 枚</span>
        </div>
        <p v-if="errorMsg" class="error-tip">{{ errorMsg }}</p>

        <p v-if="video.description" class="desc">{{ video.description }}</p>
        <div class="time">发布于 {{ formatTime(video.createTime) }}</div>

        <!-- ===== 评论区 ===== -->
        <div class="comments">
          <h2 class="comments-title">评论（{{ video.commentCount || 0 }}）</h2>
          <!-- 发表评论 -->
          <div class="comment-input">
            <textarea v-model="commentText" rows="2" class="comment-textarea" placeholder="发一条友善的评论" maxlength="500"></textarea>
            <button class="comment-submit" :disabled="submitting" @click="submitComment">
              {{ submitting ? '发布中…' : '发布' }}
            </button>
          </div>

          <!-- 评论列表：一级评论 + 楼中楼回复 -->
          <div v-if="comments.length === 0" class="no-comment">还没有评论，来抢沙发</div>
          <div v-for="c in comments" :key="c.id" class="comment-item">
            <div class="comment-main">
              <span class="c-nickname">{{ c.nickname }}</span>
              <span class="c-content">{{ c.content }}</span>
              <div class="c-meta">
                <span>{{ formatTime(c.createTime) }}</span>
                <span class="c-reply" @click="startReply(c)">回复</span>
              </div>
              <!-- 楼中楼回复 -->
              <div v-if="c.replies && c.replies.length" class="replies">
                <div v-for="r in c.replies" :key="r.id" class="reply-item">
                  <span class="r-nickname">{{ r.nickname }}</span>
                  <span class="r-content">{{ r.content }}</span>
                </div>
              </div>
              <!-- 正在回复的输入框 -->
              <div v-if="replyingTo === c.id" class="reply-input">
                <input v-model="replyText" class="reply-field" placeholder="回复 {{ c.nickname }}" maxlength="500" @keyup.enter="submitReply(c)" />
                <button class="comment-submit small" @click="submitReply(c)">回复</button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  getVideoDetail, playVideo, toggleLike, toggleFavorite,
  coinVideo, getComments, addComment
} from '../api/video'
import { toggleFollow, getFollowStatus } from '../api/follow'
import { userStore } from '../store/user'

const route = useRoute()
const router = useRouter()
const video = ref(null)
const loading = ref(true)

// ===== 互动状态 =====
const liked = ref(false)          // 当前是否已点赞
const favorited = ref(false)      // 当前是否已收藏（详情接口暂不返回，点过才变 true）
const coinLeft = ref(null)        // 今日剩余可投币数
const errorMsg = ref('')

// ===== 关注状态 =====
const followed = ref(false)       // 当前是否已关注作者
/** 是否显示关注按钮：已登录 且 不是看自己的视频 */
const canFollow = computed(() => !!userStore.user && userStore.user.id !== video.value?.userId)

// ===== 评论状态 =====
const comments = ref([])
const commentText = ref('')
const submitting = ref(false)
const replyingTo = ref(null)      // 正在回复哪条一级评论
const replyText = ref('')

// 页面加载：查详情 + 播放计数 + 拉评论
async function load() {
  loading.value = true
  try {
    const res = await getVideoDetail(route.params.id)
    video.value = res.data
    liked.value = !!res.data.liked
    // 查询关注状态（登录且不是自己的视频才查）
    if (canFollow.value) {
      loadFollowStatus()
    }
    // 播放计数（登录用户同时记录观看历史），失败不影响页面
    try {
      await playVideo(route.params.id)
      video.value.playCount = (video.value.playCount || 0) + 1
    } catch {
      // 忽略计数失败
    }
    loadComments()
  } catch {
    video.value = null
  } finally {
    loading.value = false
  }
}
load()

// 从"相关推荐/下一个"跳转到另一个视频时，路由只变 id、组件会复用：
// 监听 id 变化重置状态并重新加载，避免停留在上一个视频的数据上
watch(
  () => route.params.id,
  () => {
    video.value = null
    liked.value = false
    favorited.value = false
    followed.value = false
    coinLeft.value = null
    errorMsg.value = ''
    comments.value = []
    commentText.value = ''
    replyingTo.value = null
    load()
  }
)

/** 点赞/取消点赞 */
async function onLike() {
  if (!userStore.user) {
    userStore.loginVisible = true
    return
  }
  try {
    const res = await toggleLike(video.value.id)
    liked.value = res.data
    video.value.likeCount += liked.value ? 1 : -1
  } catch (e) {
    errorMsg.value = e.message || '操作失败'
  }
}

/** 查询是否已关注作者 */
async function loadFollowStatus() {
  try {
    const res = await getFollowStatus(video.value.userId)
    followed.value = !!res.data
  } catch {
    followed.value = false
  }
}

/** 关注/取关作者（未登录弹登录框） */
async function onFollow() {
  if (!userStore.user) {
    userStore.loginVisible = true
    return
  }
  try {
    const res = await toggleFollow(video.value.userId)
    followed.value = res.data
  } catch (e) {
    errorMsg.value = e.message || '操作失败'
  }
}

/** 进入作者主页（自己进个人中心，别人进 TA 的主页） */
function goProfile() {
  if (userStore.user && userStore.user.id === video.value.userId) {
    router.push('/user/center')
  } else {
    router.push(`/user/${video.value.userId}`)
  }
}

/** 收藏/取消收藏 */
async function onFavorite() {
  if (!userStore.user) {
    userStore.loginVisible = true
    return
  }
  try {
    const res = await toggleFavorite(video.value.id)
    favorited.value = res.data
    video.value.favoriteCount += favorited.value ? 1 : -1
  } catch (e) {
    errorMsg.value = e.message || '操作失败'
  }
}

/** 投币 */
async function onCoin() {
  if (!userStore.user) {
    userStore.loginVisible = true
    return
  }
  try {
    const res = await coinVideo(video.value.id, 1)
    coinLeft.value = res.data
    video.value.coinCount += 1
    errorMsg.value = ''
  } catch (e) {
    errorMsg.value = e.message || '操作失败'
  }
}

/** 拉取评论第一页 */
async function loadComments() {
  try {
    const res = await getComments(route.params.id, { page: 1, size: 10 })
    comments.value = res.data.records
  } catch {
    comments.value = []
  }
}

/** 发表一级评论 */
async function submitComment() {
  const content = commentText.value.trim()
  if (!content) return
  if (!userStore.user) {
    userStore.loginVisible = true
    return
  }
  submitting.value = true
  try {
    await addComment(video.value.id, { content, parentId: 0 })
    commentText.value = ''
    video.value.commentCount = (video.value.commentCount || 0) + 1
    loadComments()
  } catch (e) {
    errorMsg.value = e.message || '评论失败'
  } finally {
    submitting.value = false
  }
}

/** 打开回复输入框 */
function startReply(c) {
  replyingTo.value = replyingTo.value === c.id ? null : c.id
  replyText.value = ''
}

/** 发表楼中楼回复 */
async function submitReply(c) {
  const content = replyText.value.trim()
  if (!content) return
  if (!userStore.user) {
    userStore.loginVisible = true
    return
  }
  try {
    await addComment(video.value.id, { content, parentId: c.id })
    replyingTo.value = null
    replyText.value = ''
    loadComments()
  } catch (e) {
    errorMsg.value = e.message || '回复失败'
  }
}

// ===== 展示辅助 =====
const firstChar = computed(() =>
  video.value?.authorNickname ? video.value.authorNickname.charAt(0) : '匿'
)

function categoryText(c) {
  return { 0: '默认', 1: '生活', 2: '游戏', 3: '科技', 4: '美食' }[c] || '默认'
}

function statusText(status) {
  return { 0: '待审核', 1: '已发布', 2: '已驳回', 3: '已下架' }[status] || '未知'
}

function formatTime(t) {
  if (!t) return ''
  return String(t).replace('T', ' ').slice(0, 16)
}
</script>

<style scoped>
.detail-page {
  min-height: 100vh;
  padding: 90px 16px 40px;
  box-sizing: border-box;
}

.center-tip {
  padding: 80px 0;
  text-align: center;
  color: #999;
  font-size: 15px;
}

.detail-card {
  max-width: 860px;
  margin: 0 auto;
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 8px 30px rgba(0, 0, 0, 0.08);
  overflow: hidden;
}

.player {
  width: 100%;
  aspect-ratio: 16 / 9;
  background: #000;
  display: block;
}

.info {
  padding: 20px 28px 28px;
}

.title {
  margin: 0 0 12px;
  font-size: 22px;
  color: #333;
}

.meta {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 16px;
}

.tag {
  padding: 2px 10px;
  border-radius: 10px;
  background: #f0f0f3;
  color: #666;
  font-size: 12px;
}

.status-tag.status-0 { background: #fff7e6; color: #d48806; }
.status-tag.status-1 { background: #e8f7ee; color: #1a8a4a; }
.status-tag.status-2 { background: #fdecec; color: #e34d59; }
.status-tag.status-3 { background: #f0f0f3; color: #888; }

.play-count {
  margin-left: auto;
  font-size: 13px;
  color: #999;
}

.author {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 0;
  border-top: 1px solid #f0f0f3;
  border-bottom: 1px solid #f0f0f3;
  margin-bottom: 14px;
}

.avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: #fb7299;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: bold;
}

.nickname {
  font-size: 14px;
  color: #333;
  cursor: pointer;
}

.nickname:hover {
  color: #fb7299;
}

/* 关注按钮 */
.follow-btn {
  margin-left: auto;
  padding: 6px 20px;
  border: 1px solid #fb7299;
  border-radius: 18px;
  background: #fb7299;
  color: #fff;
  font-size: 13px;
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

/* 互动区 */
.actions {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 6px;
}

.action-btn {
  padding: 8px 18px;
  border: 1px solid #e3e3e8;
  border-radius: 20px;
  background: #fff;
  color: #555;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.2s;
}

.action-btn:hover {
  border-color: #fb7299;
  color: #fb7299;
}

.action-btn.active {
  background: #fb7299;
  border-color: #fb7299;
  color: #fff;
}

.coin-left {
  font-size: 12px;
  color: #999;
}

.error-tip {
  color: #e34d59;
  font-size: 13px;
  margin: 6px 0 0;
}

.desc {
  font-size: 14px;
  color: #555;
  line-height: 1.8;
  margin: 14px 0 6px;
  white-space: pre-wrap;
}

.time {
  font-size: 12px;
  color: #bbb;
}

/* 评论区 */
.comments {
  margin-top: 24px;
  border-top: 1px solid #f0f0f3;
  padding-top: 16px;
}

.comments-title {
  font-size: 16px;
  margin: 0 0 14px;
}

.comment-input {
  display: flex;
  gap: 10px;
  margin-bottom: 20px;
}

.comment-textarea {
  flex: 1;
  border: 1px solid #e3e3e8;
  border-radius: 10px;
  padding: 10px;
  font-size: 14px;
  resize: vertical;
  font-family: inherit;
  outline: none;
}

.comment-textarea:focus {
  border-color: #fb7299;
}

.comment-submit {
  align-self: flex-end;
  padding: 8px 22px;
  border: none;
  border-radius: 18px;
  background: #fb7299;
  color: #fff;
  font-size: 14px;
  cursor: pointer;
}

.comment-submit:disabled {
  opacity: 0.5;
}

.no-comment {
  padding: 20px 0;
  text-align: center;
  color: #bbb;
  font-size: 13px;
}

.comment-item {
  padding: 12px 0;
  border-bottom: 1px solid #f7f7f9;
}

.c-nickname {
  font-size: 13px;
  color: #fb7299;
  margin-right: 8px;
}

.c-content {
  font-size: 14px;
  color: #333;
}

.c-meta {
  margin-top: 4px;
  font-size: 12px;
  color: #bbb;
  display: flex;
  gap: 14px;
}

.c-reply {
  cursor: pointer;
}

.c-reply:hover {
  color: #fb7299;
}

/* 楼中楼回复 */
.replies {
  margin-top: 8px;
  padding: 8px 12px;
  background: #f7f7f9;
  border-radius: 8px;
}

.reply-item {
  padding: 4px 0;
  font-size: 13px;
}

.r-nickname {
  color: #fb7299;
  margin-right: 8px;
}

.r-content {
  color: #555;
}

/* 回复输入框 */
.reply-input {
  margin-top: 8px;
  display: flex;
  gap: 8px;
}

.reply-field {
  flex: 1;
  border: 1px solid #e3e3e8;
  border-radius: 16px;
  padding: 6px 12px;
  font-size: 13px;
  outline: none;
}

.comment-submit.small {
  padding: 6px 16px;
  font-size: 13px;
}
</style>
