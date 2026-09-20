export function toCents(value) {
  const text = String(value ?? '0').trim()
  if (!/^\d+(\.\d{1,2})?$/.test(text)) throw new Error('金额须为非负数字，最多两位小数')
  const [whole, fraction = ''] = text.split('.')
  return BigInt(whole) * 100n + BigInt(fraction.padEnd(2, '0'))
}

function centsToDecimal(cents) {
  return `${cents / 100n}.${String(cents % 100n).padStart(2, '0')}`
}

export const formatMoney = value => centsToDecimal(toCents(value))

export function remainingAmount(bill) {
  if (bill.remainingAmount != null) return formatMoney(bill.remainingAmount)
  if (bill.totalAmount == null) throw new Error('账单金额缺失，请核对账单')
  const remaining = toCents(bill.totalAmount) - toCents(bill.paidAmount)
  return centsToDecimal(remaining > 0n ? remaining : 0n)
}

export function validatePaymentAmount(value, remaining) {
  const cents = toCents(value)
  if (cents <= 0n) throw new Error('本次金额必须大于零')
  if (cents > toCents(remaining)) throw new Error('本次金额不能超过剩余应付')
  return centsToDecimal(cents)
}

export function preparePaymentAttempt(previous, payload, createId = () => `receipt_${crypto.randomUUID()}`) {
  const amount = formatMoney(payload.amount)
  if (!['wechat', 'alipay', 'bank', 'cash'].includes(payload.payMethod)) throw new Error('无效收款渠道')
  if (previous) {
    if (String(previous.billId) !== String(payload.billId) || previous.amount !== amount || previous.payMethod !== payload.payMethod) {
      throw new Error('待确认请求只能以原金额和渠道重试')
    }
    return previous
  }
  const tradeNo = createId()
  if (!/^[A-Za-z0-9_:-]{1,100}$/.test(tradeNo)) throw new Error('无效请求标识')
  return Object.freeze({ billId: payload.billId, amount, payMethod: payload.payMethod, tradeNo })
}

export const statusLabel = status => ({ 0: '未支付', 1: '已支付', 2: '已逾期', 3: '部分支付' })[status] || '未知'
export const statusTag = status => ({ 0: 'info', 1: 'success', 2: 'danger', 3: 'warning' })[status] || 'info'
export const channelLabel = channel => ({ wechat: '微信', alipay: '支付宝', bank: '银行', cash: '现金' })[channel] || channel || '—'

// An expired session says nothing about whether an earlier payment was committed.
export function isDefinitivePaymentRejection(error) {
  if (error.response?.status === 401) return false
  return error.businessFailure === true || [400, 403, 404, 409].includes(error.response?.status)
}
