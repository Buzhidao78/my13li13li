<template>
  <div class="search-page">
    <!-- 搜索框已移至 NavBar 顶部；本页不再自带输入框，避免重复 -->

    <!-- 搜索前：简洁提示，让用户使用 NavBar 搜索框 -->
    <div v-if="!searched" class="empty-tip">
      请在顶部搜索框输入关键词，或点击搜索框查看<strong>热搜 / 搜索历史</strong>
    </div>

    <!-- 搜索结果：关键词 + 视频卡片网格 -->
    <template v-else>
      <div class="result-title">
        搜索"<span class="kw">{{ lastKeyword }}</span>"，共 {{ total }} 个结果
      </div>
      <div v-if="videos.length === 0" class="empty">没有找到相关视频，换个关键词试试</div>
      <div v-else class="video-grid">
        <VideoCard v-for="video in videos" :key="video.id" :video="video" @click="goDetail(video.id)" />
      </div>
      <div class="load-more">
        <button v-if="hasMore" class="more-btn" :disabled="loading" @click="loadMore">
          {{ loading ? '加载中…' : '加载更多' }}
        </button>
        <p v-else-if="videos.length > 0" class="no-more">没有更多了</p>
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import VideoCard from '../components/VideoCard.vue'
import { searchVideos, recordSearch } from '../api/search'

const route = useRoute()
const router = useRouter()

const keyword = ref('')
const lastKeyword = ref('')   // 当前已搜索的关键词（用于展示结果标题）
const searched = ref(false)   // 是否已展示搜索结果（false 时展示"请用顶部搜索框"引导）
const videos = ref([])
const total = ref(0)
const page = ref(1)
const loading = ref(false)
const searching = ref(false)

const hasMore = computed(() => videos.value.length < total.value)

/** 触发一次搜索：记搜索记录 + 拉第一页结果 */
async function doSearch() {
  const kw = keyword.value.trim()
  if (!kw) return
  if (searching.value) return
  searching.value = true
  // 新搜索时显式重置分页状态：避免 page 累加导致第二次搜索请求到第 N 页（命中空集）
  page.value = 1
  videos.value = []
  total.value = 0
  try {
    // 记录搜索（热词+1，登录用户写历史），失败不影响搜索本身
    try { await recordSearch(kw) } catch { /* 忽略 */ }
    await loadPage(true, kw)
    lastKeyword.value = kw
    searched.value = true
  } finally {
    searching.value = false
  }
}

/** 加载一页结果：replace=true 覆盖（新搜索），false 追加（加载更多） */
async function loadPage(replace, kw) {
  if (loading.value) return
  loading.value = true
  try {
    const res = await searchVideos({ keyword: kw ?? lastKeyword.value, page: page.value, size: 12 })
    videos.value = replace ? res.data.records : [...videos.value, ...res.data.records]
    total.value = res.data.total
    page.value++
  } catch (e) {
    // 搜索失败保留现状
  } finally {
    loading.value = false
  }
}

async function loadMore() {
  await loadPage(false)
}

/** 清空搜索历史 */
async function onClearHistory() {
  try {
    await clearSearchHistory()
    history.value = []
  } catch { /* 忽略 */ }
}

function goDetail(id) {
  router.push(`/video/detail/${id}`)
}

onMounted(() => {
  // 支持从 NavBar 带着关键词进来：/search?kw=xxx 直接出结果
  const kw = route.query.kw
  if (kw) {
    keyword.value = kw
    doSearch()
  }
  // 没有 kw：保持引导态（"请在顶部搜索框输入..."），热词/历史由 NavBar 下拉加载
})
</script>

<style scoped>
.search-page {
  max-width: 1200px;
  margin: 0 auto;
  /* 顶部不再有自带搜索框，预留导航栏高度 + 一点呼吸即可 */
  padding: 90px 24px 40px;
  min-height: 100vh;
}

/* 引导态：简洁提示让用户使用 NavBar 搜索框 */
.empty-tip {
  max-width: 640px;
  margin: 0 auto;
  padding: 40px 0;
  text-align: center;
  font-size: 14px;
  color: #888;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
}
.empty-tip strong {
  color: #fb7299;
  margin: 0 4px;
}

.result-title {
  font-size: 15px;
  color: #555;
  margin-bottom: 20px;
}

.result-title .kw {
  color: #fb7299;
  font-weight: bold;
}

.empty {
  padding: 80px 0;
  text-align: center;
  color: #bbb;
  font-size: 14px;
}

.video-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 24px;
}

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
