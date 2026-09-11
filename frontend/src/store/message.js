import { reactive } from 'vue'

/**
 * 消息中心全局状态：私信未读数
 *
 * 写入方有两个（两者可能短暂互相覆盖，30s 内自愈，练习项目接受）：
 * - NavBar 的 30s 轮询（getDmUnreadTotal，权威值整点覆盖）
 * - Message.vue 的 WebSocket 实时收信（增量修正，保证消息页内体验实时）
 */
export const messageStore = reactive({
  // 私信未读总数（红点数字）
  dmUnread: 0
})
