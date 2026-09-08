import request from './request'

/**
 * 搜索相关接口封装
 * 视频搜索/热词/记录公开；个人搜索历史需登录
 */

/** 标题模糊搜索（返回分页视频列表） */
export function searchVideos(params) {
  return request.get('/video/search', { params })
}

/** 热门搜索词 Top10 */
export function getHotSearch() {
  return request.get('/search/hot')
}

/** 记录一次搜索（热词+1；登录用户同时写个人历史） */
export function recordSearch(keyword) {
  return request.post('/search/record', null, { params: { keyword } })
}

/** 我的搜索历史（需登录） */
export function getSearchHistory() {
  return request.get('/search/history')
}

/** 清空我的搜索历史（需登录） */
export function clearSearchHistory() {
  return request.delete('/search/history')
}
