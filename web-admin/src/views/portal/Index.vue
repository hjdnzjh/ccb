<template>
  <div class="portal-page" v-loading="loading">
    <header class="page-heading">
      <div><h2>我的用水工作台</h2><p>{{ overview?.user?.realName || overview?.user?.username || '欢迎' }}，在这里查看用水、账单和反馈进度。</p></div>
      <el-button :loading="loading" @click="load">刷新</el-button>
    </header>
    <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon />
    <template v-if="overview">
      <section class="summary-grid">
        <article><span>我的水表</span><strong>{{ overview.meters.length }} <small>块</small></strong></article>
        <article><span>待缴账单</span><strong>{{ unpaidBills.length }} <small>笔</small></strong></article>
        <article><span>剩余应付</span><strong>¥{{ outstanding }}</strong></article>
        <article><span>未处理完成的反馈</span><strong>{{ overview.feedback.filter(item => item.status !== 'resolved').length }} <small>条</small></strong></article>
      </section>

      <section class="panel-section">
        <h3>缴费提醒</h3>
        <div v-if="overview.reminders.length" class="reminders">
          <el-alert v-for="item in overview.reminders" :key="item.billId" :type="item.overdue ? 'warning' : 'info'" :closable="false" show-icon>
            <template #title>{{ item.overdue ? '已到期欠款' : '待缴账单' }} · {{ item.billNo }}</template>
            <p>应缴日期：{{ date(item.dueDate) }}；剩余应付 ¥{{ formatMoney(item.remainingAmount) }}
              <el-button link type="primary" @click="openPayment(item.billId)">演示缴费</el-button>
            </p>
          </el-alert>
        </div>
        <el-empty v-else description="暂无待缴提醒" :image-size="56" />
      </section>

      <section class="panel-section">
        <h3>我的账单</h3>
        <el-alert title="演示缴费仅登记系统账务，不会从微信、支付宝或银行卡扣款。" type="info" :closable="false" show-icon />
        <el-table :data="overview.bills" stripe empty-text="暂无账单">
          <el-table-column prop="billNo" label="账单号" min-width="210" show-overflow-tooltip />
          <el-table-column prop="billPeriod" label="账期" width="100" />
          <el-table-column label="用水量" width="100"><template #default="{row}">{{ row.usageAmount ?? '—' }} 吨</template></el-table-column>
          <el-table-column label="应付" width="110"><template #default="{row}">¥{{ formatMoney(row.totalAmount) }}</template></el-table-column>
          <el-table-column label="已付" width="110"><template #default="{row}">¥{{ formatMoney(row.paidAmount) }}</template></el-table-column>
          <el-table-column label="剩余应付" width="120"><template #default="{row}">¥{{ remainingAmount(row) }}</template></el-table-column>
          <el-table-column label="状态" width="100"><template #default="{row}"><el-tag :type="statusTag(row.status)">{{ statusLabel(row.status) }}</el-tag></template></el-table-column>
          <el-table-column label="应缴日期" width="115"><template #default="{row}">{{ date(row.dueDate) }}</template></el-table-column>
          <el-table-column label="操作" fixed="right" width="190"><template #default="{row}">
            <el-button link type="primary" @click="openDetail(row.id)">详情 / 流水</el-button>
            <el-button v-if="canPay(row)" link type="success" @click="openPayment(row.id)">{{ canRetry(row) ? '核对缴费' : '演示缴费' }}</el-button>
          </template></el-table-column>
        </el-table>
      </section>

      <section class="panel-section">
        <h3>我的水表</h3>
        <el-table :data="overview.meters" stripe empty-text="尚未关联水表，请联系管理员">
          <el-table-column prop="meterNo" label="水表编号" min-width="190" />
          <el-table-column prop="installAddress" label="安装地址" min-width="220" show-overflow-tooltip />
          <el-table-column label="累计读数" width="130"><template #default="{row}">{{ row.currentReading ?? '—' }} 吨</template></el-table-column>
          <el-table-column label="状态" width="100"><template #default="{row}">{{ meterStatus(row.status) }}</template></el-table-column>
          <el-table-column label="最近抄表" width="175"><template #default="{row}">{{ dateTime(row.lastReadingTime) }}</template></el-table-column>
        </el-table>
      </section>

      <section class="panel-section">
        <h3>抄表与用水记录</h3>
        <el-table :data="overview.readings" stripe empty-text="暂无抄表记录">
          <el-table-column prop="meterNo" label="水表编号" min-width="190" />
          <el-table-column label="抄表时间" min-width="175"><template #default="{row}">{{ dateTime(row.readingTime) }}</template></el-table-column>
          <el-table-column prop="readingPeriod" label="周期" width="110" />
          <el-table-column label="累计读数" width="130"><template #default="{row}">{{ row.readingValue ?? '—' }} 吨</template></el-table-column>
          <el-table-column label="本次用量" width="120"><template #default="{row}">{{ row.usageAmount ?? '—' }} 吨</template></el-table-column>
          <el-table-column label="审核状态" width="115"><template #default="{row}">{{ readingStatus(row.status) }}</template></el-table-column>
        </el-table>
      </section>

      <DiagnosisUpdates />
      <section id="portal-feedback" class="panel-section feedback-section">
        <h3>异常反馈</h3>
        <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent="submitFeedback">
          <el-form-item label="关联水表（可选）">
            <el-select v-model="form.meterId" clearable placeholder="不关联具体水表" style="width:100%">
              <el-option v-for="meter in overview.meters" :key="meter.id" :label="meter.meterNo" :value="meter.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="主题" prop="subject"><el-input v-model="form.subject" maxlength="100" show-word-limit placeholder="例如：本月用水量异常" /></el-form-item>
          <el-form-item label="反馈内容" prop="content"><el-input v-model="form.content" type="textarea" :rows="4" maxlength="2000" show-word-limit placeholder="请描述水表、读数或账单问题" /></el-form-item>
          <el-alert v-if="feedbackError" :title="feedbackError" type="error" :closable="false" show-icon />
          <el-button type="primary" :loading="submitting" @click="submitFeedback">提交反馈</el-button>
        </el-form>
        <h3>反馈进度</h3>
        <el-table :data="overview.feedback" stripe empty-text="暂无反馈">
          <el-table-column type="expand"><template #default="{row}"><div class="feedback-detail"><p><strong>反馈内容：</strong>{{ row.content }}</p><p><strong>管理员回复：</strong>{{ row.reply || '等待管理员处理' }}</p><p v-if="row.repliedAt">回复时间：{{ dateTime(row.repliedAt) }}</p></div></template></el-table-column>
          <el-table-column prop="subject" label="主题" min-width="210" show-overflow-tooltip />
          <el-table-column label="状态" width="100"><template #default="{row}"><el-tag :type="feedbackTag(row.status)">{{ feedbackStatus(row.status) }}</el-tag></template></el-table-column>
          <el-table-column prop="reply" label="管理员回复" min-width="220" show-overflow-tooltip />
          <el-table-column label="提交时间" width="175"><template #default="{row}">{{ dateTime(row.createdAt) }}</template></el-table-column>
        </el-table>
      </section>
    </template>
    <BillPaymentDialog v-model="payOpen" :bill-id="selectedId" :api="meApi" @registered="load" />
    <BillDetailDialog v-model="detailOpen" :bill-id="selectedId" :api="meApi" />
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import dayjs from 'dayjs'
import { ElMessage } from 'element-plus'
import { meApi } from '@/api/index.js'
import { formatMoney, remainingAmount, statusLabel, statusTag, toCents } from '@/utils/billing.js'
import BillPaymentDialog from '@/components/BillPaymentDialog.vue'
import BillDetailDialog from '@/components/BillDetailDialog.vue'
import DiagnosisUpdates from '@/components/DiagnosisUpdates.vue'

