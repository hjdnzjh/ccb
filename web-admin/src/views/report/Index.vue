<template>
  <div class="table-page reports">
    <div class="search-bar">
      <h2>智能统计报表</h2>
      <p class="muted">从审核抄表、收款流水与设备异常生成可复核快照。中文指令采用确定性解析。</p>
      <el-tabs v-model="mode">
        <el-tab-pane label="选择范围" name="structured">
          <el-form :inline="true" @submit.prevent="generate">
            <el-form-item label="日期范围">
              <el-date-picker v-model="dates" type="daterange" value-format="YYYY-MM-DD" start-placeholder="开始日期" end-placeholder="结束日期" />
            </el-form-item>
            <el-form-item label="统计粒度">
              <el-select v-model="granularity" style="width: 130px">
                <el-option v-for="(label, value) in grains" :key="value" :label="label" :value="value" />
              </el-select>
            </el-form-item>
          </el-form>
        </el-tab-pane>
        <el-tab-pane label="中文指令" name="command">
          <el-input v-model="command" maxlength="200" placeholder="生成本月按日报表" @keyup.enter="generate" />
          <p class="muted">支持本周、上周、本月、上月、本年、去年及明确日期范围；可按日、周、月、年分组。例：2026-08-01至2026-08-31按周报表。不支持的过滤条件会明确报错。</p>
        </el-tab-pane>
      </el-tabs>
      <div class="actions">
        <el-button type="primary" :loading="loading" @click="generate">生成报表快照</el-button>
        <el-button :disabled="!report || loading" :loading="downloading === 'xlsx'" @click="download('xlsx')">下载 XLSX</el-button>
        <el-button :disabled="!report || loading" :loading="downloading === 'pdf'" @click="download('pdf')">下载 PDF</el-button>
      </div>
      <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon class="report-error" />
    </div>
    <template v-if="report">
      <div class="snapshot-meta">
        <strong>{{ report.range.startDate }} 至 {{ report.range.endDate }} · {{ grains[report.range.granularity] }}</strong>
        <span>快照生成于 {{ report.generatedAt.replace('T', ' ') }}（{{ report.timezone }}）</span>
        <small>编号 {{ report.id }} · 更改筛选后需重新生成，下载始终对应当前快照</small>
      </div>
      <div class="stats">
        <div class="stat-card"><div class="stat-label">确认用水量</div><div class="stat-value">{{ number(report.totals.waterUsage) }} <small>m³</small></div><p>{{ report.totals.readingCount }} 条确认抄表</p></div>
        <div class="stat-card"><div class="stat-label">登记收款收入</div><div class="stat-value">¥ {{ number(report.totals.revenue) }}</div><p>{{ report.totals.paymentCount }} 笔实际登记流水</p></div>
        <div class="stat-card"><div class="stat-label">累计建档设备故障率</div><div class="stat-value">{{ rate(report.totals.faultRate) }}</div><p>{{ report.totals.faultMeters }} 台故障 / {{ report.totals.meterBase }} 台设备</p></div>
      </div>
      <el-alert v-if="!report.totals.readingCount && !report.totals.paymentCount && !report.totals.faultMeters" title="所选范围没有确认抄表、收款或设备故障记录；用水量和收入为0，设备基数仍按建档时间统计。" type="info" :closable="false" show-icon />
      <div class="chart-card"><div ref="chartRef" class="chart"></div></div>
      <el-table :data="report.rows" stripe border max-height="520">
        <el-table-column prop="startDate" label="开始日期" min-width="115" />
        <el-table-column prop="endDate" label="结束日期" min-width="115" />
        <el-table-column label="用水量(m³)" min-width="125" align="right"><template #default="{ row }">{{ number(row.metrics.waterUsage) }}</template></el-table-column>
        <el-table-column label="收入(元)" min-width="120" align="right"><template #default="{ row }">{{ number(row.metrics.revenue) }}</template></el-table-column>
        <el-table-column prop="metrics.faultMeters" label="故障水表数" min-width="110" align="right" />
        <el-table-column prop="metrics.meterBase" label="设备基数" min-width="100" align="right" />
        <el-table-column label="故障率" min-width="95" align="right"><template #default="{ row }">{{ rate(row.metrics.faultRate) }}</template></el-table-column>
        <el-table-column prop="metrics.readingCount" label="确认抄表数" min-width="110" align="right" />
        <el-table-column prop="metrics.paymentCount" label="收款笔数" min-width="100" align="right" />
      </el-table>
      <el-card class="definitions" shadow="never">
        <template #header><strong>统计口径与历史数据边界</strong></template>
        <p v-for="definition in report.definitions" :key="definition">{{ definition }}</p>
      </el-card>
    </template>
    <el-empty v-else description="选择范围或输入中文指令，生成数据库报表" />
  </div>
