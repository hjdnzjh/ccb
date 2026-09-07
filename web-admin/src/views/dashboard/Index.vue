<template>
  <div class="home">
    <header class="hero">
      <div>
        <p class="eyebrow">AquaMind · Home</p>
        <h1>主页</h1>
        <p class="sub">上方为 AI 运营态势，下方为用水 / 收费 / 异常等经营图表与明细。</p>
      </div>
      <div class="hero-actions">
        <el-button class="btn-ai" @click="$router.push('/ai/assistant')">打开水务智能助手</el-button>
        <el-button @click="load" :loading="loading">刷新</el-button>
      </div>
    </header>

    <el-alert v-if="error" type="error" :title="error" show-icon style="margin-bottom:12px" />

    <section class="kpi-row">
      <article v-for="k in kpis" :key="k.key" class="kpi panel" :class="`tone-${k.tone}`">
        <div class="kpi-label">{{ k.label }}</div>
        <div class="kpi-value"><strong>{{ k.value }}</strong><span>{{ k.unit }}</span></div>
        <div class="kpi-hint">{{ k.hint }}</div>
      </article>
    </section>

    <section class="overview-charts">
      <div class="section-title chart-section-title">
        <div>
          <p class="eyebrow">实时经营数据</p>
          <h2>核心趋势一览</h2>
        </div>
        <p>用水、收费、异常与抄表方式优先展示</p>
      </div>

      <div class="chart-grid">
        <div class="panel chart-card chart-card-wide">
          <div class="chart-title">用水趋势分析（近14日）</div>
          <div ref="usageChartRef" class="chart-box" />
        </div>
        <div class="panel chart-card chart-card-wide">
          <div class="chart-title">收费统计</div>
          <div ref="revenueChartRef" class="chart-box" />
        </div>
        <div class="panel chart-card">
          <div class="chart-title">异常类型分布</div>
          <div ref="anomalyChartRef" class="chart-box chart-box-compact" />
        </div>
        <div class="panel chart-card">
          <div class="chart-title">抄表方式占比</div>
          <div ref="readingTypeChartRef" class="chart-box chart-box-compact" />
        </div>
      </div>
    </section>

    <section class="main-grid">
      <div class="panel findings">
        <div class="panel-head">
          <div>
            <h3>AI今日发现</h3>
            <div class="sub">来自 anomaly_record / 低置信度抄表 / 欠费账单</div>
          </div>
          <span class="pill pill-danger">信号 {{ signals.length }}</span>
        </div>
        <div class="findings-scroll">
          <el-empty v-if="!loading && !signals.length" description="暂无开放信号" />
          <article v-for="s in signals" :key="s.id" class="signal" :class="s.level">
            <div class="signal-top">
              <h4>{{ s.title }}</h4>
              <span class="pill" :class="levelPill(s.level)">{{ s.area }}</span>
            </div>
            <div class="signal-body">
              <div><em>发现</em><p>{{ s.find }}</p></div>
              <div><em>判断</em><p>{{ s.judge }}</p></div>
              <div><em>建议</em><p>{{ s.action }}</p></div>
            </div>
          </article>
        </div>
      </div>

      <div class="side-col">
        <div class="panel mini-map">
          <div class="panel-head">
            <h3>分区态势</h3>
            <router-link class="sub link" to="/twin/map">完整地图 →</router-link>
          </div>
          <div class="zone-grid">
            <button v-for="z in zones" :key="z.id" class="zone" :class="z.status" @click="$router.push(`/twin/zone/${z.id}`)">
              <strong>{{ z.name }}</strong>
              <span>{{ statusLabel(z.status) }}</span>
              <small>漏损指数 {{ z.leakRate }}%</small>
            </button>
          </div>
        </div>
        <div class="panel advice">
          <div class="panel-head"><h3>AI运营建议</h3></div>
          <blockquote>{{ advice || '加载中…' }}</blockquote>
          <el-button type="primary" plain style="width:100%" @click="$router.push('/bill/insight')">智能收费策略</el-button>
        </div>
      </div>
    </section>

    <section class="classic">
      <div class="section-title">
        <h2>经营数据看板</h2>
        <p>用水趋势、收费统计、异常分布与最新明细</p>
      </div>

      <div class="stat-row">
        <div class="panel stat-card">
          <div class="stat-header">
            <span>水表总数</span>
            <el-icon color="#088395" :size="22"><Odometer /></el-icon>
          </div>
          <div class="stat-value">{{ Number(classic.totalMeters || 0).toLocaleString() }}</div>
          <div class="stat-label">在线 {{ classic.onlineMeters || 0 }} 台</div>
        </div>
        <div class="panel stat-card">
          <div class="stat-header">
            <span>今日抄表</span>
            <el-icon color="#2a9d8f" :size="22"><Camera /></el-icon>
          </div>
          <div class="stat-value tone-ok">{{ classic.todayReading || 0 }}</div>
          <div class="stat-label">AI识别 {{ classic.aiReading || 0 }} 次</div>
        </div>
        <div class="panel stat-card">
          <div class="stat-header">
            <span>本月收入</span>
            <el-icon color="#c4a35a" :size="22"><Money /></el-icon>
          </div>
          <div class="stat-value tone-gold">¥{{ formatMoney(classic.monthRevenue) }}</div>
          <div class="stat-label">收费率 {{ classic.collectionRate || 0 }}%</div>
        </div>
        <div class="panel stat-card">
          <div class="stat-header">
            <span>异常预警</span>
            <el-icon color="#e07a5f" :size="22"><Warning /></el-icon>
          </div>
          <div class="stat-value tone-danger">{{ classic.anomalyCount || 0 }}</div>
          <div class="stat-label">待处理 {{ classic.pendingAnomaly || 0 }} 条</div>
        </div>
      </div>

      <div class="chart-row">
        <div class="panel chart-card">
          <div class="chart-title">最新抄表记录</div>
          <el-table :data="recentReadings" stripe size="small" style="width:100%">
            <el-table-column prop="meter_no" label="水表编号" min-width="130" />
            <el-table-column prop="reading_value" label="读数" width="100" />
            <el-table-column label="类型" width="100">
              <template #default="{ row }">
                <el-tag size="small" :type="readingTag(row.reading_type)">{{ readingLabel(row.reading_type) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="置信度" width="120">
              <template #default="{ row }">
                <el-progress
                  :percentage="Math.round(Number(row.confidence || 0) * 100)"
                  :color="Number(row.confidence || 0) >= 0.95 ? '#2a9d8f' : '#c4a35a'"
                  :stroke-width="10"
                />
              </template>
            </el-table-column>
            <el-table-column prop="reading_time" label="时间" min-width="150" />
          </el-table>
        </div>
        <div class="panel chart-card">
          <div class="chart-title">待处理工单</div>
          <el-table :data="pendingWorkOrders" stripe size="small" style="width:100%">
            <el-table-column prop="order_no" label="工单号" min-width="140" />
            <el-table-column label="类型" width="90">
              <template #default="{ row }">
                <el-tag size="small" :type="orderTag(row.priority)">{{ orderLabel(row) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="description" label="描述" min-width="140" show-overflow-tooltip />
            <el-table-column prop="create_time" label="创建时间" width="160" />
          </el-table>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import * as echarts from 'echarts'
import { opsApi } from '@/api'

const loading = ref(false)
const error = ref('')
const kpis = ref([])
const signals = ref([])
const zones = ref([])
const advice = ref('')
const classic = ref({})
const recentReadings = ref([])
const pendingWorkOrders = ref([])

const usageChartRef = ref()
const revenueChartRef = ref()
const anomalyChartRef = ref()
const readingTypeChartRef = ref()
const charts = []

const levelPill = (level) => ({ critical: 'pill-danger', warn: 'pill-warn', info: 'pill-info' }[level] || 'pill-info')
const statusLabel = (s) => ({ ok: '正常', warn: '风险', danger: '异常' }[s] || s)
const formatMoney = (n) => Number(n || 0).toLocaleString()

const anomalyNameMap = {
  leak: '漏水', night_surge: '夜间突增', usage_surge: '用量突增', meter_fault: '表故障',
  zero_usage: '零用量', steal: '疑似偷水', low_battery: '低电量'
}
const readingLabel = (t) => ({ ai_image: 'AI抄表', remote: '远程抄表', manual: '人工抄表' }[t] || t || '-')
const readingTag = (t) => ({ ai_image: 'success', remote: 'primary', manual: 'info' }[t] || 'info')
const orderLabel = (row) => {
  if (row.priority === 1 || row.order_type === 'emergency') return '紧急'
  if (row.priority === 2 || row.order_type === 'urgent') return '加急'
  return '普通'
}
const orderTag = (priority) => (priority === 1 ? 'danger' : priority === 2 ? 'warning' : 'info')

const disposeCharts = () => {
  while (charts.length) {
    const c = charts.pop()
    c?.dispose?.()
  }
}

const makeChart = (el) => {
  if (!el) return null
  const c = echarts.init(el)
  charts.push(c)
  return c
}

const renderCharts = (data) => {
  disposeCharts()
  const usage = data.usageTrend || []
  const revenue = data.revenueTrend || []
  const anomaly = data.anomalyDist || []
  const readingType = data.readingTypeDist || []

  const usageChart = makeChart(usageChartRef.value)
  usageChart?.setOption({
    color: ['#088395'],
    tooltip: { trigger: 'axis' },
    grid: { left: 40, right: 16, top: 30, bottom: 28 },
    xAxis: { type: 'category', data: usage.map((x) => x.label) },
    yAxis: { type: 'value', name: '吨' },
    series: [{
      name: '用水量',
      type: 'line',
      smooth: true,
      areaStyle: { opacity: 0.18 },
      data: usage.map((x) => Number(x.usage_amount || 0))
    }]
  })

  const revenueChart = makeChart(revenueChartRef.value)
  revenueChart?.setOption({
    color: ['#088395', '#2a9d8f', '#e07a5f'],
    tooltip: { trigger: 'axis' },
    legend: { data: ['应收', '实收', '欠费'] },
    grid: { left: 48, right: 16, top: 40, bottom: 28 },
    xAxis: { type: 'category', data: revenue.map((x) => x.label) },
    yAxis: { type: 'value', name: '元' },
    series: [
      { name: '应收', type: 'bar', data: revenue.map((x) => Number(x.receivable || 0)) },
      { name: '实收', type: 'bar', data: revenue.map((x) => Number(x.received || 0)) },
      { name: '欠费', type: 'bar', data: revenue.map((x) => Number(x.unpaid || 0)) }
    ]
  })

  const anomalyChart = makeChart(anomalyChartRef.value)
  anomalyChart?.setOption({
    tooltip: { trigger: 'item' },
    legend: { orient: 'vertical', left: 'left' },
    color: ['#e07a5f', '#c4a35a', '#909399', '#088395', '#05bfdb', '#2a9d8f'],
    series: [{
      name: '异常类型',
      type: 'pie',
      radius: ['40%', '68%'],
      label: { show: false },
      emphasis: { label: { show: true, fontSize: 13, fontWeight: 600 } },
      data: anomaly.map((x) => ({
        name: anomalyNameMap[x.name] || x.name || '其他',
        value: Number(x.value || 0)
      }))
    }]
  })

  const readingChart = makeChart(readingTypeChartRef.value)
  readingChart?.setOption({
    tooltip: { trigger: 'item' },
    legend: { orient: 'vertical', left: 'left' },
    color: ['#088395', '#2a9d8f', '#c4a35a'],
    series: [{
      name: '抄表方式',
      type: 'pie',
      radius: '65%',
      data: readingType.map((x) => ({
        name: readingLabel(x.name),
        value: Number(x.value || 0)
      }))
    }]
  })
}

const onResize = () => charts.forEach((c) => c.resize())

const load = async () => {
  loading.value = true
  error.value = ''
  try {
    const res = await opsApi.cockpit()
    const data = res.data || {}
    kpis.value = data.kpis || []
    signals.value = data.signals || []
    zones.value = data.zones || []
    advice.value = data.advice || ''
    classic.value = data.classicStats || {}
    recentReadings.value = data.recentReadings || []
    pendingWorkOrders.value = data.pendingWorkOrders || []
    await nextTick()
    renderCharts(data)
  } catch (e) {
    error.value = e.message || '主页加载失败，请确认 water-service 已启动'
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  load()
  window.addEventListener('resize', onResize)
})
onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  disposeCharts()
})
</script>

<style scoped lang="scss">
.home { display: grid; gap: 18px; }
.hero {
  display: flex; justify-content: space-between; gap: 20px; align-items: flex-end;
  padding: 22px 24px; border-radius: var(--radius-lg);
  background: linear-gradient(120deg, rgba(8,131,149,.16), rgba(5,191,219,.08)), #fff;
  border: 1px solid var(--color-border); box-shadow: var(--shadow-glow);
  .eyebrow { color: var(--teal-600); font-size: 12px; letter-spacing: .12em; text-transform: uppercase; margin-bottom: 8px; }
  h1 { font-size: clamp(24px, 3vw, 34px); margin-bottom: 8px; }
  .sub { color: var(--color-muted); max-width: 640px; line-height: 1.6; }
  .hero-actions { display: flex; gap: 10px; flex-shrink: 0; }
}
.kpi-row { display: grid; grid-template-columns: repeat(4, 1fr); gap: 14px; }
.kpi {
  padding: 16px 18px; position: relative; overflow: hidden;
  &::after { content:''; position:absolute; right:-20px; top:-20px; width:90px; height:90px; border-radius:50%; opacity:.18; }
  &.tone-teal::after { background:#088395; } &.tone-gold::after { background:#c4a35a; }
  &.tone-coral::after { background:#e07a5f; } &.tone-cyan::after { background:#05bfdb; }
  .kpi-label { color: var(--color-muted); font-size: 13px; }
  .kpi-value { display:flex; align-items:baseline; gap:6px; margin:8px 0 6px;
    strong { font-family: var(--font-display); font-size: 30px; }
    span { color: var(--color-muted); font-size: 13px; }
  }
  .kpi-hint { font-size: 12px; color: var(--teal-700); }
}
.main-grid { display: grid; grid-template-columns: 1.35fr .9fr; gap: 14px; align-items: start; }
.findings, .mini-map, .advice { padding: 18px; }
.findings {
  display: flex; flex-direction: column; min-height: 0;
  .panel-head { flex-shrink: 0; margin-bottom: 12px; }
}
.findings-scroll {
  height: 420px;
  overflow-y: auto;
  padding-right: 4px;
  scrollbar-gutter: stable;
  &::-webkit-scrollbar { width: 6px; }
  &::-webkit-scrollbar-thumb { background: rgba(8,131,149,.35); border-radius: 999px; }
}
.signal {
  border: 1px solid var(--color-border); border-radius: 14px; padding: 14px; margin-bottom: 12px; background: #fafefe;
  &:last-child { margin-bottom: 0; }
  &.critical { border-color: rgba(224,122,95,.45); } &.warn { border-color: rgba(233,196,106,.55); }
  .signal-top { display:flex; justify-content:space-between; gap:10px; margin-bottom:10px; h4 { font-size:15px; } }
  .signal-body { display:grid; gap:8px; em { font-style:normal; font-size:11px; color:var(--color-muted); } p { margin-top:2px; font-size:14px; line-height:1.5; } }
}
.zone-grid { display:grid; grid-template-columns:1fr 1fr; gap:10px; }
.zone {
  border:none; border-radius:14px; padding:14px; text-align:left; cursor:pointer; color:#fff; display:grid; gap:4px;
  strong { font-family: var(--font-display); font-size:18px; } span,small { font-size:12px; opacity:.9; }
  &.ok { background: linear-gradient(145deg,#2a9d8f,#1d7a6f); }
  &.warn { background: linear-gradient(145deg,#c4a35a,#9a6b00); }
  &.danger { background: linear-gradient(145deg,#e07a5f,#b84f38); }
}
.advice blockquote { margin:0 0 14px; padding:14px; border-left:3px solid var(--cyan-500); background:rgba(5,191,219,.08); border-radius:0 12px 12px 0; line-height:1.65; }
.link { color: var(--teal-600); }

.classic { display: grid; gap: 14px; padding-top: 8px; }
.overview-charts { display: grid; gap: 14px; }
.section-title {
  h2 { font-family: var(--font-display); font-size: 22px; margin-bottom: 4px; }
  p { color: var(--color-muted); font-size: 13px; }
}
.chart-section-title {
  display: flex; align-items: flex-end; justify-content: space-between; gap: 18px;
  padding: 2px 2px 0;
  .eyebrow { color: var(--teal-600); font-size: 11px; letter-spacing: .12em; text-transform: uppercase; margin-bottom: 5px; }
}
.chart-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 14px; }
.stat-row { display: grid; grid-template-columns: repeat(4, 1fr); gap: 14px; }
.stat-card {
  padding: 16px 18px;
  .stat-header { display:flex; justify-content:space-between; align-items:center; color:var(--color-muted); font-size:13px; }
  .stat-value { font-family: var(--font-display); font-size: 28px; margin: 10px 0 4px; }
  .stat-label { font-size: 12px; color: var(--color-muted); }
  .tone-ok { color: #2a9d8f; } .tone-gold { color: #a07f2f; } .tone-danger { color: #c45a40; }
}
.chart-row { display: grid; grid-template-columns: 1fr 1fr; gap: 14px; }
.chart-card { padding: 16px 18px; }
.chart-title { font-size: 15px; font-weight: 600; margin-bottom: 12px; }
.chart-box { height: 300px; width: 100%; }
.chart-box-compact { height: 240px; }

@media (max-width: 1100px) {
  .kpi-row, .stat-row, .chart-row { grid-template-columns: 1fr; }
  .chart-grid { grid-template-columns: 1fr 1fr; }
  .main-grid { grid-template-columns: 1fr; }
}
@media (max-width: 720px) {
  .chart-grid { grid-template-columns: 1fr; }
  .chart-section-title { align-items: flex-start; flex-direction: column; gap: 4px; }
}
</style>