const loading = ref(false), error = ref(''), overview = ref(null)
const route=useRoute()
async function focusFeedback(){await nextTick();const id=({'#feedback':'portal-feedback','#diagnosis':'diagnosis'})[route.hash];if(id)document.getElementById(id)?.scrollIntoView({behavior:'smooth',block:'start'})}
watch(()=>route.hash,focusFeedback)
const payOpen = ref(false), detailOpen = ref(false), selectedId = ref(null)
const formRef = ref(), submitting = ref(false), feedbackError = ref('')
const form = reactive({ meterId: null, subject: '', content: '' })
const rules = {
  subject: [{ required: true, whitespace: true, message: '请输入反馈主题', trigger: 'blur' }, { max: 100, message: '主题最多100字', trigger: 'blur' }],
  content: [{ required: true, whitespace: true, message: '请输入反馈内容', trigger: 'blur' }, { max: 2000, message: '内容最多2000字', trigger: 'blur' }]
}
const unpaidBills = computed(() => (overview.value?.bills || []).filter(bill => toCents(remainingAmount(bill)) > 0n))
const outstanding = computed(() => {
  const cents = unpaidBills.value.reduce((sum, bill) => sum + toCents(remainingAmount(bill)), 0n)
  return `${cents / 100n}.${String(cents % 100n).padStart(2, '0')}`
})
const date = value => value ? dayjs(value).format('YYYY-MM-DD') : '—'
const dateTime = value => value ? dayjs(value).format('YYYY-MM-DD HH:mm:ss') : '—'
const meterStatus = status => ({ 0: '正常', 1: '故障', 2: '停用', 3: '更换' })[status] || '未知'
const readingStatus = status => ({ 0: '待审核', 1: '已确认', 2: '异常' })[status] || '未知'
const feedbackStatus = status => ({ pending: '待处理', processing: '处理中', resolved: '已解决' })[status] || '待处理'
const feedbackTag = status => ({ pending: 'info', processing: 'warning', resolved: 'success' })[status] || 'info'
const canRetry = bill => !!sessionStorage.getItem(`water_receipt_pending_${bill.id}`)
const canPay = bill => canRetry(bill) || ([0, 2, 3].includes(bill.status) && toCents(remainingAmount(bill)) > 0n)
function openPayment(id) { selectedId.value = id; payOpen.value = true }
function openDetail(id) { selectedId.value = id; detailOpen.value = true }
let version = 0
async function load() {
  const current = ++version
  loading.value = true; error.value = ''
  try {
    const response = await meApi.overview()
    if (current !== version) return
    if (!response.data?.user) throw new Error('工作台数据不完整，请刷新重试')
    const data = response.data
    for (const key of ['meters', 'readings', 'bills', 'reminders', 'feedback']) {
      if (!Array.isArray(data[key])) throw new Error('工作台数据不完整，请刷新重试')
    }
    data.bills.forEach(bill => { remainingAmount(bill); formatMoney(bill.totalAmount); formatMoney(bill.paidAmount) })
    data.reminders.forEach(item => formatMoney(item.remainingAmount))
    overview.value = data
    await focusFeedback()
  } catch (e) {
    if (current === version) { error.value = e.message || '工作台加载失败，请刷新重试'; overview.value = null }
  } finally { if (current === version) loading.value = false }
}
async function submitFeedback() {
  if (submitting.value || !await formRef.value.validate().catch(() => false)) return
  submitting.value = true; feedbackError.value = ''
  try {
    await meApi.submitFeedback({ meterId: form.meterId || null, subject: form.subject.trim(), content: form.content.trim() })
    ElMessage.success('反馈已提交')
    Object.assign(form, { meterId: null, subject: '', content: '' })
    formRef.value?.clearValidate()
    await load()
  } catch (e) { feedbackError.value = e.message || '反馈提交失败，请重试' }
  finally { submitting.value = false }
}
onMounted(load)
</script>

