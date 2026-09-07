<template>
  <div class="table-page reading-trust">
    <section class="panel explain-banner">
      <div>
        <h3>AI抄表可信度管理</h3>
        <p>数据来自 meter_reading（ai_image），含置信度与人机协同建议。</p>
      </div>
      <el-button type="primary" :loading="loading" @click="load">刷新</el-button>
    </section>

    <el-alert v-if="error" type="error" :title="error" show-icon style="margin-bottom:12px" />

    <div class="explain-grid">
      <article v-for="c in explainCards" :key="c.id || c.meterNo" class="panel explain-card" :class="{ risk: c.confidence < 70 }">
        <div class="top">
          <strong>{{ c.meterNo }}</strong>
          <span class="pill" :class="c.confidence >= 90 ? 'pill-ok' : c.confidence >= 70 ? 'pill-warn' : 'pill-danger'">
            置信度 {{ c.confidence }}%
          </span>
        </div>
        <dl>
          <div><dt>识别</dt><dd>{{ c.reading }} m³</dd></div>
          <div><dt>关注区域</dt><dd>{{ c.focus }}</dd></div>
          <div><dt>风险</dt><dd>{{ c.risk }}</dd></div>
          <div><dt>建议</dt><dd>{{ c.suggest }}</dd></div>
        </dl>
      </article>
    </div>

    <div class="table-container">
      <el-table :data="tableData" stripe>
        <el-table-column prop="meterNo" label="水表编号" width="140" />
        <el-table-column prop="userName" label="用户" width="100" />
        <el-table-column prop="readingValue" label="读数" width="120" />
        <el-table-column prop="usageAmount" label="用水量" width="100" />
        <el-table-column prop="readingType" label="方式" width="100" />
        <el-table-column label="置信度" width="120">
          <template #default="{ row }">
            <span v-if="row.confidence != null">{{ Number(row.confidence * 100).toFixed(1) }}%</span>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="90" />
        <el-table-column prop="readingTime" label="时间" min-width="160" />
      </el-table>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { opsApi } from '@/api'

const loading = ref(false)
const error = ref('')
const explainCards = ref([])
const tableData = ref([])

const load = async () => {
  loading.value = true
  error.value = ''
  try {
    const [trust, recent] = await Promise.all([
      opsApi.ocrTrust(20),
      opsApi.recentReadings(50)
    ])
    explainCards.value = trust.data || []
    tableData.value = (recent.data || []).map(r => ({
      meterNo: r.meter_no,
      userName: r.user_name,
      readingValue: r.reading_value,
      usageAmount: r.usage_amount,
      readingType: r.reading_type,
      confidence: r.confidence,
      status: r.status,
      readingTime: r.reading_time
    }))
  } catch (e) {
    error.value = e.message || '加载失败'
  } finally {
    loading.value = false
  }
}
onMounted(load)
</script>

<style scoped lang="scss">
.explain-banner { padding:16px 18px; margin-bottom:14px; display:flex; justify-content:space-between; gap:16px; align-items:center;
  h3{font-family:var(--font-display); margin-bottom:6px;} p{color:var(--color-muted); font-size:14px;} }
.explain-grid { display:grid; grid-template-columns:repeat(auto-fit,minmax(260px,1fr)); gap:12px; margin-bottom:14px; }
.explain-card { padding:14px; &.risk{border-color:rgba(224,122,95,.45);}
  .top{display:flex;justify-content:space-between;margin-bottom:10px; strong{font-family:var(--font-display);} }
  dl{display:grid;gap:8px; div{display:flex;justify-content:space-between;font-size:13px;} dt{color:var(--color-muted);} dd{font-weight:600;} }
}
</style>
