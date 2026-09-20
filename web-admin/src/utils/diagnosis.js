export function safeJson(value, fallback = {}) {
  if (value && typeof value === 'object') return value
  try { return JSON.parse(value) ?? fallback } catch { return fallback }
}

export function displayValue(value) {
  if (value === null || value === undefined || value === '') return '未提供'
  if (typeof value === 'boolean') return value ? '是' : '否'
  return typeof value === 'object' ? JSON.stringify(value) : String(value)
}

export const states = {
  candidate: '待补充证据', probing: '复测中', needs_review: '待人工核查',
  work_order_linked: '已关联工单', awaiting_verification: '效果待观察',
  closed: '已结束', not_persistent: '未持续出现', pending: '待执行',
  running: '执行中', success: '成功', retry: '等待重试', failed: '失败',
  cancelled: '已取消', observing: '效果待观察', recovered: '观测恢复',
  persistent: '仍有异常', inconclusive: '无法判断', paused: '已暂停',
  completed: '已完成', queued: '排队中', confirmed_anomaly: '人工确认异常',
  normal_business: '正常经营用水', device_fault: '设备故障', insufficient_data: '证据不足'
}
export const stateLabel = value => states[value] || value || '未提供'
export const stateTone = value => ['recovered', 'success', 'completed'].includes(value) ? 'success'
  : ['failed', 'persistent', 'device_fault'].includes(value) ? 'danger'
    : ['needs_review', 'inconclusive', 'insufficient_data'].includes(value) ? 'warning' : 'info'
export const newRequestKey = () => globalThis.crypto?.randomUUID?.() || `diag-${Date.now()}-${Math.random().toString(36).slice(2)}`
export const prettyJson = value => JSON.stringify(safeJson(value, value), null, 2)
