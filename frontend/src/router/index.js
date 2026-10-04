import { createRouter, createWebHistory } from 'vue-router'

// 路由 + 守卫：未登录访问业务页重定向到 /login（课件配角逐字保真）
const routes = [
  {
    path: '/login',
    name: 'login',
    component: () => import('../views/Login.vue'),
    meta: { public: true }
  },
  {
    path: '/workbench',
    name: 'workbench',
    component: () => import('../views/Workbench.vue')
  },
  {
    path: '/',
    redirect: '/workbench'
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/workbench'
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 路由守卫：未登录访问业务页 → 重定向 /login（独立导出以便测试）
export function authGuard(to) {
  if (to.path !== '/login' && !localStorage.getItem('token')) {
    return '/login'
  }
}

router.beforeEach(authGuard)

export default router
