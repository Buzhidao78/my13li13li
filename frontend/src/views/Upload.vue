<template>
  <div class="upload-page">
    <div class="upload-card">
      <h1 class="page-title">投稿</h1>

      <!-- ===== 表单：v-model 绑定输入内容，文件用 @change 单独处理 ===== -->
      <div class="form-item">
        <label class="label">视频标题 <span class="required">*</span></label>
        <input v-model="title" class="input" placeholder="给视频起个标题（100字以内）" maxlength="100" />
      </div>

      <div class="form-item">
        <label class="label">视频简介</label>
        <textarea v-model="description" class="textarea" rows="3" placeholder="简单介绍一下这个视频" maxlength="500"></textarea>
      </div>

      <div class="form-item">
        <label class="label">分类</label>
        <!-- 分类下拉：v-model 绑定选中值 -->
        <select v-model="category" class="input">
          <option v-for="c in categories" :key="c.value" :value="c.value">{{ c.label }}</option>
        </select>
      </div>

      <!-- 文件选择区：原生 file input 隐藏，点击自定义区域触发 -->
      <div class="form-item">
        <label class="label">视频文件 <span class="required">*</span></label>
        <div class="file-box" @click="videoInput.click()">
          <input ref="videoInput" type="file" accept="video/*" class="hidden-input" @change="onVideoChange" />
          <span v-if="videoName" class="file-name">已选择：{{ videoName }}</span>
          <span v-else class="file-placeholder">点击选择视频（mp4/webm/mov，≤100MB）</span>
        </div>
      </div>

      <div class="form-item">
        <label class="label">封面图（可选）</label>
        <div class="file-box" @click="coverInput.click()">
          <input ref="coverInput" type="file" accept="image/*" class="hidden-input" @change="onCoverChange" />
          <span v-if="coverName" class="file-name">已选择：{{ coverName }}</span>
          <span v-else class="file-placeholder">点击选择封面（jpg/png/webp，≤5MB）</span>
        </div>
        <!-- 封面预览：v-if 选了才显示，用 URL.createObjectURL 生成临时预览 -->
        <img v-if="coverPreview" :src="coverPreview" class="cover-preview" alt="封面预览" />
      </div>

      <!-- 提交按钮：上传中禁用 + 显示进度文案 -->
      <button class="submit-btn" :disabled="uploading" @click="submit">
        {{ uploading ? '上传中…' : '提交投稿' }}
      </button>

      <!-- 上传成功提示：v-if 条件渲染 -->
      <div v-if="uploaded" class="success-tip">
        上传成功！视频已进入<strong>待审核</strong>状态，审核通过后即可公开展示。
      </div>
      <!-- 错误提示 -->
      <div v-if="error" class="error-tip">{{ error }}</div>
    </div>

    <!-- ===== 我的视频列表：方便测试"上传→审核→播放"闭环 ===== -->
    <div class="my-videos">
      <h2 class="sub-title">我的投稿</h2>
      <div v-if="myVideos.length === 0" class="empty">还没有投稿，上传第一个视频吧</div>
      <div v-for="v in myVideos" :key="v.id" class="video-row">
        <span class="video-title" @click="goDetail(v.id)">{{ v.title }}</span>
        <!-- 状态徽章：动态 class 换颜色 -->
        <span class="status-badge" :class="'status-' + v.status">{{ statusText(v.status) }}</span>
        <!-- 阶段一测试入口：待审核时可以点"通过审核"，正式管理后台在运营阶段做 -->
        <button v-if="v.status === 0" class="audit-btn" @click="passAudit(v.id)">通过审核</button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { uploadVideo, getMyVideos, auditVideo } from '../api/video'

const router = useRouter()

// ===== 表单状态 =====
const title = ref('')
const description = ref('')
const category = ref(0)
const categories = [
  { value: 0, label: '默认' },
  { value: 1, label: '生活' },
  { value: 2, label: '游戏' },
  { value: 3, label: '科技' },
  { value: 4, label: '美食' }
]

// ===== 文件选择 =====
const videoInput = ref(null)
const coverInput = ref(null)
const videoName = ref('')
const coverName = ref('')
const coverPreview = ref('') // 封面本地预览地址（临时 URL）

function onVideoChange(e) {
  const file = e.target.files[0]
  videoName.value = file ? file.name : ''
}
function onCoverChange(e) {
  const file = e.target.files[0]
  coverName.value = file ? file.name : ''
  // URL.createObjectURL 生成临时预览链接；组件卸载前要释放（见 onBeforeUnmount）
  if (file) {
    if (coverPreview.value) URL.revokeObjectURL(coverPreview.value)
    coverPreview.value = URL.createObjectURL(file)
  } else {
    coverPreview.value = ''
  }
}

// ===== 提交上传 =====
const uploading = ref(false)
const uploaded = ref(false)
const error = ref('')

async function submit() {
  error.value = ''
  if (!videoName.value) {
    error.value = '请先选择视频文件'
    return
  }
  if (!title.value.trim()) {
    error.value = '请填写视频标题'
    return
  }
  // 组装 FormData：文件 + 表单字段一起提交
  const formData = new FormData()
  formData.append('file', videoInput.value.files[0])
  if (coverInput.value.files[0]) formData.append('cover', coverInput.value.files[0])
  formData.append('title', title.value.trim())
  formData.append('description', description.value.trim())
  formData.append('category', category.value)

  uploading.value = true
  try {
    await uploadVideo(formData)
    uploaded.value = true
    // 上传成功后清空表单，方便继续投第二个
    title.value = ''
    description.value = ''
    category.value = 0
    videoName.value = ''
    coverName.value = ''
    if (coverPreview.value) URL.revokeObjectURL(coverPreview.value)
    coverPreview.value = ''
    loadMyVideos()
  } catch (e) {
    error.value = e.message || '上传失败，请重试'
  } finally {
    uploading.value = false
  }
}

