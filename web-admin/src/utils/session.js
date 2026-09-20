export function readUser(storage = localStorage) {
  try { return JSON.parse(storage.getItem('userInfo') || '{}') || {} }
  catch { return {} }
}

export function clearSession(storage = localStorage) {
  storage.removeItem('token')
  storage.removeItem('userInfo')
}

export function saveSession(data, storage = localStorage) {
  if (typeof data?.token !== 'string' || !data.token.trim() || !['admin', 'user'].includes(data.userInfo?.role)) {
    throw new Error('登录响应无有效会话，请重试')
  }
  storage.setItem('token', data.token)
  storage.setItem('userInfo', JSON.stringify(data.userInfo))
}

export function routeForRole(path, role) {
  if (role === 'user') return path === '/portal' ? null : '/portal'
  if (role === 'admin') return path === '/portal' ? '/dashboard' : null
  return '/login'
}
