<template>
  <div class="feedback-page">
    <header><div><h2>用户反馈</h2><p>查看用水问题，回复用户并更新处理状态。</p></div><el-button :loading="loading" @click="load">刷新</el-button></header>
    <section class="table-page">
      <el-form :inline="true" @submit.prevent>
        <el-form-item label="处理状态"><el-select v-model="filter" style="width:150px"><el-option label="全部" value="all" /><el-option v-for="item in statuses" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item>
      </el-form>
      <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" />
      <el-table v-loading="loading" :data="visibleRows" stripe empty-text="暂无符合条件的反馈">
        <el-table-column prop="id" label="编号" width="80" />
        <el-table-column prop="username" label="用户" min-width="120" />
        <el-table-column label="水表ID" width="100"><template #default="{row}">{{ row.meterId ?? '未关联' }}</template></el-table-column>
        <el-table-column prop="subject" label="反馈主题" min-width="200" show-overflow-tooltip />
        <el-table-column prop="content" label="内容" min-width="230" show-overflow-tooltip />
        <el-table-column label="状态" width="110"><template #default="{row}"><el-tag :type="statusTag(row.status)">{{ statusLabel(row.status) }}</el-tag></template></el-table-column>
        <el-table-column label="提交时间" width="180"><template #default="{row}">{{ dateTime(row.createdAt) }}</template></el-table-column>
        <el-table-column label="操作" fixed="right" width="110"><template #default="{row}"><el-button type="primary" link @click="open(row)">查看 / 回复</el-button></template></el-table-column>
      </el-table>
    </section>
    <el-dialog v-model="dialogOpen" title="反馈处理" width="min(680px,94vw)" :close-on-click-modal="false" :show-close="!saving" :close-on-press-escape="!saving">
      <template v-if="selected">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="用户">{{ selected.username }}</el-descriptions-item>
          <el-descriptions-item label="关联水表">{{ selected.meterId ?? '未关联' }}</el-descriptions-item>
          <el-descriptions-item label="提交时间">{{ dateTime(selected.createdAt) }}</el-descriptions-item>
          <el-descriptions-item label="最近回复">{{ dateTime(selected.repliedAt) }}</el-descriptions-item>
          <el-descriptions-item label="主题" :span="2">{{ selected.subject }}</el-descriptions-item>
          <el-descriptions-item label="问题描述" :span="2"><div class="content-text">{{ selected.content }}</div></el-descriptions-item>
        </el-descriptions>
        <el-form ref="formRef" :model="form" :rules="rules" label-position="top" class="reply-form" @submit.prevent="save">
          <el-form-item label="回复内容" prop="reply"><el-input v-model="form.reply" :disabled="saving" type="textarea" :rows="5" maxlength="2000" show-word-limit placeholder="说明核查结果与处理安排" /></el-form-item>
          <el-form-item label="处理状态" prop="status"><el-radio-group v-model="form.status" :disabled="saving"><el-radio label="processing">处理中</el-radio><el-radio label="resolved">已解决</el-radio></el-radio-group></el-form-item>
        </el-form>
        <el-alert v-if="saveError" :title="saveError" type="error" show-icon :closable="false" />
      </template>
      <template #footer><el-button :disabled="saving" @click="dialogOpen=false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存回复</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import dayjs from 'dayjs'
import { ElMessage } from 'element-plus'
import { feedbackApi } from '@/api/index.js'
import { useNotificationTarget } from '@/utils/notificationTarget.js'
const rows = ref([]), loading = ref(false), error = ref(''), filter = ref('all')
const dialogOpen = ref(false), selected = ref(null), saving = ref(false), saveError = ref(''), formRef = ref()
const form = reactive({ reply: '', status: 'processing' })
const statuses = [{ value: 'pending', label: '待处理' }, { value: 'processing', label: '处理中' }, { value: 'resolved', label: '已解决' }]
const rules = { reply: [{ required: true, whitespace: true, message: '请输入回复内容', trigger: 'blur' }, { max: 2000, message: '回复最多2000字', trigger: 'blur' }] }
const visibleRows = computed(() => filter.value === 'all' ? rows.value : rows.value.filter(row => row.status === filter.value))
const statusLabel = value => statuses.find(item => item.value === value)?.label || '待处理'
const statusTag = value => ({ pending: 'info', processing: 'warning', resolved: 'success' })[value] || 'info'
const dateTime = value => value ? dayjs(value).format('YYYY-MM-DD HH:mm:ss') : '—'
let version = 0
async function load() {
  const current = ++version
  loading.value = true; error.value = ''
  try {
    const response = await feedbackApi.list()
    if (current !== version) return
    if (!Array.isArray(response.data)) throw new Error('反馈数据格式异常')
    rows.value = response.data
  } catch (e) { if (current === version) { error.value = e.message || '反馈加载失败'; rows.value = [] } }
  finally { if (current === version) loading.value = false }
}
async function open(row) {
  selected.value = row; saveError.value = ''
  Object.assign(form, { reply: row.reply || '', status: row.status === 'resolved' ? 'resolved' : 'processing' })
  dialogOpen.value = true
  await nextTick()
  formRef.value?.clearValidate()
}
async function save() {
  if (saving.value || !selected.value || !await formRef.value.validate().catch(() => false)) return
  saving.value = true; saveError.value = ''
  try {
    await feedbackApi.reply(selected.value.id, { reply: form.reply.trim(), status: form.status })
    ElMessage.success('回复已保存'); dialogOpen.value = false
    await load()
  } catch (e) { saveError.value = e.message || '保存失败，请重试' }
  finally { saving.value = false }
}
onMounted(load)
useNotificationTarget(async ({id})=>{const response=await feedbackApi.detail(id);await open(response.data)})
</script>

<style scoped>
.feedback-page{display:grid;grid-template-columns:minmax(0,1fr);gap:20px;min-width:0}.feedback-page header{display:flex;justify-content:space-between;align-items:center;gap:16px}.feedback-page h2{margin:0 0 8px}.feedback-page header p{margin:0;color:var(--el-text-color-secondary)}.table-page{min-width:0}.table-page>.el-alert{margin-bottom:16px}.reply-form{margin-top:22px}.content-text{white-space:pre-wrap;overflow-wrap:anywhere}
</style>
