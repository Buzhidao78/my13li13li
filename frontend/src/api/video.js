import request from './request'

/**
 * 视频相关接口封装
 * 上传用 multipart/form-data（要手动设 Content-Type 和放宽超时），其他接口默认即可
 */

/** 上传视频：formData 里包含 file(视频)、cover(封面)、title、description、category */
export function uploadVideo(formData) {
  return request.post('/video/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 60000 // 大文件上传可能超过默认 10s，放宽到 60s
  })
}

/** 查询视频详情（已发布所有人可见，未发布仅作者可见） */
export function getVideoDetail(id) {
  return request.get(`/video/detail/${id}`)
}

/** 分页查询已发布的视频列表（首页/分类页用） */
export function getVideoList(params) {
  return request.get('/video/list', { params })
}

/** 某个用户已发布的视频（他人主页用，公开） */
export function getUserVideos(userId, params) {
  return request.get(`/video/user/${userId}`, { params })
}

/** 关注动态流：我关注的用户发布的视频（需登录） */
export function getFeed(params) {
  return request.get('/video/feed', { params })
}

/** 播放计数：进入播放页时调用一次（登录用户同时记录观看历史） */
export function playVideo(id) {
  return request.post(`/video/${id}/play`)
}

/** 点赞/取消点赞（切换），返回最新点赞状态 */
export function toggleLike(id) {
  return request.post(`/video/${id}/like`)
}

/** 收藏/取消收藏（切换），返回最新收藏状态 */
export function toggleFavorite(id) {
  return request.post(`/video/${id}/favorite`)
}

/** 投币（每日上限 3 枚），返回今日剩余可投币数 */
export function coinVideo(id, coin = 1) {
  return request.post(`/video/${id}/coin`, null, { params: { coin } })
}

/** 分页查询视频评论（一级评论 + 楼中楼回复） */
export function getComments(videoId, params) {
  return request.get(`/video/${videoId}/comments`, { params })
}

/** 发表评论/回复：body 传 content 和 parentId */
export function addComment(videoId, data) {
  return request.post(`/video/${videoId}/comment`, data)
}

/** 我的收藏（个人中心） */
export function getFavorites(params) {
  return request.get('/user/me/favorites', { params })
}

/** 观看历史（个人中心） */
export function getHistory(params) {
  return request.get('/user/me/history', { params })
}

/** 分页查询"我的视频"（个人中心用） */
export function getMyVideos(params) {
  return request.get('/video/my', { params })
}

/** 审核视频：status = 1 通过 / 2 驳回（仅管理员可调，后端 service 校验 role） */
export function auditVideo(id, status) {
  return request.post(`/video/${id}/audit`, null, { params: { status } })
}

/** 管理员分页查询全站"待审核"视频（仅管理员可调） */
export function getPendingVideos(params) {
  return request.get('/video/pending', { params })
}

/** 下架视频（作者本人或管理员）：已发布 -> 已下架，前台不再展示 */
export function offlineVideo(id) {
  return request.post(`/video/${id}/offline`)
}

/** 重新上架（作者本人或管理员）：已下架 -> 已发布，前台恢复展示 */
export function republishVideo(id) {
  return request.post(`/video/${id}/republish`)
}

/** 删除视频（作者本人或管理员）：硬删除并级联清理互动数据与文件，不可恢复 */
export function deleteVideo(id) {
  return request.delete(`/video/${id}`)
}

/** 删除单条观看历史（个人中心"观看历史"用） */
export function deleteHistory(videoId) {
  return request.delete(`/user/me/history/${videoId}`)
}
