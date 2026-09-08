import { createRouter, createWebHistory } from 'vue-router'
import { getToken, userStore } from '../store/user'

const routes = [
  {
    path: '/',
    name: 'Home',
    component: () => import('../views/Home.vue')
  },
  {
    // 会员充值页：需要登录才能访问（路由守卫自动判断）
    path: '/recharge',
    name: 'Recharge',
    component: () => import('../views/Recharge.vue'),
    meta: { requiresAuth: true }
  },
  {
    // 待办清单练习页：纯前端练习，不需要登录（数据存 localStorage）
    path: '/todo',
    name: 'TodoPractice',
    component: () => import('../views/TodoPractice.vue')
  },
  {
    // 视频投稿页：需要登录才能访问
    path: '/upload',
    name: 'Upload',
    component: () => import('../views/Upload.vue'),
    meta: { requiresAuth: true }
  },
  {
    // 视频播放详情页：公开（游客可看已发布视频）
    path: '/video/detail/:id',
    name: 'VideoDetail',
    component: () => import('../views/VideoDetail.vue')
  },
  {
    // 个人中心：我的视频/收藏/历史（需登录）
    path: '/user/center',
    name: 'UserCenter',
    component: () => import('../views/UserCenter.vue'),
    meta: { requiresAuth: true }
  },
  {
    // 他人主页：公开（游客可看，关注按钮需登录）
    // 注意：必须注册在 /user/center 之后，否则 /user/center 会被 /user/:id 抢先匹配
    path: '/user/:id',
    name: 'UserProfile',
    component: () => import('../views/UserProfile.vue')
  },
  {
    // 搜索结果页：公开
    path: '/search',
    name: 'Search',
    component: () => import('../views/Search.vue')
  },
  {
    // 消息通知中心：需登录
    path: '/notification',
    name: 'Notification',
    component: () => import('../views/Notification.vue'),
    meta: { requiresAuth: true }
  },
  {
    // 管理员审核台：需登录且 role=1
    path: '/admin/audit',
    name: 'Audit',
    component: () => import('../views/Audit.vue'),
    meta: { requiresAuth: true, requiresAdmin: true }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

/**
 * 全局路由守卫：进入需要登录的页面时检查登录态
 * 练习：后续新增需要登录的页面（如个人中心），加上 meta.requiresAuth: true 即可
 */
router.beforeEach((to) => {
  if (to.meta && to.meta.requiresAuth && !getToken()) {
    // 未登录：记录目标地址并弹出登录框，同时拦截本次跳转（留在当前页）
    // 登录成功后由 LoginModal 读取 redirectTo 自动跳回，实现"登录后回到想去的地方"
    userStore.redirectTo = to.fullPath
    userStore.loginVisible = true
    return false
  }
  if (to.meta && to.meta.requiresAdmin) {
    // 仅管理员（role=1）可访问；非管理员直接拦截回首页（后端 service 也会再校验一次）
    const u = userStore.user
    if (!u || u.role !== 1) {
      alert('仅管理员可访问此页面')
      return { path: '/' }
    }
  }
})

export default router
