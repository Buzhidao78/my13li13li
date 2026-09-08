<template>
  <div class="audit-page">
    <div class="audit-card">
      <h1 class="page-title">视频审核台</h1>
      <p class="page-tip">按提交时间正序排列，最早提交的优先审。每页 20 条。</p>

      <div v-if="loading && list.length === 0" class="empty">加载中…</div>
      <div v-else-if="list.length === 0" class="empty">暂无待审核视频</div>

      <table v-else class="audit-table">
        <thead>
          <tr>
            <th class="col-id">ID</th>
            <th>标题</th>
            <th class="col-cat">分类</th>
            <th class="col-author">作者</th>
            <th class="col-time">提交时间</th>
            <th class="col-op">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="v in list" :key="v.id">
            <td class="col-id">{{ v.id }}</td>
            <td class="title-cell" :title="v.description" @click="goPreview(v.id)">{{ v.title }}</td>
            <td class="col-cat">{{ categoryText(v.category) }}</td>
            <td class="col-author">{{ v.author || ('UP#' + v.userId) }}</td>
            <td class="col-time">{{ formatTime(v.createTime) }}</td>
            <td class="col-op">
              <button class="op-btn pass" :disabled="busy === v.id" @click="onAudit(v.id, 1)">通过</button>
              <button class="op-btn reject" :disabled="busy === v.id" @click="onAudit(v.id, 2)">驳回</button>
            </td>
          </tr>
        </tbody>
      </table>

      <!-- 翻页 -->
      <div v-if="list.length > 0 || page > 1" class="pager">
        <button class="pg-btn" :disabled="page <= 1 || loading" @click="changePage(page - 1)">上一页</button>
        <span class="pg-info">第 {{ page }} 页 / 共 {{ totalPages }} 页（合计 {{ total }} 条）</span>
        <button class="pg-btn" :disabled="page >= totalPages || loading" @click="changePage(page + 1)">下一页</button>
      </div>

      <div v-if="error" class="error-tip">{{ error }}</div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getPendingVideos, auditVideo } from '../api/video'

const router = useRouter()

const list = ref([])
const page = ref(1)
const size = 20
const total = ref(0)
const loading = ref(false)
const busy = ref(null)        // 当前正在审核的视频 ID（按钮置灰防重复）
const error = ref('')

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / size)))

/** 拉取当前页的待审核视频 */
async function load() {
  loading.value = true
  error.value = ''
  try {
    const res = await getPendingVideos({ page: page.value, size })
    list.value = res.data.records
    total.value = res.data.total
  } catch (e) {
    error.value = e.message || '加载失败'
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function changePage(p) {
  if (p < 1 || p > totalPages.value || p === page.value) return
  page.value = p
  load()
}

/** 审核：status=1 通过 / 2 驳回 */
async function onAudit(id, status) {
  if (busy.value) return
  busy.value = id
  error.value = ''
  try {
    await auditVideo(id, status)
    // 重新拉当前页：若当前页已空且非第一页，回退一页
    if (list.value.length === 1 && page.value > 1) {
      page.value -= 1
    }
    await load()
  } catch (e) {
    error.value = e.message || '审核失败'
  } finally {
    busy.value = null
  }
}

/** 点击标题进预览（已发布才能正常查看详情；待审核的预览仅作者可见，管理员会 403） */
function goPreview(id) {
  router.push(`/video/detail/${id}`)
}

/** 分类数字 → 文案 */
function categoryText(c) {
  return { 0: '默认', 1: '生活', 2: '游戏', 3: '科技', 4: '美食' }[c] || '其他'
}

/** 去掉 ISO 字符串的 T，截到分钟 */
function formatTime(t) {
  if (!t) return ''
  return String(t).replace('T', ' ').slice(0, 16)
}

onMounted(load)
</script>

<style scoped>
.audit-page {
  max-width: 1200px;
  margin: 0 auto;
  padding: 90px 24px 40px;
}

.audit-card {
  background: #fff;
  border-radius: 12px;
  padding: 32px 36px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.04);
}

.page-title {
  margin: 0 0 6px;
  font-size: 22px;
  color: #222;
}

.page-tip {
  margin: 0 0 24px;
  font-size: 13px;
  color: #999;
}

.audit-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 14px;
}

.audit-table th,
.audit-table td {
  padding: 12px 8px;
  text-align: left;
  border-bottom: 1px solid #f0f0f3;
}

.audit-table th {
  background: #fafafc;
  color: #555;
  font-weight: 600;
}

.col-id,
.col-cat,
.col-author,
.col-time {
  white-space: nowrap;
}

.col-op {
  white-space: nowrap;
  width: 1%;
}

.title-cell {
  cursor: pointer;
  color: #fb7299;
}

.title-cell:hover {
  text-decoration: underline;
}

.op-btn {
  height: 30px;
  padding: 0 14px;
  border: 1px solid;
  border-radius: 6px;
  font-size: 13px;
  cursor: pointer;
  margin-right: 6px;
  background: #fff;
}

.op-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.op-btn.pass {
  color: #2a9d5f;
  border-color: #2a9d5f;
}

.op-btn.pass:hover:not(:disabled) {
  background: #2a9d5f;
  color: #fff;
}

.op-btn.reject {
  color: #e04343;
  border-color: #e04343;
}

.op-btn.reject:hover:not(:disabled) {
  background: #e04343;
  color: #fff;
}

.pager {
  margin-top: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16px;
}

.pg-btn {
  height: 34px;
  padding: 0 18px;
  border: 1px solid #fb7299;
  border-radius: 17px;
  background: #fff;
  color: #fb7299;
  font-size: 13px;
  cursor: pointer;
}

.pg-btn:hover:not(:disabled) {
  background: #fb7299;
  color: #fff;
}

.pg-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.pg-info {
  font-size: 13px;
  color: #666;
}

.empty {
  padding: 60px 0;
  text-align: center;
  color: #bbb;
  font-size: 14px;
}

.error-tip {
  margin-top: 16px;
  padding: 10px 14px;
  border-radius: 6px;
  background: #fdeeee;
  color: #e04343;
  font-size: 13px;
}
</style>
