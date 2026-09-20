import test from 'node:test'
import assert from 'node:assert/strict'

const helpers = await import('./diagnosis.js').catch(() => ({}))

test('evidence JSON tolerates malformed, null and already parsed values', () => {
  assert.equal(typeof helpers.safeJson, 'function')
  assert.deepEqual(helpers.safeJson('{"coverage":0}'), { coverage: 0 })
  assert.deepEqual(helpers.safeJson('{broken'), {})
  assert.deepEqual(helpers.safeJson(null), {})
  assert.deepEqual(helpers.safeJson({ score: 0 }), { score: 0 })
})

test('missing measurement remains unavailable while zero remains a measurement', () => {
  assert.equal(typeof helpers.displayValue, 'function')
  assert.equal(helpers.displayValue(null), '未提供')
  assert.equal(helpers.displayValue(undefined), '未提供')
  assert.equal(helpers.displayValue(0), '0')
  assert.equal(helpers.displayValue(false), '否')
})

test('verification labels distinguish insufficient evidence from recovery', () => {
  assert.equal(typeof helpers.stateLabel, 'function')
  assert.equal(helpers.stateLabel('inconclusive'), '无法判断')
  assert.equal(helpers.stateLabel('recovered'), '观测恢复')
  assert.equal(helpers.stateLabel('persistent'), '仍有异常')
  assert.equal(helpers.stateLabel('future_state'), 'future_state')
})
