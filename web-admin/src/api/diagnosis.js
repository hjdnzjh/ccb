import { request } from './index.js'

const base = '/diagnosis'
export const diagnosisApi = {
  cases: params => request.get(`${base}/cases`, { params }),
  detail: id => request.get(`${base}/cases/${id}`),
  policy: () => request.get(`${base}/policy`),
  savePolicy: data => request.put(`${base}/policy`, data),
  meters: () => request.get('/automation/meters'),
  probe: (id, data) => request.post(`${base}/cases/${id}/probe`, data),
  cancelProbes: (id, data) => request.post(`${base}/cases/${id}/cancel-probes`, data),
  review: (id, data) => request.post(`${base}/cases/${id}/review`, data),
  workOrder: (id, data) => request.post(`${base}/cases/${id}/work-order`, data),
  observation: data => request.post(`${base}/observations`, data),
  process: () => request.post(`${base}/process`, {}),
  myCases: params => request.get('/me/diagnosis/cases', { params, silentError: true }),
  myDetail: id => request.get(`/me/diagnosis/cases/${id}`),
  verifications: id => request.get(`/work-order/${id}/verification`),
  followup: (id, data) => request.post(`/work-order/${id}/verification/review`, data)
}

export const replayApi = {
  list: () => request.get('/lab/replays'),
  create: data => request.post('/lab/replays', data),
  detail: id => request.get(`/lab/replays/${id}`),
  control: (id, action) => request.post(`/lab/replays/${id}/control`, { action }),
  report: id => request.get(`/lab/replays/${id}/report`)
}
