<template>
  <div class="search-page">
    <!-- 搜索框：顶部输入，回车/点击触发搜索 -->
    <div class="search-bar">
      <input
        v-model="keyword"
        class="search-input"
        placeholder="搜索你感兴趣的视频"
        maxlength="30"
        @keyup.enter="doSearch"
      />
      <button class="search-btn" :disabled="searching" @click="doSearch">搜索</button>
    </div>

    <!-- 搜索前：展示热门搜索词 + 我的搜索历史 -->
    <div v-if="!searched" class="suggest">
      <div class="hot">
        <div class="section-title">热门搜索</div>
        <div class="chips">
          <span v-for="w in hotWords" :key="w" class="chip hot-chip" @click="searchByWord(w)">{{ w }}</span>
          <span v-if="hotWords.length === 0" class="empty-tip">暂无热门词，快来搜索第一个吧</span>
        </div>
      </div>

      <div v-if="history.length > 0" class="history">
        <div class="section-title">
          搜索历史
          <span class="clear-btn" @click="onClearHistory">清空</span>
        </div>
        <div class="chips">
          <span v-for="w in history" :key="w" class="chip" @click="searchByWord(w)">{{ w }}</span>
        </div>
      </div>
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
import { searchVideos, getHotSearch, recordSearch, getSearchHistory, clearSearchHistory } from '../api/search'
import { userStore } from '../store/user'

const route = useRoute()
const router = useRouter()

const keyword = ref('')
const lastKeyword = ref('')   // 当前已搜索的关键词（用于展示结果标题）
const searched = ref(false)   // 是否已展示搜索结果（false 时展示热词/历史引导）
const hotWords = ref([])
const history = ref([])
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
  try {
    // 记录搜索（热词+1，登录用户写历史），失败不影响搜索本身
    try { await recordSearch(kw) } catch { /* 忽略 */ }
    await loadPage(true, kw)
    lastKeyword.value = kw
    searched.value = true
    loadSuggest() // 顺带刷新热词/历史
  } finally {
    searching.value = false
  }
}

/** 点击热词/历史词条：填入输入框并搜索 */
function searchByWord(w) {
  keyword.value = w
  doSearch()
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

/** 拉取热词 + 我的历史（登录才拉历史） */
async function loadSuggest() {
  try {
    const res = await getHotSearch()
    hotWords.value = res.data || []
  } catch { hotWords.value = [] }
  if (userStore.user) {
    try {
      const res = await getSearchHistory()
      history.value = res.data || []
    } catch { history.value = [] }
  } else {
    history.value = []
  }
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
  // 支持从导航栏带着关键词进来：/search?kw=xxx 直接出结果
  const kw = route.query.kw
  if (kw) {
    keyword.value = kw
    doSearch()
  } else {
    loadSuggest()
  }
})
</script>

<style scoped>
.search-page {
  max-width: 1200px;
  margin: 0 auto;
  padding: 90px 24px 40px;
  min-height: 100vh;
}

/* 顶部搜索框 */
.search-bar {
  display: flex;
  gap: 10px;
  max-width: 640px;
  margin: 0 auto 30px;
}

.search-input {
  flex: 1;
  height: 44px;
  border: 2px solid #fb7299;
  border-radius: 22px;
  padding: 0 20px;
  font-size: 15px;
  outline: none;
}

.search-btn {
  width: 96px;
  border: none;
  border-radius: 22px;
  background: #fb7299;
  color: #fff;
  font-size: 15px;
  cursor: pointer;
}

.search-btn:disabled {
  opacity: 0.6;
}

/* 搜索前的引导区 */
.suggest {
  max-width: 640px;
  margin: 0 auto;
}

.section-title {
  font-size: 15px;
  font-weight: bold;
  color: #333;
  margin-bottom: 12px;
}

.history .section-title {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.clear-btn {
  font-size: 12px;
  font-weight: normal;
  color: #999;
  cursor: pointer;
}

.clear-btn:hover {
  color: #fb7299;
}

.hot {
  margin-bottom: 26px;
}

.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.chip {
  padding: 6px 14px;
  border-radius: 16px;
  background: #f5f5f7;
  color: #555;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.2s;
}

.chip:hover {
  color: #fb7299;
  background: #fdeef3;
}

.hot-chip {
  color: #fb7299;
  border: 1px solid #ffd6e2;
  background: #fff;
}

.empty-tip {
  color: #bbb;
  font-size: 13px;
}

/* 搜索结果 */
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
