<template>
  <div class="zone-detail">
    <header class="panel head">
      <div class="left">
        <el-button text @click="$router.push('/twin/map')">← 返回地图</el-button>
        <div>
          <h3>{{ overview.name || areaCode }} 分区详情</h3>
          <div class="sub">点选孪生节点进入 · 数据来自该分区水表 / 异常 / 抄表实时聚合</div>
        </div>
      </div>
      <div class="right">
        <span class="pill" :class="pillClass(overview.status)">{{ statusText(overview.status) }}</span>
        <el-button :loading="loading" @click="load">刷新</el-button>
      </div>
    </header>

    <el-alert v-if="error" type="error" :title="error" show-icon style="margin-bottom:12px" />

    <section class="kpi-row">
      <article class="panel kpi"><em>水表</em><strong>{{ overview.meters ?? '-' }}</strong></article>
      <article class="panel kpi"><em>今日用水</em><strong>{{ overview.usage ?? '-' }} <small>m³</small></strong></article>
      <article class="panel kpi"><em>异常</em><strong>{{ overview.anomaly ?? '-' }}</strong></article>
      <article class="panel kpi"><em>漏损指数</em><strong>{{ overview.leakRate ?? '-' }}%</strong></article>
      <article class="panel kpi"><em>夜间用水(7日)</em><strong>{{ nightDay.nightUsage ?? '-' }}</strong></article>
      <article class="panel kpi"><em>日间用水(7日)</em><strong>{{ nightDay.dayUsage ?? '-' }}</strong></article>
    </section>

    <section class="panel insight">
      <em>AI研判</em>
      <p>{{ overview.insight || '加载中…' }}</p>
    </section>

    <section class="grid-2">
      <div class="panel block">
        <div class="panel-head">
          <h3>分区水表</h3>
          <span class="sub">共 {{ meters.length }} 块 · 均电量 {{ fmtNum(meterStats.avg_battery) }}%</span>
        </div>
        <el-table :data="meters" stripe height="360" size="small">
          <el-table-column prop="meter_no" label="水表编号" min-width="130" />
          <el-table-column prop="user_name" label="用户" width="90" />
          <el-table-column prop="statusLabel" label="状态" width="70" />
          <el-table-column prop="health" label="健康度" width="80">
            <template #default="{ row }">
              <span :class="healthClass(row.health)">{{ row.health }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="battery_level" label="电量" width="70" />
          <el-table-column prop="signal_strength" label="信号" width="70" />
          <el-table-column prop="current_reading" label="当前读数" width="100" />
        </el-table>
      </div>

      <div class="panel block">
        <div class="panel-head">
          <h3>分区异常</h3>
          <span class="sub">最近 {{ anomalies.length }} 条</span>
        </div>
        <el-table :data="anomalies" stripe height="360" size="small">
          <el-table-column prop="meter_no" label="水表" width="130" />
          <el-table-column prop="anomaly_type" label="类型" width="110" />
          <el-table-column prop="severity" label="级别" width="90">
            <template #default="{ row }">
              <span class="pill" :class="severityPill(row.severity)">{{ row.severity }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="ai_score" label="AI分" width="70" />
          <el-table-column prop="description" label="描述" min-width="160" show-overflow-tooltip />
        </el-table>
      </div>
    </section>

    <section class="panel block">
      <div class="panel-head">
        <h3>最近抄表</h3>
        <span class="sub">本区最近 {{ readings.length }} 条</span>
      </div>
      <el-table :data="readings" stripe size="small">
        <el-table-column prop="meter_no" label="水表编号" width="140" />
        <el-table-column prop="reading_value" label="读数" width="100" />
        <el-table-column prop="usage_amount" label="用量" width="90" />
        <el-table-column prop="reading_type" label="方式" width="100" />
        <el-table-column label="置信度" width="90">
          <template #default="{ row }">
            <span v-if="row.confidence != null">{{ Number(row.confidence * 100).toFixed(0) }}%</span>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="anomaly_type" label="异常标记" width="110" />
        <el-table-column prop="reading_time" label="时间" min-width="160" />
      </el-table>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { opsApi } from '@/api'

const route = useRoute()
const areaCode = computed(() => String(route.params.areaCode || ''))
const loading = ref(false)
const error = ref('')
const overview = ref({})
const nightDay = ref({})
const meterStats = ref({})
const meters = ref([])
const anomalies = ref([])
const readings = ref([])

const statusText = (s) => ({ ok: '正常', warn: '风险', danger: '异常' }[s] || '-')
const pillClass = (s) => ({ ok: 'pill-ok', warn: 'pill-warn', danger: 'pill-danger' }[s] || 'pill-info')
const severityPill = (s) => ({ critical: 'pill-danger', high: 'pill-warn', medium: 'pill-info', low: 'pill-ok' }[s] || 'pill-info')
const healthClass = (h) => (h >= 80 ? 'ok' : h >= 60 ? 'warn' : 'bad')
const fmtNum = (v) => (v == null || v === '' ? '-' : Number(v).toFixed(0))

const load = async () => {
  if (!areaCode.value) return
  loading.value = true
  error.value = ''
  try {
    const res = await opsApi.zoneDetail(areaCode.value)
    const data = res.data || {}
    overview.value = data.overview || {}
    nightDay.value = data.nightDay || {}
    meterStats.value = data.meterStats || {}
    meters.value = data.meters || []
    anomalies.value = data.anomalies || []
    readings.value = data.readings || []
  } catch (e) {
    error.value = e.message || '分区详情加载失败'
  } finally {
    loading.value = false
  }
}

watch(areaCode, load)
onMounted(load)
</script>

<style scoped lang="scss">
.zone-detail { display: grid; gap: 14px; }
.head {
  padding: 14px 16px;
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
  .left { display: flex; gap: 10px; align-items: center; }
  .right { display: flex; gap: 10px; align-items: center; }
  h3 { font-family: var(--font-display); margin-bottom: 4px; }
  .sub { color: var(--color-muted); font-size: 13px; }
}
.kpi-row {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 10px;
}
.kpi {
  padding: 14px;
  em { display:block; font-style:normal; color:var(--color-muted); font-size:12px; margin-bottom:6px; }
  strong { font-family: var(--font-display); font-size: 22px; }
  small { font-size: 12px; color: var(--color-muted); font-weight: 500; }
}
.insight {
  padding: 14px 16px;
  em { font-style:normal; font-size:12px; color: var(--coral-500); }
  p { margin-top: 6px; line-height: 1.65; }
}
.grid-2 { display: grid; grid-template-columns: 1fr 1fr; gap: 14px; }
.block { padding: 14px 16px; }
.ok { color: #2a9d8f; font-weight: 700; }
.warn { color: #c4a35a; font-weight: 700; }
.bad { color: #e07a5f; font-weight: 700; }
@media (max-width: 1100px) {
  .kpi-row { grid-template-columns: repeat(3, 1fr); }
  .grid-2 { grid-template-columns: 1fr; }
}
</style>
