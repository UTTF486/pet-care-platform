import { createRouter, createWebHistory } from 'vue-router'
import { getToken, getUser, isAdmin } from '../store/user'

const routes = [
  { path: '/login', name: 'login', component: () => import('../views/Login.vue'), meta: { title: '登录' } },

  // 用户端
  {
    path: '/user',
    component: () => import('../layout/UserLayout.vue'),
    redirect: '/user/home',
    children: [
      { path: 'home', name: 'user-home', component: () => import('../views/user/Home.vue'), meta: { title: '预约服务' } },
      { path: 'service/:id', name: 'service-detail', component: () => import('../views/user/ServiceDetail.vue'), meta: { title: '服务详情' } },
      { path: 'orders', name: 'user-orders', component: () => import('../views/user/MyOrders.vue'), meta: { title: '我的订单' } },
      { path: 'pets', name: 'user-pets', component: () => import('../views/user/MyPets.vue'), meta: { title: '宠物档案' } }
    ]
  },

  // 商家后台
  {
    path: '/admin',
    component: () => import('../layout/AdminLayout.vue'),
    redirect: '/admin/dashboard',
    meta: { admin: true },
    children: [
      { path: 'dashboard', name: 'admin-dashboard', component: () => import('../views/admin/Dashboard.vue'), meta: { title: '数据看板' } },
      { path: 'orders', name: 'admin-orders', component: () => import('../views/admin/OrderManage.vue'), meta: { title: '订单审核' } },
      { path: 'services', name: 'admin-services', component: () => import('../views/admin/ServiceManage.vue'), meta: { title: '服务管理' } },
      { path: 'servicers', name: 'admin-servicers', component: () => import('../views/admin/ServicerManage.vue'), meta: { title: '服务师管理' } },
      { path: 'users', name: 'admin-users', component: () => import('../views/admin/UserManage.vue'), meta: { title: '用户管理' } }
    ]
  },

  { path: '/', redirect: '/user/home' },
  { path: '/:pathMatch(.*)*', redirect: '/user/home' }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

/**
 * 全局前置守卫：
 * 1. 没登录 -> 去登录页（记住原目标，登录后跳回去）
 * 2. 访问 /admin 但不是管理员 -> 回用户端
 * 这只是前端体验层拦截，真正的权限校验在后端拦截器（前端拦不住直接调接口的人）。
 */
router.beforeEach((to, from, next) => {
  document.title = to.meta.title ? `${to.meta.title} · 爪爪到家` : '爪爪到家'

  if (to.path === '/login') {
    return next()
  }

  if (!getToken() || !getUser()) {
    return next({ path: '/login', query: { redirect: to.fullPath } })
  }

  if (to.meta.admin && !isAdmin()) {
    return next('/user/home')
  }

  next()
})

export default router
