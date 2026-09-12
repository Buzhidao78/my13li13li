<template>
  <div class="home">
    <!-- 分类栏：关注 + 全部 + 各分类（关注 Tab 显示我关注的 UP 主的动态流） -->
    <div class="channel-bar">
      <span class="channel" :class="{ active: followMode }" @click="selectFollow">关注</span>
      <span
        v-for="c in categories"
        :key="c.value"
        class="channel"
        :class="{ active: !followMode && category === c.value }"
        @click="selectCategory(c.value)"
      >
        {{ c.label }}
      </span>
    </div>

    <!-- 排序 tab：最新 / 最热 -->
    <div class="sort-bar">
      <span
        v-for="s in sorts"
        :key="s.value"
        class="sort"
        :class="{ active: sort === s.value }"
        @click="selectSort(s.value)"
      >
        {{ s.label }}
      </span>
    </div>

    <!-- 视频卡片网格：数据来自后端 /api/video/list -->
    <div class="video-grid">
      <VideoCard
        v-for="video in videos"
        :key="video.id"
        :video="video"
        @click="goDetail(video.id)"
      />
    </div>

    <!-- 加载更多 / 没有更多 -->
    <div class="load-more">
      <button v-if="hasMore" class="more-btn" :disabled="loading" @click="loadMore">
        {{ loading ? '加载中…' : '加载更多' }}
      </button>
      <p v-else-if="videos.length > 0" class="no-more">没有更多视频了</p>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import VideoCard from '../components/VideoCard.vue'
import { getVideoList, getFeed } from '../api/video'
import { userStore } from '../store/user'

const router = useRouter()

/** 分类（对应后端 category：0默认 1生活 2游戏 3科技 4美食） */
const categories = [
  { value: null, label: '全部' },
  { value: 1, label: '生活' },
  { value: 2, label: '游戏' },
  { value: 3, label: '科技' },
  { value: 4, label: '美食' }
]

/** 排序：hot=播放量，new=最新 */
const sorts = [
  { value: 'new', label: '最新' },
  { value: 'hot', label: '最热' }
]

const category = ref(null)
const sort = ref('new')
const followMode = ref(false) // true=关注 Tab（显示我关注的人的动态流）
const page = ref(1)
const videos = ref([])
const total = ref(0)
const loading = ref(false)

const hasMore = computed(() => videos.value.length < total.value)

/** 切换关注 Tab：未登录弹登录框，登录后加载关注动态流 */
function selectFollow() {
  if (followMode.value) return
  if (!userStore.user) {
    userStore.loginVisible = true
    return
  }
  followMode.value = true
  reload()
}

/** 切换分类：退出关注模式，重置到第一页重新加载 */
function selectCategory(v) {
  // 去抖条件：仅在"不在关注模式 且 点击的是当前分类"时跳过。
  // 若从"关注"切回(此时 category 仍是进入关注前的值,与点击的分类常相同),
  // 必须强制 reload,否则列表停留在空的关注流上(表现为页面空白,需手动刷新)
  if (!followMode.value && category.value === v) return
  followMode.value = false
  category.value = v
  reload()
}

/** 切换排序：重置到第一页重新加载 */
function selectSort(v) {
  if (sort.value === v) return
  sort.value = v
  reload()
}

/** 重新加载第一页 */
async function reload() {
  page.value = 1
  videos.value = []
  total.value = 0
  await loadPage(true)
}

/** 加载下一页（追加） */
async function loadMore() {
  await loadPage(false)
}

/** 请求接口拉取一页数据：replace=true 覆盖列表，false 追加
 * 关注 Tab 走 /video/feed（我关注的 UP 主动态），其余走 /video/list
 */
async function loadPage(replace) {
  if (loading.value) return
  loading.value = true
  try {
    const params = { page: page.value, size: 12 }
    const res = followMode.value
      ? await getFeed({ ...params, sort: sort.value })
      : await getVideoList({ ...params, category: category.value, sort: sort.value })
    const records = res.data.records
    videos.value = replace ? records : [...videos.value, ...records]
    total.value = res.data.total
    page.value++
  } catch (e) {
    // 首页接口失败时保留现有数据，不打扰用户
  } finally {
    loading.value = false
  }
}

/** 点击卡片进入播放详情页 */
function goDetail(id) {
  router.push(`/video/detail/${id}`)
}

onMounted(() => {
  loadPage(true)
})
</script>

<style scoped>
.home {
  max-width: 1200px;
  margin: 0 auto;
  padding: 84px 24px 40px; /* 顶部预留导航栏高度 */
}

/* 分类栏 */
.channel-bar {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 12px;
}

.channel {
  padding: 6px 16px;
  border-radius: 18px;
  font-size: 14px;
  color: #555;
  background: #f5f5f7;
  cursor: pointer;
  transition: all 0.2s;
}

.channel:hover {
  color: #fb7299;
}

.channel.active {
  color: #fff;
  background: #fb7299;
}

/* 排序 tab */
.sort-bar {
  display: flex;
  gap: 20px;
  margin-bottom: 20px;
  padding-bottom: 10px;
  border-bottom: 1px solid #f0f0f3;
}

.sort {
  font-size: 14px;
  color: #666;
  cursor: pointer;
}

.sort:hover {
  color: #fb7299;
}

.sort.active {
  color: #fb7299;
  font-weight: bold;
}

/* 视频网格：4 列 */
.video-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 24px;
}

/* 窄屏自动降为 2 列、1 列 */
@media (max-width: 1024px) {
  .video-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 640px) {
  .video-grid {
    grid-template-columns: 1fr;
  }
}

/* 加载更多 */
.load-more {
  margin-top: 30px;
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
  transition: all 0.2s;
}

.more-btn:hover {
  background: #fb7299;
  color: #fff;
}

.more-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.no-more {
  color: #bbb;
  font-size: 13px;
}
</style>
