import test from 'node:test'
import assert from 'node:assert/strict'
import { clearSession, readUser, saveSession, routeForRole } from './session.js'

const storage = () => {
  const data = new Map()
  return { getItem: key => data.get(key) ?? null, setItem: (key, value) => data.set(key, String(value)), removeItem: key => data.delete(key) }
}

test('ordinary users cannot navigate to administration and unknown roles cannot enter', () => {
  assert.equal(routeForRole('/bill/list', 'user'), '/portal')
  assert.equal(routeForRole('/portal', 'user'), null)
  assert.equal(routeForRole('/portal', 'admin'), '/dashboard')
  assert.equal(routeForRole('/feedback', 'admin'), null)
  assert.equal(routeForRole('/dashboard', 'unknown'), '/login')
})

test('session requires a real token and known role; malformed cached user is safe', () => {
  const cache = storage()
  assert.throws(() => saveSession({ token: undefined, userInfo: { role: 'admin' } }, cache))
  assert.throws(() => saveSession({ token: 'token', userInfo: { role: 'other' } }, cache))
  cache.setItem('userInfo', '{broken')
  assert.deepEqual(readUser(cache), {})
  saveSession({ token: 'test-token', userInfo: { id: 5, role: 'user' } }, cache)
  assert.equal(readUser(cache).id, 5)
  clearSession(cache)
  assert.equal(cache.getItem('token'), null)
  assert.deepEqual(readUser(cache), {})
})
