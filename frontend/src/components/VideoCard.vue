<template>
  <!-- 视频卡片：B站首页推荐卡片样式，展示后端返回的视频数据 -->
  <div class="video-card" @click="$emit('click')">
    <!-- 封面图：有封面显示图片，没有封面显示渐变占位块 -->
    <div class="cover-wrap">
      <img v-if="video.coverUrl" :src="video.coverUrl" :alt="video.title" class="cover" loading="lazy" />
      <div v-else class="cover placeholder">{{ video.title }}</div>
      <!-- 时长角标：数据里有就显示（当前未存储时长，隐藏） -->
      <span v-if="video.duration" class="duration">{{ video.duration }}</span>
    </div>

    <!-- 标题：最多两行，超出省略 -->
    <div class="title">{{ video.title }}</div>

    <!-- 作者 + 播放量：点击作者昵称进入 TA 的主页（stop 防止触发卡片点击） -->
    <div class="meta">
      <span class="author" @click.stop="goProfile">{{ video.authorNickname || '匿名用户' }}</span>
      <span class="views">{{ formatViews(video.playCount) }}播放</span>
    </div>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'
/**
 * 视频卡片组件：接收后端 video 对象（coverUrl/authorNickname/playCount）并展示
 * 通过 props 从父组件传入数据，通过 $emit 向父组件发送点击事件
 */
defineProps({
  video: {
    type: Object,
    required: true
  }
})

defineEmits(['click'])

const router = useRouter()

/** 点击作者：自己进个人中心，别人进 TA 的主页 */
function goProfile() {
  router.push(`/user/${video.userId}`)
}

/** 播放量格式化：超过 1 万显示 x.x万，否则原样 */
function formatViews(count) {
  const n = Number(count) || 0
  return n >= 10000 ? (n / 10000).toFixed(1) + '万' : n
}
</script>

<style scoped>
.video-card {
  cursor: pointer;
  transition: transform 0.15s;
}

.video-card:hover {
  transform: translateY(-4px);
}

/* 封面区域 */
.cover-wrap {
  position: relative;
  border-radius: 8px;
  overflow: hidden;
  aspect-ratio: 16 / 9;
  background: #1f1f23;
}

.cover {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

/* 无封面时的占位：渐变背景 + 标题文字居中 */
.placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0 16px;
  box-sizing: border-box;
  text-align: center;
  font-size: 13px;
  color: #ccc;
  background: linear-gradient(135deg, #3a3a46, #1f1f23);
}

/* 时长角标：右下角黑色半透明底 */
.duration {
  position: absolute;
  right: 6px;
  bottom: 6px;
  background: rgba(0, 0, 0, 0.7);
  color: #fff;
  font-size: 12px;
  padding: 1px 6px;
  border-radius: 4px;
}

/* 标题：两行省略 */
.title {
  margin-top: 8px;
  font-size: 14px;
  line-height: 1.4;
  color: #222;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.video-card:hover .title {
  color: #fb7299;
}

/* 作者 + 播放量 */
.meta {
  margin-top: 6px;
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: #999;
}

.author {
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  max-width: 60%;
  cursor: pointer;
}

.author:hover {
  color: #fb7299;
}
</style>