<style scoped>
.portal-page{display:grid;grid-template-columns:minmax(0,1fr);gap:20px;min-width:0}.page-heading{display:flex;align-items:center;justify-content:space-between;gap:16px}.page-heading h2{margin:0 0 8px}.page-heading p{margin:0;color:var(--el-text-color-secondary)}
.summary-grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:16px}.summary-grid article,.panel-section{background:var(--el-bg-color,#fff);border:1px solid var(--el-border-color-lighter);border-radius:12px;padding:22px;min-width:0}.summary-grid article{display:grid;gap:14px}.summary-grid span{font-size:13px;color:var(--el-text-color-secondary)}.summary-grid strong{font-size:clamp(20px,2vw,28px);color:var(--el-color-primary);overflow-wrap:anywhere}.summary-grid small{font-size:13px;font-weight:normal}.panel-section h3{margin:0 0 18px}.panel-section>.el-alert{margin-bottom:14px}.reminders{display:grid;gap:10px}.reminders p{margin:4px 0}.feedback-section .el-form{max-width:760px;margin-bottom:32px}.feedback-section .el-form>.el-alert{margin-bottom:12px}.feedback-detail{padding:12px 24px;white-space:pre-wrap;overflow-wrap:anywhere}.feedback-detail p{margin:8px 0}@media(max-width:900px){.summary-grid{grid-template-columns:repeat(2,minmax(0,1fr))}.panel-section{padding:16px}}
</style>
