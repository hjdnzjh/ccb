import { createRouter, createWebHistory } from 'vue-router'
import NProgress from 'nprogress'
import 'nprogress/nprogress.css'
import { authApi } from '@/api/index.js'
import { clearSession, routeForRole } from '@/utils/session.js'

NProgress.configure({ showSpinner: false })

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login.vue'),
    meta: { title: '登录' }
  },
  {
    path: '/',
    component: () => import('@/views/Layout.vue'),
    redirect: '/dashboard',
    children: [
      { path: 'diagnosis', name: 'Diagnosis', component: () => import('@/views/diagnosis/Index.vue'), meta: { title: '诊断中心' } },
      { path: 'lab/replay', name: 'InnovationLab', component: () => import('@/views/lab/Replay.vue'), meta: { title: '创新演练' } },
      {
        path: 'portal', name: 'Portal',
        component: () => import('@/views/portal/Index.vue'),
        meta: { title: '我的用水工作台' }
      },
      {
        path: 'feedback', name: 'Feedback',
        component: () => import('@/views/feedback/Index.vue'),
        meta: { title: '用户反馈' }
      },
      {
        path: 'automation', name: 'Automation',
        component: () => import('@/views/automation/Index.vue'),
        meta: { title: '自动采集与计费' }
      },
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/dashboard/Index.vue'),
        meta: { title: '主页', icon: 'DataBoard' }
      },
      {
        path: 'ai/assistant',
        name: 'AiAssistant',
        component: () => import('@/views/ai/Assistant.vue'),
        meta: { title: '水务智能助手' }
      },
      {
        path: 'twin/map',
        name: 'TwinMap',
        component: () => import('@/views/twin/Map.vue'),
        meta: { title: '数字孪生地图' }
      },
      {
        path: 'twin/zone/:areaCode',
        name: 'TwinZoneDetail',
        component: () => import('@/views/twin/ZoneDetail.vue'),
        meta: { title: '分区详情' }
      },
      {
        path: 'meter/health',
        name: 'MeterHealth',
        component: () => import('@/views/meter/Health.vue'),
        meta: { title: '水表健康指数' }
      },
      {
        path: 'meter',
        name: 'Meter',
        redirect: '/meter/reading',
        meta: { title: '抄表可信度', icon: 'Odometer' },
        children: [
          {
            path: 'list',
            name: 'MeterList',
            component: () => import('@/views/meter/List.vue'),
            meta: { title: '水表资产' }
          },
          {
            path: 'reading',
            name: 'MeterReading',
            component: () => import('@/views/meter/Reading.vue'),
            meta: { title: 'AI抄表解释' }
          }
        ]
      },
      {
        path: 'bill',
        name: 'Bill',
        redirect: '/bill/insight',
        meta: { title: '智能收费', icon: 'Tickets' },
        children: [
          {
            path: 'tariff', name: 'ResidentialTariff',
            component: () => import('@/views/bill/Tariff.vue'),
            meta: { title: '居民年度计费' }
          },
          {
            path: 'insight',
            name: 'BillInsight',
            component: () => import('@/views/bill/Insight.vue'),
            meta: { title: '收费策略洞察' }
          },
          {
            path: 'list',
            name: 'BillList',
            component: () => import('@/views/bill/List.vue'),
            meta: { title: '账单列表' }
          },
          {
            path: 'payment',
            name: 'Payment',
            component: () => import('@/views/bill/Payment.vue'),
            meta: { title: '缴费管理' }
          }
        ]
      },
      {
        path: 'anomaly',
        name: 'Anomaly',
        redirect: '/anomaly/list',
        meta: { title: '异常管理', icon: 'Warning' },
        children: [
          {
            path: 'list',
            name: 'AnomalyList',
            component: () => import('@/views/anomaly/List.vue'),
            meta: { title: '异常记录' }
          },
          {
            path: 'workorder',
            name: 'WorkOrder',
            component: () => import('@/views/anomaly/WorkOrder.vue'),
            meta: { title: '工单管理' }
          }
        ]
      },
      {
        path: 'user',
        name: 'User',
        redirect: '/user/list',
        meta: { title: '用户管理', icon: 'User' },
        children: [
          {
            path: 'list',
            name: 'UserList',
            component: () => import('@/views/user/List.vue'),
            meta: { title: '用户列表' }
          }
        ]
      },
      {
        path: 'report',
        name: 'Report',
        component: () => import('@/views/report/Index.vue'),
        meta: { title: '统计报表', icon: 'Document' }
      }
    ]
  },
  { path: '/:pathMatch(.*)*', redirect: '/' }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach(async (to) => {
  NProgress.start()
  document.title = to.meta.title
    ? `${to.meta.title} - 水慧云`
    : '水慧云 · 智慧水务服务平台'

  if (to.path === '/login') return true
  if (!localStorage.getItem('token')) { clearSession(); return '/login' }
  try {
    const response = await authApi.me()
    const user = response.data
    if (!['admin', 'user'].includes(user?.role)) { clearSession(); return '/login' }
    localStorage.setItem('userInfo', JSON.stringify(user))
    return routeForRole(to.path, user.role) || true
  } catch (error) {
    if (error.response?.status === 401) clearSession()
    return '/login'
  }
})

router.afterEach(() => {
  NProgress.done()
})
router.onError(() => NProgress.done())

export default router
