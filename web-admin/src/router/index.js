import { createRouter, createWebHistory } from 'vue-router'
import NProgress from 'nprogress'
import 'nprogress/nprogress.css'

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
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to, from, next) => {
  NProgress.start()
  document.title = to.meta.title
    ? `${to.meta.title} - 水务AI智能运营决策平台`
    : '水务AI智能运营决策平台'

  const token = localStorage.getItem('token')
  if (to.path !== '/login' && !token) {
    next('/login')
  } else {
    next()
  }
})

router.afterEach(() => {
  NProgress.done()
})

export default router
