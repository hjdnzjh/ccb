<template>
  <el-dialog :model-value="modelValue" title="登记收款" width="min(520px, 94vw)" :close-on-click-modal="false"
    :close-on-press-escape="!submitting" :show-close="!submitting" @update:model-value="close">
    <el-alert title="本页登记线下或演示收款，不会发起微信、支付宝或银行扣款。" type="info" :closable="false" show-icon />
    <div v-loading="loading" class="receipt-form">
      <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon />
      <template v-if="bill">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="账单号">{{ bill.billNo }}</el-descriptions-item>
          <el-descriptions-item label="账单金额">¥{{ formatMoney(bill.totalAmount) }}</el-descriptions-item>
          <el-descriptions-item label="累计已付">¥{{ formatMoney(bill.paidAmount) }}</el-descriptions-item>
          <el-descriptions-item label="剩余应付"><strong>¥{{ balance }}</strong></el-descriptions-item>
        </el-descriptions>
        <el-alert v-if="attempt" title="这次登记尚待确认。金额和渠道已锁定，重试将复用原请求号，不会重复入账。"
          type="warning" :closable="false" show-icon />
        <el-form label-width="90px" @submit.prevent="submit">
          <el-form-item label="本次金额">
            <el-input v-model="amount" inputmode="decimal" :disabled="submitting || !!attempt" placeholder="例如 40.00"><template #prepend>¥</template></el-input>
          </el-form-item>
          <el-form-item label="收款渠道">
            <el-select v-model="payMethod" :disabled="submitting || !!attempt" style="width:100%">
              <el-option v-for="channel in channels" :key="channel" :value="channel" :label="channelLabel(channel) + '（登记）'" />
            </el-select>
          </el-form-item>
          <p v-if="attempt" class="request-id">请求号：{{ attempt.tradeNo }}</p>
        </el-form>
      </template>
    </div>
    <template #footer>
      <el-button :disabled="submitting" @click="close(false)">关闭</el-button>
      <el-button v-if="!bill" :loading="loading" @click="load">重新加载</el-button>
      <el-button type="primary" :loading="submitting" :disabled="loading || !bill" @click="submit">{{ attempt ? '核对并重试原登记' : '确认登记收款' }}</el-button>
    </template>
  </el-dialog>
</template>
<script setup>
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { billApi } from '@/api/index.js'
import { formatMoney, remainingAmount, validatePaymentAmount, preparePaymentAttempt, channelLabel, isDefinitivePaymentRejection } from '@/utils/billing.js'
const props = defineProps({ modelValue: Boolean, billId: [Number, String], api: { type: Object, default: () => billApi } })
const emit = defineEmits(['update:modelValue', 'registered'])
const bill = ref(null), amount = ref(''), payMethod = ref('cash'), error = ref('')
const loading = ref(false), submitting = ref(false), attempt = ref(null)
const channels = ['cash', 'bank', 'wechat', 'alipay']
const balance = computed(() => bill.value ? remainingAmount(bill.value) : '0.00')
const cacheKey = () => `water_receipt_pending_${props.billId}`
let loadVersion = 0
async function load() {
  const version = ++loadVersion
  loading.value = true; bill.value = null; error.value = ''
  try {
    const cached = sessionStorage.getItem(cacheKey())
    attempt.value = cached ? JSON.parse(cached) : null
    const result = await props.api.getDetail(props.billId)
    if (version !== loadVersion) return
    if (result?.id == null) throw new Error('账单不存在')
    remainingAmount(result)
    bill.value = result
    amount.value = attempt.value?.amount ?? remainingAmount(result)
    payMethod.value = attempt.value?.payMethod ?? 'cash'
  } catch (e) { if (version === loadVersion) error.value = e.message || '账单加载失败' }
  finally { if (version === loadVersion) loading.value = false }
}
async function submit() {
  if (submitting.value || loading.value || !bill.value) return
  error.value = ''
  try {
    const paymentAmount = attempt.value ? amount.value : validatePaymentAmount(amount.value, balance.value)
    const request = preparePaymentAttempt(attempt.value, { billId: props.billId, amount: paymentAmount, payMethod: payMethod.value })
    sessionStorage.setItem(cacheKey(), JSON.stringify(request))
    attempt.value = request; submitting.value = true
    const response = await props.api.pay(props.billId, { amount: request.amount, payMethod: request.payMethod, tradeNo: request.tradeNo })
    if (response.success !== true) throw new Error(response.message || '登记结果未确认')
    sessionStorage.removeItem(cacheKey()); attempt.value = null
    ElMessage.success(response.data?.replayed ? '原登记已确认，未重复入账' : '收款登记成功')
    emit('registered'); emit('update:modelValue', false)
  } catch (e) {
    const knownRejection = isDefinitivePaymentRejection(e)
    if (knownRejection) { sessionStorage.removeItem(cacheKey()); attempt.value = null }
    error.value = e.message || '结果未确认，请使用原请求重试'
  } finally { submitting.value = false }
}
function close(value) { if (!submitting.value) emit('update:modelValue', value) }
watch(() => [props.modelValue, props.billId], ([open]) => {
  if (open && props.billId != null) load(); else ++loadVersion
})
</script>
<style scoped>
.receipt-form { min-height:100px; display:grid; gap:16px; margin-top:16px; }
.request-id { color:var(--el-text-color-secondary); font-size:12px; overflow-wrap:anywhere; }
</style>
