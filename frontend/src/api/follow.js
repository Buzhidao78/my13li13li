import request from './request'

/**
 * 关注相关接口封装
 * 关注/取关和关注状态需要登录；用户主页公开（游客可看）
 */

/** 关注 / 取消关注（切换），返回最新状态：true=已关注 */
export function toggleFollow(userId) {
  return request.post(`/follow/${userId}`)
}

/** 当前登录用户是否已关注对方 */
export function getFollowStatus(userId) {
  return request.get(`/follow/status/${userId}`)
}

/** 用户主页信息：资料 + 视频数/粉丝数/关注数 + 是否已关注 */
export function getUserProfile(userId) {
  return request.get(`/user/${userId}/profile`)
}

/** 粉丝列表（关注了该用户的人） */
export function getFollowers(userId, params) {
  return request.get(`/follow/${userId}/followers`, { params })
}

/** 关注列表（该用户关注的人） */
export function getFollowing(userId, params) {
  return request.get(`/follow/${userId}/following`, { params })
}