// ===== 我的投稿 =====
const myVideos = ref([])

async function loadMyVideos() {
  try {
    const res = await getMyVideos({ page: 1, size: 12 })
    myVideos.value = res.data.records
  } catch {
    // 未登录或加载失败：静默忽略（列表区域显示空）
  }
}

/** 状态数字 → 文案 */
function statusText(status) {
  return { 0: '待审核', 1: '已发布', 2: '已驳回', 3: '已下架' }[status] || '未知'
}

/** 阶段一测试入口：点"通过审核"把视频状态改为已发布 */
async function passAudit(id) {
  try {
    await auditVideo(id, 1)
    loadMyVideos()
  } catch (e) {
    error.value = e.message || '审核失败'
  }
}

/** 跳转到播放详情页 */
function goDetail(id) {
  router.push(`/video/detail/${id}`)
}

// 组件卸载前释放封面预览的临时 URL，避免内存泄漏
onBeforeUnmount(() => {
  if (coverPreview.value) URL.revokeObjectURL(coverPreview.value)
})

// 页面进入时加载一次我的投稿
loadMyVideos()
</script>

<style scoped>
.upload-page {
  min-height: 100vh;
  padding: 90px 16px 40px;
  box-sizing: border-box;
}

.upload-card {
  max-width: 620px;
  margin: 0 auto;
  padding: 28px;
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 8px 30px rgba(0, 0, 0, 0.08);
}

.page-title {
  margin: 0 0 20px;
  font-size: 24px;
  text-align: center;
}

.form-item {
  margin-bottom: 18px;
}

.label {
  display: block;
  margin-bottom: 6px;
  font-size: 14px;
  color: #555;
}

.required {
  color: #e34d59;
}

.input,
.textarea,
select.input {
  width: 100%;
  box-sizing: border-box;
  padding: 10px 14px;
  border: 2px solid #e3e3e8;
  border-radius: 10px;
  font-size: 15px;
  outline: none;
  transition: border-color 0.2s;
}

.input:focus,
.textarea:focus {
  border-color: #fb7299;
}

.textarea {
  resize: vertical;
  font-family: inherit;
}

/* 文件选择框：整块可点击 */
.file-box {
  padding: 22px 14px;
  border: 2px dashed #d5d5dd;
  border-radius: 10px;
  text-align: center;
  cursor: pointer;
  transition: border-color 0.2s;
}

.file-box:hover {
  border-color: #fb7299;
}

.hidden-input {
  display: none;
}

.file-name {
  color: #fb7299;
  font-size: 14px;
}

.file-placeholder {
  color: #999;
  font-size: 14px;
}

.cover-preview {
  margin-top: 10px;
  max-width: 200px;
  border-radius: 8px;
}

.submit-btn {
  width: 100%;
  padding: 12px 0;
  border: none;
  border-radius: 10px;
  background: #fb7299;
  color: #fff;
  font-size: 16px;
  font-weight: bold;
  cursor: pointer;
  transition: opacity 0.2s;
}

.submit-btn:hover {
  opacity: 0.85;
}

.submit-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.success-tip {
  margin-top: 14px;
  padding: 12px;
  background: #e8f7ee;
  color: #1a8a4a;
  border-radius: 8px;
  font-size: 14px;
}

.error-tip {
  margin-top: 14px;
  padding: 12px;
  background: #fdecec;
  color: #e34d59;
  border-radius: 8px;
  font-size: 14px;
}

/* 我的投稿列表 */
.my-videos {
  max-width: 620px;
  margin: 24px auto 0;
  padding: 20px 28px;
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 8px 30px rgba(0, 0, 0, 0.08);
}

.sub-title {
  margin: 0 0 14px;
  font-size: 18px;
}

.empty {
  padding: 20px 0;
  text-align: center;
  color: #bbb;
  font-size: 14px;
}

.video-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 0;
  border-bottom: 1px solid #f0f0f3;
}

.video-row:last-child {
  border-bottom: none;
}

.video-title {
  flex: 1;
  font-size: 14px;
  color: #333;
  cursor: pointer;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.video-title:hover {
  color: #fb7299;
}

/* 状态徽章：不同状态不同颜色（动态 class） */
.status-badge {
  padding: 2px 10px;
  border-radius: 10px;
  font-size: 12px;
  flex-shrink: 0;
}

.status-0 {
  background: #fff7e6;
  color: #d48806;
}

.status-1 {
  background: #e8f7ee;
  color: #1a8a4a;
}

.status-2 {
  background: #fdecec;
  color: #e34d59;
}

.status-3 {
  background: #f0f0f3;
  color: #888;
}

.audit-btn {
  border: 1px solid #fb7299;
  background: transparent;
  color: #fb7299;
  border-radius: 12px;
  padding: 3px 12px;
  font-size: 12px;
  cursor: pointer;
  flex-shrink: 0;
}

.audit-btn:hover {
  background: #fb7299;
  color: #fff;
}
</style>
