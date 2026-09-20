import test from 'node:test'
import assert from 'node:assert/strict'
import * as billing from './billing.js'

test('decimal amounts are converted without floating point rounding', () => {
  assert.equal(typeof billing.toCents, 'function')
  assert.equal(billing.toCents('0.29'), 29n)
  assert.equal(billing.toCents('1000000000.01'), 100000000001n)
  assert.equal(billing.formatMoney(1234.5), '1234.50')
  assert.equal(billing.formatMoney(null), '0.00')
})

test('invalid decimal syntax and precision are rejected', () => {
  assert.equal(typeof billing.toCents, 'function')
  for (const value of ['', '1e2', '-1', '1.001', 'NaN', '1,000', '.5']) {
    assert.throws(() => billing.toCents(value), /金额/)
  }
})

test('remaining amount uses server value when supplied and exact subtraction otherwise', () => {
  assert.equal(typeof billing.remainingAmount, 'function')
  assert.equal(billing.remainingAmount({ totalAmount: '0.30', paidAmount: '0.10' }), '0.20')
  assert.equal(billing.remainingAmount({ totalAmount: '100', paidAmount: '40', remainingAmount: '61' }), '61.00')
  assert.equal(billing.remainingAmount({ totalAmount: '1', paidAmount: '2' }), '0.00')
})

test('payment must be positive and no more than outstanding balance', () => {
  assert.equal(typeof billing.validatePaymentAmount, 'function')
  assert.equal(billing.validatePaymentAmount('0.20', '0.20'), '0.20')
  assert.equal(billing.validatePaymentAmount('40', '100'), '40.00')
  for (const value of ['0', '-1', '100.01', '0.001']) {
    assert.throws(() => billing.validatePaymentAmount(value, '100'), /金额/)
  }
})

test('uncertain attempts reuse request identity and cannot change payload', () => {
  assert.equal(typeof billing.preparePaymentAttempt, 'function')
  const attempt = billing.preparePaymentAttempt(null, { billId: 8, amount: '40', payMethod: 'cash' }, () => 'receipt_1')
  assert.deepEqual(attempt, { billId: 8, amount: '40.00', payMethod: 'cash', tradeNo: 'receipt_1' })
  assert.strictEqual(billing.preparePaymentAttempt(attempt, { billId: 8, amount: '40.0', payMethod: 'cash' }), attempt)
  for (const update of [{ amount: '41' }, { payMethod: 'wechat' }, { billId: 9 }]) {
    assert.throws(() => billing.preparePaymentAttempt(attempt, { billId: 8, amount: '40', payMethod: 'cash', ...update }), /重试/)
  }
})

test('new payment requests reject invalid channels and identities', () => {
  assert.equal(typeof billing.preparePaymentAttempt, 'function')
  assert.throws(() => billing.preparePaymentAttempt(null, { billId: 8, amount: '40', payMethod: 'invalid' }), /渠道/)
  assert.throws(() => billing.preparePaymentAttempt(null, { billId: 8, amount: '40', payMethod: 'cash' }, () => 'bad id'), /标识/)
})

test('expired sessions and uncertain failures preserve a pending payment identity', () => {
  assert.equal(typeof billing.isDefinitivePaymentRejection, 'function')
  for (const error of [{}, { response: { status: 401 } }, { response: { status: 500 } }, { response: { status: 429 } }]) {
    assert.equal(billing.isDefinitivePaymentRejection(error), false)
  }
  for (const status of [400, 403, 404, 409]) {
    assert.equal(billing.isDefinitivePaymentRejection({ response: { status } }), true)
  }
  assert.equal(billing.isDefinitivePaymentRejection({ businessFailure: true }), true)
})
