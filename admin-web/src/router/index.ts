import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const routes = [
  { path: '/login', component: () => import('../views/LoginView.vue'), meta: { public: true } },
  {
    path: '/',
    component: () => import('../layouts/MainLayout.vue'),
    children: [
      { path: '', redirect: '/dashboard' },
      { path: 'dashboard', component: () => import('../views/DashboardView.vue'), meta: { title: '工作台' } },
      { path: 'system/users', component: () => import('../views/PlaceholderView.vue'), meta: { title: '用户管理', group: '系统管理' } },
      { path: 'archive/suppliers', component: () => import('../views/PlaceholderView.vue'), meta: { title: '供货商', group: '基础档案' } },
      { path: 'archive/customers', component: () => import('../views/PlaceholderView.vue'), meta: { title: '客户', group: '基础档案' } },
      { path: 'archive/green-bean', component: () => import('../views/PlaceholderView.vue'), meta: { title: '生豆品种', group: '基础档案' } },
      { path: 'archive/roasted-product', component: () => import('../views/PlaceholderView.vue'), meta: { title: '熟豆商品', group: '基础档案' } },
      { path: 'greenbean/purchases', component: () => import('../views/PlaceholderView.vue'), meta: { title: '采购入库', group: '生豆' } },
      { path: 'roast/batches', component: () => import('../views/PlaceholderView.vue'), meta: { title: '烘焙批次', group: '烘焙' } },
      { path: 'stock/overview', component: () => import('../views/PlaceholderView.vue'), meta: { title: '库存看板', group: '熟豆库存' } }
    ]
  }
]

const router = createRouter({ history: createWebHistory(), routes })

router.beforeEach(async (to) => {
  const auth = useAuthStore()
  if (to.meta.public) return true
  if (!auth.token) return '/login'
  if (!auth.user) {
    try {
      await auth.fetchMe()
    } catch {
      return '/login'
    }
  }
  return true
})

export default router
