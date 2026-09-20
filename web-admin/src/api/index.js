import axios from 'axios'
import { ElMessage } from 'element-plus'
import { clearSession } from '@/utils/session.js'

export const request = axios.create({
  baseURL: '/api/v1',
  timeout: 30000
})

const attachToken = config => {
  const token = localStorage.getItem('token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
}
const rejectResponse = error => {
  if (error.response?.status === 401) {
    clearSession()
    if (window.location.pathname !== '/login') window.location.replace('/login')
  }
  if (!error.config?.silentError) ElMessage.error(error.response?.data?.message || error.message || '网络错误')
  return Promise.reject(error)
}
request.interceptors.request.use(attachToken)

request.interceptors.response.use(
  response => {
    const res = response.data
    if (res && typeof res.success === 'boolean' && !res.success) {
      ElMessage.error(res.message || '请求失败')
      const error = new Error(res.message || '请求失败')
      error.businessFailure = true
      return Promise.reject(error)
    }
    return res
  },
  rejectResponse
)

const agentRequest = axios.create({
  baseURL: '/agent-api',
  timeout: 60000
})
agentRequest.interceptors.request.use(attachToken)
agentRequest.interceptors.response.use(response => response, rejectResponse)

export const authApi = {
  login: (data) => request.post('/auth/login', data),
  me: () => request.get('/auth/me'),
  logout: () => request.post('/auth/logout')
}

export const meApi = {
  overview: () => request.get('/me/overview'),
  getDetail: (id) => request.get(`/me/bills/${id}`),
  getPayments: (id) => request.get(`/me/bills/${id}/payments`),
  pay: (id, params) => request.post(`/me/bills/${id}/pay`, null, { params }),
  feedback: () => request.get('/me/feedback'),
  submitFeedback: (data) => request.post('/me/feedback', data)
}

export const feedbackApi = {
  list: () => request.get('/feedback'),
  detail: (id) => request.get(`/feedback/${id}`),
  reply: (id, data) => request.post(`/feedback/${id}/reply`, data)
}

export const notificationApi = {
  inbox: () => request.get('/me/notifications', { silentError: true }),
  mark: (keys, read) => request.post('/me/notifications/read', { keys, read }, { silentError: true })
}

export const opsApi = {
  cockpit: () => request.get('/ops/cockpit'),
  twinZones: () => request.get('/ops/twin/zones'),
  zoneDetail: (areaCode) => request.get(`/ops/twin/zones/${areaCode}`),
  twinMap: () => request.get('/ops/twin/map'),
  meterHealth: () => request.get('/ops/meter-health'),
  ocrTrust: (limit = 20) => request.get('/ops/ocr-trust', { params: { limit } }),
  billingInsights: () => request.get('/ops/billing-insights'),
  assistant: (question) => request.post('/ops/assistant', { question }),
  recentReadings: (limit = 50) => request.get('/ops/readings/recent', { params: { limit } })
}

export const meterReadingApi = {
  aiImageReading: (meterId, imageData) =>
    request.post('/meter-reading/ai-image', imageData, { params: { meterId } }),
  remoteReading: (meterId) =>
    request.post(`/meter-reading/remote/${meterId}`),
  batchReading: (areaId, meterCount) =>
    request.post('/meter-reading/batch', null, { params: { areaId, meterCount } }),
  getHistory: (meterId, limit = 10) =>
    request.get(`/meter-reading/history/${meterId}`, { params: { limit } }),
  getPendingReview: (params) =>
    request.get('/meter-reading/pending-review', { params }),
  review: (readingId, params) =>
    request.post(`/meter-reading/review/${readingId}`, null, { params })
}

export const billApi = {
  list: (params) => request.get('/bill/list', { params }),
  generate: (meterId, readingId) =>
    request.post('/bill/generate', null, { params: { meterId, readingId } }),
  getUserBills: (userId) => request.get(`/bill/user/${userId}`),
  getDetail: (billId) => request.get(`/bill/${billId}`),
  getPayments: (billId) => request.get(`/bill/${billId}/payments`),
  pay: (billId, params) => request.post(`/bill/pay/${billId}`, null, { params }),
  getOverdueRank: (limit = 10) => request.get('/bill/overdue-rank', { params: { limit } }),
  getStatistics: (startDate) => request.get('/bill/statistics', { params: { startDate } })
}

export const meterApi = {
  getList: (params) => request.get('/meter/list', { params }),
  getDetail: (id) => request.get(`/meter/${id}`)
}

export const workOrderApi = {
  list: (params) => request.get('/work-order/list', { params }),
  stats: () => request.get('/work-order/stats'),
  detail: (id) => request.get(`/work-order/${id}`),
  dispatch: (id, data) => request.post(`/work-order/${id}/dispatch`, data),
  accept: (id) => request.post(`/work-order/${id}/accept`),
  complete: (id, data) => request.post(`/work-order/${id}/complete`, data || {}),
  close: (id, data) => request.post(`/work-order/${id}/close`, data || {})
}

export const userApi = {
  list: (params) => request.get('/user/list', { params }),
  detail: (id) => request.get(`/user/${id}`),
  create: (data) => request.post('/user', data),
  update: (id, data) => request.put(`/user/${id}`, data),
  meters: (id) => request.get(`/user/${id}/meters`)
}

export const agentApi = {
  getStatus: () => agentRequest.get('/status'),
  getAgents: () => agentRequest.get('/agents'),
  executeAgent: (agentId, context) => agentRequest.post(`/agents/${agentId}/execute`, context)
}

export default request
