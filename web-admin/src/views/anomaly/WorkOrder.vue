<template>
  <div class="wo">
    <header class="panel intro">
      <div>
        <h3>工单管理</h3>
        <p class="sub">异常处置工单流转 · 派单 / 接单 / 结案，数据来自 work_order 表</p>
      </div>
      <el-button @click="load" :loading="loading">刷新</el-button>
    </header>

    <section class="kpi-row">
      <article class="panel kpi">
        <em>全部工单</em>
        <strong>{{ stats.total }}</strong>
      </article>
      <article class="panel kpi tone-warn">
        <em>待派 / 已派</em>
        <strong>{{ stats.pending }}</strong>
      </article>
      <article class="panel kpi tone-info">
        <em>处理中</em>
        <strong>{{ stats.processing }}</strong>
      </article>
      <article class="panel kpi tone-ok">
        <em>已完成 / 关闭</em>
        <strong>{{ stats.done }}</strong>
      </article>
    </section>

    <el-alert v-if="error" type="error" :title="error" show-icon style="margin-bottom:12px" />

    <div class="table-page">
      <div class="search-bar">
        <el-form :inline="true" :model="searchForm">
          <el-form-item label="工单号">
            <el-input v-model="searchForm.orderNo" placeholder="工单编号" clearable />
          </el-form-item>
          <el-form-item label="水表编号">
            <el-input v-model="searchForm.meterNo" placeholder="水表编号" clearable />
          </el-form-item>
          <el-form-item label="类型">
            <el-select v-model="searchForm.orderType" placeholder="全部" clearable>
              <el-option label="紧急" value="emergency" />
              <el-option label="加急" value="urgent" />
              <el-option label="普通" value="normal" />
            </el-select>
          </el-form-item>
          <el-form-item label="优先级">
            <el-select v-model="searchForm.priority" placeholder="全部" clearable>
              <el-option label="紧急" :value="1" />
              <el-option label="高" :value="2" />
              <el-option label="普通" :value="3" />
            </el-select>
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="searchForm.status" placeholder="全部" clearable>
              <el-option label="待派单" :value="0" />
              <el-option label="已派单" :value="1" />
              <el-option label="处理中" :value="2" />
              <el-option label="已完成" :value="3" />
              <el-option label="已关闭" :value="4" />
            </el-select>
          </el-form-item>
          <el-form-item label="处理人">
            <el-input v-model="searchForm.handlerName" placeholder="处理人" clearable />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="handleSearch">查询</el-button>
            <el-button @click="handleReset">重置</el-button>
          </el-form-item>
        </el-form>
      </div>

      <div class="table-container">
        <el-table :data="tableData" stripe v-loading="loading" style="width:100%">
          <el-table-column prop="orderNo" label="工单号" min-width="150" fixed />
          <el-table-column prop="title" label="标题" min-width="140" show-overflow-tooltip />
          <el-table-column prop="orderType" label="类型" width="90">
            <template #default="{ row }">
              <el-tag :type="orderTypeTag(row.orderType)" size="small">{{ orderTypeLabel(row.orderType) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="priority" label="优先级" width="90">
            <template #default="{ row }">
              <el-tag :type="priorityTag(row.priority)" effect="dark" size="small">{{ priorityLabel(row.priority) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="meterNo" label="水表" width="130" />
          <el-table-column prop="areaName" label="区域" width="110" show-overflow-tooltip />
          <el-table-column prop="handlerName" label="处理人" width="100">
            <template #default="{ row }">{{ row.handlerName || '—' }}</template>
          </el-table-column>
          <el-table-column prop="status" label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="statusTag(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="createTime" label="创建时间" width="170">
            <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="220" fixed="right">
            <template #default="{ row }">
              <el-button type="primary" link @click="openDetail(row)">详情</el-button>
              <el-button v-if="row.status === 0 || row.status === 1" type="warning" link @click="openDispatch(row)">派单</el-button>
              <el-button v-if="row.status === 0 || row.status === 1" type="success" link @click="doAccept(row)">接单</el-button>
              <el-button v-if="row.status === 2" type="success" link @click="openComplete(row)">完成</el-button>
              <el-button v-if="row.status < 3" type="info" link @click="doClose(row)">关闭</el-button>
            </template>
          </el-table-column>
        </el-table>

        <div class="pager">
          <el-pagination
            v-model:current-page="pagination.pageNum"
            v-model:page-size="pagination.pageSize"
            :page-sizes="[10, 20, 50]"
            :total="pagination.total"
            layout="total, sizes, prev, pager, next"
            @size-change="load"
            @current-change="load"
          />
        </div>
      </div>
    </div>

    <el-drawer v-model="detailVisible" title="工单详情" size="420px">
      <template v-if="current">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="工单号">{{ current.orderNo }}</el-descriptions-item>
          <el-descriptions-item label="标题">{{ current.title }}</el-descriptions-item>
          <el-descriptions-item label="类型">{{ orderTypeLabel(current.orderType) }}</el-descriptions-item>
          <el-descriptions-item label="优先级">{{ priorityLabel(current.priority) }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ statusLabel(current.status) }}</el-descriptions-item>
          <el-descriptions-item label="水表">{{ current.meterNo || '—' }}</el-descriptions-item>
          <el-descriptions-item label="区域">{{ current.areaName || '—' }}</el-descriptions-item>
          <el-descriptions-item label="用户">{{ current.userName || '—' }}</el-descriptions-item>
          <el-descriptions-item label="异常编号">{{ current.anomalyNo || '—' }}</el-descriptions-item>
          <el-descriptions-item label="处理人">{{ current.handlerName || '—' }}</el-descriptions-item>
          <el-descriptions-item label="地址">{{ current.installAddress || '—' }}</el-descriptions-item>
          <el-descriptions-item label="描述">{{ current.description || '—' }}</el-descriptions-item>
          <el-descriptions-item label="处理结果">{{ current.result || '—' }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ formatTime(current.createTime) }}</el-descriptions-item>
          <el-descriptions-item label="派单时间">{{ formatTime(current.dispatchTime) }}</el-descriptions-item>
          <el-descriptions-item label="接单时间">{{ formatTime(current.acceptTime) }}</el-descriptions-item>
          <el-descriptions-item label="完成时间">{{ formatTime(current.completeTime) }}</el-descriptions-item>
        </el-descriptions>
      </template>
    </el-drawer>

    <el-dialog v-model="dispatchVisible" title="派单" width="420px">
      <el-form label-width="80px">
        <el-form-item label="处理人">
          <el-select v-model="dispatchForm.handlerName" filterable allow-create default-first-option style="width:100%">
            <el-option label="巡检一组" value="巡检一组" />
            <el-option label="巡检二组" value="巡检二组" />
            <el-option label="抢修班" value="抢修班" />
            <el-option label="表务班" value="表务班" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dispatchVisible = false">取消</el-button>
        <el-button type="primary" :loading="acting" @click="doDispatch">确认派单</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="completeVisible" title="完成工单" width="460px">
      <el-form label-width="80px">
        <el-form-item label="处理结果">
          <el-input v-model="completeForm.result" type="textarea" :rows="4" placeholder="填写现场处置结果" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="completeVisible = false">取消</el-button>
        <el-button type="primary" :loading="acting" @click="doComplete">确认完成</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { workOrderApi } from '@/api'

const loading = ref(false)
const acting = ref(false)
const error = ref('')
const tableData = ref([])
const stats = reactive({ total: 0, pending: 0, processing: 0, done: 0 })
const searchForm = reactive({
  orderNo: '',
  meterNo: '',
  orderType: '',
  priority: null,
  status: null,
  handlerName: ''
})
const pagination = reactive({ pageNum: 1, pageSize: 10, total: 0 })

const detailVisible = ref(false)
const dispatchVisible = ref(false)
const completeVisible = ref(false)
const current = ref(null)
const dispatchForm = reactive({ handlerName: '巡检一组' })
const completeForm = reactive({ result: '' })

const orderTypeLabel = (t) => ({ emergency: '紧急', urgent: '加急', normal: '普通' }[t] || t || '—')
const orderTypeTag = (t) => ({ emergency: 'danger', urgent: 'warning', normal: 'info' }[t] || 'info')
const priorityLabel = (p) => ({ 1: '紧急', 2: '高', 3: '普通' }[p] || '—')
const priorityTag = (p) => ({ 1: 'danger', 2: 'warning', 3: 'info' }[p] || 'info')
const statusLabel = (s) => ({ 0: '待派单', 1: '已派单', 2: '处理中', 3: '已完成', 4: '已关闭' }[s] || '—')
const statusTag = (s) => ({ 0: 'info', 1: 'warning', 2: '', 3: 'success', 4: 'info' }[s] || 'info')

const formatTime = (v) => {
  if (!v) return '—'
  return String(v).replace('T', ' ').slice(0, 19)
}

const loadStats = async () => {
  const res = await workOrderApi.stats()
  Object.assign(stats, res.data || {})
}

const load = async () => {
  loading.value = true
  error.value = ''
  try {
    const [listRes] = await Promise.all([
      workOrderApi.list({
        pageNum: pagination.pageNum,
        pageSize: pagination.pageSize,
        orderNo: searchForm.orderNo || undefined,
        meterNo: searchForm.meterNo || undefined,
        orderType: searchForm.orderType || undefined,
        priority: searchForm.priority ?? undefined,
        status: searchForm.status ?? undefined,
        handlerName: searchForm.handlerName || undefined
      }),
      loadStats()
    ])
    tableData.value = listRes.data?.records || []
    pagination.total = Number(listRes.data?.total || 0)
  } catch (e) {
    error.value = e.message || '加载失败'
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  pagination.pageNum = 1
  load()
}

const handleReset = () => {
  searchForm.orderNo = ''
  searchForm.meterNo = ''
  searchForm.orderType = ''
  searchForm.priority = null
  searchForm.status = null
  searchForm.handlerName = ''
  handleSearch()
}

const openDetail = async (row) => {
  try {
    const res = await workOrderApi.detail(row.id)
    current.value = res.data
    detailVisible.value = true
  } catch (_) {}
}

const openDispatch = (row) => {
  current.value = row
  dispatchForm.handlerName = row.handlerName || '巡检一组'
  dispatchVisible.value = true
}

const doDispatch = async () => {
  if (!dispatchForm.handlerName) {
    ElMessage.warning('请选择处理人')
    return
  }
  acting.value = true
  try {
    await workOrderApi.dispatch(current.value.id, { handlerName: dispatchForm.handlerName })
    ElMessage.success('派单成功')
    dispatchVisible.value = false
    await load()
  } finally {
    acting.value = false
  }
}

const doAccept = async (row) => {
  await ElMessageBox.confirm(`确认接单「${row.orderNo}」并转入处理中？`, '接单', { type: 'warning' })
  await workOrderApi.accept(row.id)
  ElMessage.success('已转入处理中')
  await load()
}

const openComplete = (row) => {
  current.value = row
  completeForm.result = row.result || ''
  completeVisible.value = true
}

const doComplete = async () => {
  acting.value = true
  try {
    await workOrderApi.complete(current.value.id, { result: completeForm.result })
    ElMessage.success('工单已完成')
    completeVisible.value = false
    await load()
  } finally {
    acting.value = false
  }
}

const doClose = async (row) => {
  await ElMessageBox.confirm(`确认关闭工单「${row.orderNo}」？`, '关闭', { type: 'warning' })
  await workOrderApi.close(row.id, { remark: '人工关闭' })
  ElMessage.success('工单已关闭')
  await load()
}

onMounted(load)
</script>

<style scoped lang="scss">
.wo { display: grid; gap: 14px; }
.intro {
  padding: 16px 18px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  h3 { font-family: var(--font-display); margin: 0 0 6px; }
  .sub { margin: 0; color: var(--color-muted); font-size: 13px; }
}
.kpi-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
}
.kpi {
  padding: 14px 16px;
  em { display: block; font-style: normal; color: var(--color-muted); font-size: 12px; margin-bottom: 6px; }
  strong { font-family: var(--font-display); font-size: 26px; }
  &.tone-warn strong { color: #c4a35a; }
  &.tone-info strong { color: #3d7ea6; }
  &.tone-ok strong { color: #2a9d8f; }
}
.pager {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
@media (max-width: 960px) {
  .kpi-row { grid-template-columns: 1fr 1fr; }
}
</style>