</template>

<script setup>
import { ref, nextTick, onBeforeUnmount } from 'vue'
import * as echarts from 'echarts'
import { request } from '@/api'

const today = new Date()
const localDate = value => `${value.getFullYear()}-${String(value.getMonth() + 1).padStart(2, '0')}-${String(value.getDate()).padStart(2, '0')}`
const dates = ref([localDate(new Date(today.getFullYear(), today.getMonth(), 1)), localDate(today)])
const grains = { DAY: '日报', WEEK: '周报', MONTH: '月报', YEAR: '年报' }
const granularity = ref('DAY')
const mode = ref('structured')
const command = ref('生成本月按日报表')
const report = ref(null)
const loading = ref(false)
const downloading = ref('')
const error = ref('')
const chartRef = ref(null)
let chart
let resizeObserver
const number = value => Number(value || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
const rate = value => value == null ? '不适用' : `${number(value)}%`

async function messageOf(err) {
  if (err.response?.data instanceof Blob) {
    try { return JSON.parse(await err.response.data.text()).message || '下载失败，请重试' } catch { return '下载失败，请重试' }
  }
  return err.response?.data?.message || err.message || '请求失败，请稍后重试'
}

async function generate() {
  if (loading.value) return
  error.value = ''
  if (mode.value === 'structured' && dates.value?.length !== 2) { error.value = '请选择完整日期范围'; return }
  if (mode.value === 'command' && !command.value.trim()) { error.value = '请输入中文报表指令'; return }
  loading.value = true
  try {
    const payload = mode.value === 'command' ? { command: command.value.trim() } : { startDate: dates.value[0], endDate: dates.value[1], granularity: granularity.value }
    const result = await request.post('/reports', payload)
    report.value = result.data
    await nextTick()
    if (!chart) {
      chart = echarts.init(chartRef.value)
      resizeObserver = new ResizeObserver(() => chart?.resize())
      resizeObserver.observe(chartRef.value)
    }
    chart.setOption({
      tooltip: { trigger: 'axis' }, legend: { data: ['用水量', '收入'] },
      grid: { left: 65, right: 75, bottom: 65, top: 45 },
      xAxis: { type: 'category', data: report.value.rows.map(row => row.startDate) },
      yAxis: [{ type: 'value', name: '立方米' }, { type: 'value', name: '元' }],
      dataZoom: [{ type: 'inside' }, { type: 'slider', height: 18, bottom: 5 }],
      series: [
        { name: '用水量', type: 'bar', data: report.value.rows.map(row => row.metrics.waterUsage), itemStyle: { color: '#2586a8' } },
        { name: '收入', type: 'line', yAxisIndex: 1, data: report.value.rows.map(row => row.metrics.revenue), itemStyle: { color: '#47a06a' } }
      ]
    }, true)
  } catch (err) { error.value = await messageOf(err) }
  finally { loading.value = false }
}

async function download(format) {
  if (!report.value || downloading.value) return
  downloading.value = format
  error.value = ''
  try {
    const id = report.value.id
    const blob = await request.get(`/reports/${id}/export`, { params: { format }, responseType: 'blob' })
    if (!(blob instanceof Blob)) throw new Error('下载响应格式不正确')
    if (blob.type.includes('json')) { const body = JSON.parse(await blob.text()); throw new Error(body.message || '导出失败') }
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `water-report-${id}.${format}`
    document.body.appendChild(link)
    link.click()
    link.remove()
    setTimeout(() => URL.revokeObjectURL(url), 1000)
  } catch (err) { error.value = await messageOf(err) }
  finally { downloading.value = '' }
}

onBeforeUnmount(() => { resizeObserver?.disconnect(); chart?.dispose() })
</script>

<style scoped>
.reports h2 { margin: 0 0 8px; }
.muted, .snapshot-meta span, .snapshot-meta small { color: #667788; }
.muted { line-height: 1.65; }
.actions { display: flex; flex-wrap: wrap; gap: 8px; }
.actions .el-button + .el-button { margin-left: 0; }
.report-error, .definitions { margin-top: 16px; }
.snapshot-meta { display: flex; flex-direction: column; gap: 8px; margin: 20px 0; overflow-wrap: anywhere; }
.stats { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 18px; margin-bottom: 20px; }
.stat-value { font-size: 29px; overflow-wrap: anywhere; }
.stat-value small { font-size: 16px; }
.stat-card p { color: #667788; font-size: 13px; }
.chart-card { background: white; padding: 16px; margin: 18px 0; border-radius: 8px; }
.chart { width: 100%; height: 320px; }
.definitions p { line-height: 1.8; margin: 6px 0; }
@media (max-width: 900px) { .stats { grid-template-columns: 1fr; } }
</style>
